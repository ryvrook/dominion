package dev.ryvrook.dominion.shield;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shield windows are a daily span of server local hours. A window change is stored as a pending
 * request in the faction stats and applied by the minute tick once the change delay has passed,
 * so a faction cannot raise its shield the moment a siege starts.
 */
public final class ShieldServiceImpl implements ShieldService {
    static final String PENDING_HOUR = "shield.pendingHour";
    static final String PENDING_AT = "shield.pendingAt";
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;

    private final DominionContext ctx;

    public ShieldServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- shield windows ---------------------------------------------------

    @Override
    public boolean shielded(Faction faction, String world, long chunkIndex) {
        if (faction == null || world == null || !windowActive(faction)) {
            return false;
        }
        Location home = faction.home();
        if (home == null || !world.equals(home.world())) {
            return false;
        }
        long homeChunk = home.chunkIndex();
        int dx = Keys.chunkX(chunkIndex) - Keys.chunkX(homeChunk);
        int dz = Keys.chunkZ(chunkIndex) - Keys.chunkZ(homeChunk);
        if (!ShieldWindow.covers(ctx.settings().integer("shield.coreRadius"), upgradeLevel(faction), dx, dz)) {
            return false;
        }
        ClaimService claims = ctx.serviceOrNull(ClaimService.class);
        return claims == null || !claims.raidable(faction);
    }

    @Override
    public boolean windowActive(Faction faction) {
        Settings s = ctx.settings();
        if (faction == null || !s.bool("shield.enabled") || s.bool("eotw.active")) {
            return false;
        }
        return minutesLeft(faction.shieldStartHour(), s.integer("shield.hours")) > 0;
    }

    /** Minutes left in today's window, or zero when the window is not running. */
    private static long minutesLeft(int startHour, int hours) {
        LocalDateTime now = LocalDateTime.now();
        return ShieldWindow.minutesLeft(startHour, hours, now.getHour() * 60 + now.getMinute());
    }

    private int upgradeLevel(Faction faction) {
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        return Math.max(0, progress != null ? progress.upgradeLevel(faction, "shield") : faction.upgrade("shield"));
    }

