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
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
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
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.SETTINGS)) {
            return Result.fail(Messages.get(ShieldText.RANK_CANNOT));
        }
        if (!ctx.settings().bool("shield.enabled")) {
            return Result.fail(Messages.get(ShieldText.SHIELDS_OFF));
        }
        if (startHour < -1 || startHour > 23) {
            return Result.fail(Messages.get(ShieldText.PICK_HOUR));
        }
        String who = nameOf(actor);
        boolean pending = f.stat(PENDING_AT) > 0;
        if (startHour == f.shieldStartHour()) {
            if (!pending) {
                return Result.fail(startHour < 0 ? Messages.get(ShieldText.ALREADY_NONE)
                        : Messages.get(ShieldText.ALREADY_STARTS, "hour", hour(startHour)));
            }
            setStat(f, PENDING_AT, 0);
            setStat(f, PENDING_HOUR, 0);
            fs.audit(f, actor, who, "shield", Messages.get(ShieldText.AUDIT_PENDING_CANCELLED));
            return Result.pass(Messages.get(ShieldText.PENDING_CANCELLED));
        }
        long delay = Math.max(0, ctx.settings().integer("shield.changeDelayHours")) * HOUR;
        if (delay == 0) {
            apply(f, startHour, actor, who);
            return Result.pass(describeChange(startHour, true, 0));
        }
        setStat(f, PENDING_HOUR, startHour);
        setStat(f, PENDING_AT, Time.now());
        fs.audit(f, actor, who, "shield", describeAudit(startHour, delay));
        return Result.pass(describeChange(startHour, false, delay));
    }

    private void apply(Faction f, int hour, UUID actor, String who) {
        f.setShieldStartHour(hour);
        setStat(f, PENDING_AT, 0);
        setStat(f, PENDING_HOUR, 0);
        ctx.service(FactionService.class).audit(f, actor, who, "shield",
                hour < 0 ? Messages.get(ShieldText.AUDIT_REMOVED) : Messages.get(ShieldText.AUDIT_STARTS, "hour", hour(hour)));
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
    }

    private static String describeChange(int hour, boolean now, long delay) {
        if (hour < 0) {
            return now ? Messages.get(ShieldText.REMOVED) : Messages.get(ShieldText.WILL_BE_REMOVED, "delay", Time.remaining(delay));
        }
        return now ? Messages.get(ShieldText.NOW_STARTS, "hour", hour(hour))
                : Messages.get(ShieldText.WILL_START, "hour", hour(hour), "delay", Time.remaining(delay));
    }

    private static String describeAudit(int hour, long delay) {
        return hour < 0 ? Messages.get(ShieldText.AUDIT_ASK_REMOVE, "delay", Time.remaining(delay))
                : Messages.get(ShieldText.AUDIT_ASK_MOVE, "hour", hour(hour), "delay", Time.remaining(delay));
    }

    @Override
    public String status(Faction faction) {
        if (faction == null) {
            return Messages.get(ShieldText.STATUS_NO_FACTION);
        }
        Settings s = ctx.settings();
        String text;
        int start = faction.shieldStartHour();
        if (!s.bool("shield.enabled")) {
            text = Messages.get(ShieldText.SHIELDS_OFF);
        } else if (s.bool("eotw.active")) {
            text = Messages.get(ShieldText.STATUS_EOTW);
        } else if (start < 0) {
            text = Messages.get(ShieldText.STATUS_NONE);
        } else {
            long left = minutesLeft(start, s.integer("shield.hours"));
            if (left > 0) {
                ClaimService claims = ctx.serviceOrNull(ClaimService.class);
                if (claims != null && claims.raidable(faction)) {
                    text = Messages.get(ShieldText.STATUS_RAIDABLE);
                } else if (faction.home() == null) {
                    text = Messages.get(ShieldText.STATUS_NO_HOME);
                } else {
                    text = Messages.get(ShieldText.STATUS_ACTIVE, "time", Time.remaining(left * MINUTE));
                }
            } else {
                text = Messages.get(ShieldText.STATUS_STARTS, "hour", hour(start));
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
        String when = left <= 0 ? Messages.get(ShieldText.WHEN_SOON) : Messages.get(ShieldText.WHEN_IN, "time", Time.remaining(left));
        return next < 0 ? Messages.get(ShieldText.PENDING_REMOVE, "when", when)
                : Messages.get(ShieldText.PENDING_MOVE, "hour", hour(next), "when", when);
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
            return Result.fail(Messages.get(ShieldText.GRACE_FUTURE));
        }
        Settings s = ctx.settings();
        s.set("grace.endsAt", endsAtMillis);
        s.set("grace.active", true);
        ctx.bus().publish(new DominionEvent.GraceChanged(true, endsAtMillis));
        return Result.pass(endsAtMillis > 0 ? Messages.get(ShieldText.GRACE_FOR, "time", Time.remaining(endsAtMillis - Time.now()))
                : Messages.get(ShieldText.GRACE_UNTIL_ENDED));
    }

    @Override
    public Result endGrace(UUID admin) {
        Settings s = ctx.settings();
        if (!s.bool("grace.active")) {
            return Result.fail(Messages.get(ShieldText.GRACE_NOT_ACTIVE));
        }
        s.set("grace.active", false);
        ctx.bus().publish(new DominionEvent.GraceChanged(false, Time.now()));
        return Result.pass(Messages.get(ShieldText.GRACE_ENDED));
    }

    @Override
    public boolean eotwActive() {
        return ctx.settings().bool("eotw.active");
    }

    @Override
    public Result setEotw(UUID admin, boolean on) {
        if (eotwActive() == on) {
            return Result.fail(on ? Messages.get(ShieldText.EOTW_ALREADY_ON) : Messages.get(ShieldText.EOTW_ALREADY_OFF));
        }
        ctx.settings().set("eotw.active", on);
        // End of the world belongs to no faction, so the admin audit is a server log line.
        ctx.logger().at(Level.INFO).log("Admin audit. %s turned end of the world %s", adminName(admin), on ? "on" : "off");
        ctx.bus().publish(new DominionEvent.EotwChanged(on));
        return Result.pass(on ? Messages.get(ShieldText.EOTW_ON)
                : Messages.get(ShieldText.EOTW_OFF));
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