    @Override
    public Result setWindow(UUID actor, int startHour) {
        FactionService fs = ctx.service(FactionService.class);
        Faction f = fs.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!fs.can(actor, FactionAction.SETTINGS)) {
            return Result.fail("Your rank cannot change the shield window.");
        }
        if (!ctx.settings().bool("shield.enabled")) {
            return Result.fail("Shields are turned off on this server.");
        }
        if (startHour < -1 || startHour > 23) {
            return Result.fail("Pick an hour from 0 to 23, or none to remove the shield.");
        }
        String who = nameOf(actor);
        boolean pending = f.stat(PENDING_AT) > 0;
        if (startHour == f.shieldStartHour()) {
            if (!pending) {
                return Result.fail(startHour < 0 ? "Your faction has no shield window already."
                        : "Your shield already starts at " + hour(startHour) + ".");
            }
            setStat(f, PENDING_AT, 0);
            setStat(f, PENDING_HOUR, 0);
            fs.audit(f, actor, who, "shield", "Cancelled the pending shield change");
            return Result.pass("The pending shield change was cancelled.");
        }
        long delay = Math.max(0, ctx.settings().integer("shield.changeDelayHours")) * HOUR;
        if (delay == 0) {
            apply(f, startHour, actor, who);
            return Result.pass(describeChange(startHour, true));
        }
        setStat(f, PENDING_HOUR, startHour);
        setStat(f, PENDING_AT, Time.now());
        fs.audit(f, actor, who, "shield", "Asked to " + describeAudit(startHour) + " in "
                + Time.remaining(delay));
        return Result.pass(describeChange(startHour, false) + " in " + Time.remaining(delay) + ".");
    }

    private void apply(Faction f, int hour, UUID actor, String who) {
        f.setShieldStartHour(hour);
        setStat(f, PENDING_AT, 0);
        setStat(f, PENDING_HOUR, 0);
        ctx.service(FactionService.class).audit(f, actor, who, "shield",
                hour < 0 ? "The shield window was removed" : "The shield window now starts at " + hour(hour));
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
    }

    private static String describeChange(int hour, boolean now) {
        if (hour < 0) {
            return now ? "Your shield window was removed." : "Your shield window will be removed";
        }
        return now ? "Your shield now starts at " + hour(hour) + "." : "Your shield will start at " + hour(hour);
    }

    private static String describeAudit(int hour) {
        return hour < 0 ? "remove the shield window" : "move the shield window to " + hour(hour);
    }

    @Override
    public String status(Faction faction) {
        if (faction == null) {
            return "No faction.";
        }
        Settings s = ctx.settings();
        String text;
        int start = faction.shieldStartHour();
        if (!s.bool("shield.enabled")) {
            text = "Shields are turned off on this server.";
        } else if (s.bool("eotw.active")) {
            text = "Shields are down for the end of the world.";
        } else if (start < 0) {
            text = "No shield window is set.";
        } else {
            long left = minutesLeft(start, s.integer("shield.hours"));
            if (left > 0) {
                ClaimService claims = ctx.serviceOrNull(ClaimService.class);
                if (claims != null && claims.raidable(faction)) {
                    text = "Shield is down because the faction is raidable.";
                } else if (faction.home() == null) {
                    text = "The shield window is open but covers no land until the faction sets a home.";
                } else {
                    text = "Shield active for " + Time.remaining(left * MINUTE) + ".";
                }
            } else {
                text = "Shield starts at " + hour(start) + ".";
            }
        }
        String pending = pendingSentence(faction);
        return pending == null ? text : text + " " + pending;
    }

    /** The sentence describing a pending window change, or null when none is waiting. */
    private String pendingSentence(Faction faction) {
        long pendingAt = faction.stat(PENDING_AT);
        if (pendingAt <= 0) {
            return null;
        }
        int next = (int) faction.stat(PENDING_HOUR);
        long applyAt = pendingAt + Math.max(0, ctx.settings().integer("shield.changeDelayHours")) * HOUR;
        long left = applyAt - Time.now();
        String when = left <= 0 ? "within a minute" : "in " + Time.remaining(left);
        return next < 0 ? "The shield window will be removed " + when + "."
                : "The shield window moves to " + hour(next) + " " + when + ".";
    }

    // ---- grace and end of world -------------------------------------------

    @Override
    public boolean graceActive() {
        Settings s = ctx.settings();
        return ShieldWindow.graceActive(s.bool("grace.active"), s.longValue("grace.endsAt"), Time.now());
    }

    @Override
    public long graceEndsAt() {
        return ctx.settings().longValue("grace.endsAt");
    }

    @Override
    public Result setGrace(UUID admin, long endsAtMillis) {
        if (endsAtMillis > 0 && endsAtMillis <= Time.now()) {
            return Result.fail("The grace end must be in the future.");
        }
        Settings s = ctx.settings();
        s.set("grace.endsAt", endsAtMillis);
        s.set("grace.active", true);
        ctx.bus().publish(new DominionEvent.GraceChanged(true, endsAtMillis));
        return Result.pass(endsAtMillis > 0 ? "Grace is active for " + Time.remaining(endsAtMillis - Time.now()) + "."
                : "Grace is active until an admin ends it.");
    }

    @Override
    public Result endGrace(UUID admin) {
        Settings s = ctx.settings();
        if (!s.bool("grace.active")) {
            return Result.fail("Grace is not active.");
        }
        s.set("grace.active", false);
        ctx.bus().publish(new DominionEvent.GraceChanged(false, Time.now()));
        return Result.pass("Grace has ended.");
    }

    @Override
    public boolean eotwActive() {
        return ctx.settings().bool("eotw.active");
    }

    @Override
    public Result setEotw(UUID admin, boolean on) {
        if (eotwActive() == on) {
            return Result.fail(on ? "End of the world is already on." : "End of the world is already off.");
        }
        ctx.settings().set("eotw.active", on);
        // End of the world belongs to no faction, so the admin audit is a server log line.
        ctx.logger().at(Level.INFO).log("Admin audit. %s turned end of the world %s", adminName(admin), on ? "on" : "off");
        ctx.bus().publish(new DominionEvent.EotwChanged(on));
        return Result.pass(on ? "End of the world is on. Shields and siege protections are down."
                : "End of the world is off.");
    }

    // ---- tick ---------------------------------------------------------------

    @Override
    public void tick() {
        Settings s = ctx.settings();
        if (s.bool("grace.active")) {
            long ends = s.longValue("grace.endsAt");
            if (ends > 0 && Time.now() >= ends) {
                s.set("grace.active", false);
                ctx.bus().publish(new DominionEvent.GraceChanged(false, ends));
            }
        }
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs == null) {
            return;
        }
        long delay = Math.max(0, s.integer("shield.changeDelayHours")) * HOUR;
        long now = Time.now();
        for (Faction f : fs.all()) {
            if (ShieldWindow.pendingDue(f.stat(PENDING_AT), delay, now)) {
                apply(f, (int) f.stat(PENDING_HOUR), null, "Server");
            }
        }
    }

    // ---- helpers ----------------------------------------------------------

    static void setStat(Faction f, String key, long value) {
        long current = f.stat(key);
        if (current != value) {
            f.addStat(key, value - current);
        }
    }

    private static String hour(int h) {
        return String.format("%02d:00", h);
    }

    private String adminName(UUID admin) {
        return admin == null ? "The console" : nameOf(admin) + " (" + admin + ")";
    }

    private String nameOf(UUID uuid) {
        PlayerRef ref = Players.online(uuid);
        return ref != null ? ref.getUsername() : ctx.player(uuid).name();
    }
}
