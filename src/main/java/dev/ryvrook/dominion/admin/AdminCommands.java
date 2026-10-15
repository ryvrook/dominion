package dev.ryvrook.dominion.admin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.command.Call;
import dev.ryvrook.dominion.command.IdPrefix;
import dev.ryvrook.dominion.command.PlayerCmd;
import dev.ryvrook.dominion.command.ServerCmd;
import dev.ryvrook.dominion.command.Targets;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.config.Perms;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.CombatTagService;
import dev.ryvrook.dominion.service.ExportService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.model.Strike;
import dev.ryvrook.dominion.service.ImportService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Builds /fa. The engine checks permissions per command and never the parent, so every sub
 * command and variant requires the admin permission on its own.
 */
final class AdminCommands {
    private static final List<String> PRESETS = dev.ryvrook.dominion.config.Defaults.PRESETS;
    private static final int MAX_ADMIN_RADIUS = 10;

    private final DominionContext ctx;
    private final Targets targets;

    AdminCommands(DominionContext ctx) {
        this.ctx = ctx;
        this.targets = new Targets(ctx);
    }

    private FactionService factions() { return ctx.service(FactionService.class); }
    private ClaimService claims() { return ctx.service(ClaimService.class); }
    private PowerService power() { return ctx.service(PowerService.class); }
    private EconomyService economy() { return ctx.service(EconomyService.class); }
    private RulesService rules() { return ctx.service(RulesService.class); }
    private UiService ui() { return ctx.service(UiService.class); }

    private static String t(String id, Object... pairs) {
        return Messages.get(id, pairs);
    }

    private static PlayerCmd player(String name, String description) {
        return new PlayerCmd(name, description).permission(Perms.admin());
    }

    private static PlayerCmd playerVariant(String description) {
        return new PlayerCmd(description).permission(Perms.admin());
    }

    private static ServerCmd server(String name, String description) {
        return new ServerCmd(name, description).permission(Perms.admin());
    }

    ServerCmd build() {
        // Read once at registration, so a change applies at the next start or /plugin reload.
        String rootName = CommandNames.name(ctx.settings().text("commands.adminRoot"), CommandNames.DEFAULT_ADMIN);
        ServerCmd root = server(rootName, t(AdminText.CMD_ROOT));
        List<String> aliases = CommandNames.aliases(ctx.settings().text("commands.adminAliases"), rootName);
        if (!aliases.isEmpty()) {
            root.addAliases(aliases.toArray(String[]::new));
        }
        CommandNames.registeredAdmin(rootName);
        root.handler(this::usage);

        registerSettings(root);
        registerZones(root);
        registerLand(root);
        registerForce(root);
        registerPower(root);
        registerModes(root);
        registerEconomy(root);
        registerMaintenance(root);
        registerStrikes(root);
        registerSieges(root);
        registerImport(root);
        registerIntegrations(root);
        new ConfigCommands(ctx).register(root);
        // Attach every sub command and variant before the caller registers the root.
        root.seal();
        return root;
    }

    private void usage(Call c) {
        c.send(Msg.info(t(AdminText.USAGE_TITLE)));
        String[] lines = {
                "bypass", "settings [group]", "set <key> <value>", "get <key>", "preset <name>",
                "zone safe <name>", "zone war <name>", "claim <tag> [radius]", "unclaim [radius]",
                "force join <player> <tag>", "force kick <player>", "force leader <tag> <player>", "force disband <tag>",
                "force rename <tag> <newTag> <name>", "strikes <tag>", "strike <tag> <points> <reason>", "unstrike <tag> <index>",
                "power set <player> <value>", "power add <player> <value>", "boost <tag> <value>",
                "grace on <duration>", "grace off", "eotw on", "eotw off",
                "eco give <player> <amount>", "eco take <player> <amount>", "eco set <player> <amount>", "eco provider",
                "siege", "siege cancel <id>", "import <source>", "import preview <source>", "export", "backup", "reload", "diag", "inspect",
                "integrations"};
        for (String line : lines) {
            c.send(Msg.colored(" /fa " + line, Msg.GOLD));
        }
    }

    // ---- settings ------------------------------------------------------------

    private void registerSettings(ServerCmd root) {
        root.sub(player("bypass", t(AdminText.CMD_BYPASS))).handler(c -> {
            boolean on = !rules().bypassing(c.uuid());
            rules().setBypass(c.uuid(), on);
            c.good(t(on ? AdminText.BYPASS_ON : AdminText.BYPASS_OFF));
        });

        PlayerCmd settings = root.sub(player("settings", t(AdminText.CMD_SETTINGS)));
        settings.handler(c -> ui().openAdminSettings(c.player(), firstGroup()));
        PlayerCmd settingsGroup = playerVariant(t(AdminText.CMD_SETTINGS_GROUP));
        RequiredArg<String> group = settingsGroup.withRequiredArg("group", t(AdminText.ARG_SETTINGS_GROUP), ArgTypes.STRING);
        settingsGroup.handler(c -> ui().openAdminSettings(c.player(), c.arg(group)));
        settings.variant(settingsGroup);

        ServerCmd set = root.sub(server("set", t(AdminText.CMD_SET)));
        RequiredArg<String> setKey = set.withRequiredArg("key", t(AdminText.ARG_SETTING_KEY), ArgTypes.STRING);
        RequiredArg<String> setValue = set.withRequiredArg("value", t(AdminText.ARG_NEW_VALUE), ArgTypes.GREEDY_STRING);
        set.handler(c -> {
            String key = c.arg(setKey);
            String value = c.arg(setValue).trim();
            if (ctx.settings().fileOnly(key)) {
                c.bad(t(AdminText.SET_FILE_ONLY, "key", key));
                return;
            }
            String why = ctx.settings().trySet(key, value);
            if (why == null) {
                c.good(t(AdminText.SET_DONE, "key", key, "value", ctx.settings().raw(key)));
            } else {
                c.bad(why);
            }
        });

        ServerCmd get = root.sub(server("get", t(AdminText.CMD_GET)));
        RequiredArg<String> getKey = get.withRequiredArg("key", t(AdminText.ARG_SETTING_KEY), ArgTypes.STRING);
        get.handler(c -> {
            String key = c.arg(getKey);
            Settings.Def def = null;
            for (Settings.Def d : ctx.settings().definitions()) {
                if (d.key().equals(key)) {
                    def = d;
                    break;
                }
            }
            if (def == null) {
                c.bad(t(AdminText.NO_SUCH_SETTING, "key", key));
                return;
            }
            c.info(t(AdminText.GET_VALUE, "key", key, "value", ctx.settings().raw(key)));
            c.send(Msg.muted(" " + t(AdminText.GET_DETAILS, "label", def.label(), "group", def.group(), "default", def.defaultValue())));
            Settings.Range range = ctx.settings().rangeOf(key);
            List<String> allowed = ctx.settings().choicesOf(key);
            if (range != null) {
                c.send(Msg.muted(" " + (range.max() >= Long.MAX_VALUE / 2.0
                        ? t(AdminText.GET_RANGE_OPEN, "min", Msg.number(range.min()))
                        : t(AdminText.GET_RANGE, "min", Msg.number(range.min()), "max", Msg.number(range.max())))));
            } else if (!allowed.isEmpty()) {
                c.send(Msg.muted(" " + t(AdminText.GET_CHOICES, "values", String.join(", ", allowed))));
            }
            if (ctx.settings().fileOnly(key)) {
                c.send(Msg.muted(" " + t(AdminText.GET_FILE_ONLY)));
            }
        });

        ServerCmd preset = root.sub(server("preset", t(AdminText.CMD_PRESET)));
        RequiredArg<String> presetName = preset.withRequiredArg("name", t(AdminText.ARG_PRESET_NAME), ArgTypes.STRING);
        preset.handler(c -> {
            String name = c.arg(presetName).toLowerCase(Locale.ROOT);
            if (!ctx.plugin().applyPreset(name)) {
                c.bad(t(AdminText.PRESET_LIST, "presets", String.join(", ", PRESETS)));
                return;
            }
            c.good(t(AdminText.PRESET_APPLIED, "name", name));
        });
    }

    private String firstGroup() {
        List<Settings.Def> defs = ctx.settings().definitions();
        return defs.isEmpty() ? "" : defs.get(0).group();
    }

    // ---- zones and land ------------------------------------------------------

    private void registerZones(ServerCmd root) {
        ServerCmd zone = root.sub(server("zone", t(AdminText.CMD_ZONE)));
        zone.handler(c -> c.info(t(AdminText.ZONE_USAGE)));
        PlayerCmd safe = zone.sub(player("safe", t(AdminText.CMD_ZONE_SAFE)));
        RequiredArg<String> safeName = safe.withRequiredArg("name", t(AdminText.ARG_ZONE_NAME), ArgTypes.STRING);
        safe.handler(c -> c.reply(claims().createZone(c.uuid(), FactionType.SAFEZONE, c.arg(safeName))));
        PlayerCmd war = zone.sub(player("war", t(AdminText.CMD_ZONE_WAR)));
        RequiredArg<String> warName = war.withRequiredArg("name", t(AdminText.ARG_ZONE_NAME), ArgTypes.STRING);
        war.handler(c -> c.reply(claims().createZone(c.uuid(), FactionType.WARZONE, c.arg(warName))));
    }

    private void registerLand(ServerCmd root) {
        PlayerCmd claim = root.sub(player("claim", t(AdminText.CMD_CLAIM)));
        RequiredArg<String> claimTag = claim.withRequiredArg("tag", t(AdminText.ARG_FACTION_OR_ZONE_TAG), ArgTypes.STRING);
        claim.handler(c -> adminClaim(c, c.arg(claimTag), 0));
        PlayerCmd claimRadius = playerVariant(t(AdminText.CMD_CLAIM_RADIUS));
        RequiredArg<String> radiusTag = claimRadius.withRequiredArg("tag", t(AdminText.ARG_FACTION_OR_ZONE_TAG), ArgTypes.STRING);
        RequiredArg<Integer> radius = claimRadius.withRequiredArg("radius", t(AdminText.ARG_RADIUS), ArgTypes.INTEGER);
        claimRadius.handler(c -> adminClaim(c, c.arg(radiusTag), c.arg(radius)));
        claim.variant(claimRadius);

        PlayerCmd unclaim = root.sub(player("unclaim", t(AdminText.CMD_UNCLAIM)));
        unclaim.handler(c -> adminUnclaim(c, 0));
        PlayerCmd unclaimRadius = playerVariant(t(AdminText.CMD_UNCLAIM_RADIUS));
        RequiredArg<Integer> unRadius = unclaimRadius.withRequiredArg("radius", t(AdminText.ARG_RADIUS), ArgTypes.INTEGER);
        unclaimRadius.handler(c -> adminUnclaim(c, c.arg(unRadius)));
        unclaim.variant(unclaimRadius);

        root.sub(player("inspect", t(AdminText.CMD_INSPECT))).handler(this::inspect);
    }

    private void adminClaim(Call c, String tag, int radius) {
        Faction target = factions().byTag(tag);
        if (target == null) {
            c.bad(t(AdminText.NO_SUCH_TAG, "tag", tag));
            return;
        }
        String world = c.world().getName();
        long center = Players.chunkOf(c.player());
        if (center == Long.MIN_VALUE) {
            c.bad(t(CommonText.POSITION_UNKNOWN));
            return;
        }
        int r = Math.max(0, Math.min(MAX_ADMIN_RADIUS, radius));
        int ok = 0;
        Result last = Result.pass();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Result res = claims().adminClaim(c.uuid(), target, world, Keys.chunk(Keys.chunkX(center) + dx, Keys.chunkZ(center) + dz));
                if (res.success()) {
                    ok++;
                } else {
                    last = res;
                }
            }
        }
        int total = (2 * r + 1) * (2 * r + 1);
        if (ok == 0) {
            c.reply(last.failed() ? last : Result.fail(t(AdminText.NOTHING_CLAIMED)));
        } else {
            c.good(t(AdminText.CLAIMED_SOME, "count", ok, "total", total, "tag", target.tag()));
        }
    }

    private void adminUnclaim(Call c, int radius) {
        String world = c.world().getName();
        long center = Players.chunkOf(c.player());
        if (center == Long.MIN_VALUE) {
            c.bad(t(CommonText.POSITION_UNKNOWN));
            return;
        }
        int r = Math.max(0, Math.min(MAX_ADMIN_RADIUS, radius));
        int ok = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long idx = Keys.chunk(Keys.chunkX(center) + dx, Keys.chunkZ(center) + dz);
                if (claims().ownerAt(world, idx) == null) {
                    continue;
                }
                if (claims().adminUnclaim(c.uuid(), world, idx).success()) {
                    ok++;
                }
            }
        }
        if (ok == 0) {
            c.bad(t(AdminText.NOTHING_TO_RELEASE));
        } else {
            c.good(t(AdminText.RELEASED, "count", ok));
        }
    }

    private void inspect(Call c) {
        String world = c.world().getName();
        long chunk = Players.chunkOf(c.player());
        if (chunk == Long.MIN_VALUE) {
            c.bad(t(CommonText.POSITION_UNKNOWN));
            return;
        }
        c.info(t(AdminText.INSPECT_TITLE, "chunk", Keys.describe(chunk), "world", world));
        Faction owner = claims().ownerAt(world, chunk);
        if (owner == null) {
            c.send(Msg.muted(" " + t(AdminText.INSPECT_WILDERNESS)));
            return;
        }
        c.send(Message.join(Msg.muted(" " + t(AdminText.INSPECT_OWNED_BY) + " "), Msg.colored(owner.tag(), owner.color()).bold(true),
                Msg.muted("  " + owner.type().name().toLowerCase(Locale.ROOT))));
        ClaimEntry entry = claims().claimAt(world, chunk);
        if (entry != null) {
            String by = entry.claimedBy() == null ? t(AdminText.INSPECT_BY_SERVER) : targets.nameOf(entry.claimedBy());
            String ago = Time.ago(entry.claimedAt());
            c.send(Msg.muted(" " + (entry.zoneId() == null ? t(AdminText.INSPECT_CLAIMED, "ago", ago, "by", by)
                    : t(AdminText.INSPECT_CLAIMED_IN_ZONE, "ago", ago, "by", by, "zone", entry.zoneId()))));
        }
        if (owner.type() == FactionType.NORMAL) {
            boolean raidable = claims().raidable(owner);
            c.send(Msg.colored(" " + t(raidable ? AdminText.INSPECT_RAIDABLE : AdminText.INSPECT_NOT_RAIDABLE), raidable ? Msg.BAD : Msg.GOOD));
            c.send(Msg.muted(" " + t(AdminText.INSPECT_POWER, "power", Msg.number(power().factionPower(owner)),
                    "maxPower", Msg.number(power().factionMaxPower(owner)), "claims", claims().claimCount(owner),
                    "maxClaims", claims().maxClaims(owner), "boost", Msg.number(owner.powerBoost()))));
        }
        c.send(Msg.muted(" " + t(rules().graceActive() ? AdminText.INSPECT_GRACE_ON : AdminText.INSPECT_GRACE_OFF)
                + ", " + t(rules().eotwActive() ? AdminText.INSPECT_EOTW_ON : AdminText.INSPECT_EOTW_OFF)
                + ", " + t(rules().bypassing(c.uuid()) ? AdminText.INSPECT_BYPASS_ON : AdminText.INSPECT_BYPASS_OFF) + "."));
    }

    // ---- force ---------------------------------------------------------------

    private void registerForce(ServerCmd root) {
        ServerCmd force = root.sub(server("force", t(AdminText.CMD_FORCE)));
        force.handler(c -> c.info(t(AdminText.FORCE_USAGE)));

        ServerCmd join = force.sub(server("join", t(AdminText.CMD_FORCE_JOIN)));
        RequiredArg<String> joinWho = join.withRequiredArg("player", t(AdminText.ARG_PLAYER_NAME), ArgTypes.STRING);
        RequiredArg<String> joinTag = join.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        join.handler(c -> {
            Targets.Target t = anyone(c, c.arg(joinWho));
            Faction f = t == null ? null : faction(c, c.arg(joinTag));
            if (f == null) {
                return;
            }
            Result r = factions().forceJoin(c.uuid(), c.name(), t.uuid(), t.name(), f);
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.FORCE_JOINED, "player", t.name(), "tag", f.tag())) : r);
        });

        ServerCmd kick = force.sub(server("kick", t(AdminText.CMD_FORCE_KICK)));
        RequiredArg<String> kickWho = kick.withRequiredArg("player", t(AdminText.ARG_PLAYER_NAME), ArgTypes.STRING);
        kick.handler(c -> {
            Targets.Target t = anyone(c, c.arg(kickWho));
            if (t == null) {
                return;
            }
            Result r = factions().forceKick(c.uuid(), c.name(), t.uuid());
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.FORCE_KICKED, "player", t.name())) : r);
        });

        ServerCmd leader = force.sub(server("leader", t(AdminText.CMD_FORCE_LEADER)));
        RequiredArg<String> leaderTag = leader.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        RequiredArg<String> leaderWho = leader.withRequiredArg("player", t(AdminText.ARG_PLAYER_NAME), ArgTypes.STRING);
        leader.handler(c -> {
            Faction f = faction(c, c.arg(leaderTag));
            Targets.Target t = f == null ? null : anyone(c, c.arg(leaderWho));
            if (t == null) {
                return;
            }
            Result r = factions().forceLeader(c.uuid(), c.name(), f, t.uuid());
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.FORCE_LEADER, "player", t.name(), "tag", f.tag())) : r);
        });

        ServerCmd disband = force.sub(server("disband", t(AdminText.CMD_FORCE_DISBAND)));
        RequiredArg<String> disbandTag = disband.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        disband.handler(c -> {
            Faction f = faction(c, c.arg(disbandTag));
            if (f == null) {
                return;
            }
            String tag = f.tag();
            Result r = factions().forceDisband(c.uuid(), c.name(), f);
            if (r.success()) {
                ctx.logger().at(Level.INFO).log("%s force disbanded %s", c.name(), tag);
            }
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.FORCE_DISBANDED, "tag", tag)) : r);
        });

        ServerCmd rename = force.sub(server("rename", t(AdminText.CMD_FORCE_RENAME)));
        RequiredArg<String> renameTag = rename.withRequiredArg("tag", t(AdminText.ARG_CURRENT_TAG), ArgTypes.STRING);
        RequiredArg<String> renameNew = rename.withRequiredArg("newTag", t(AdminText.ARG_NEW_TAG), ArgTypes.STRING);
        RequiredArg<String> renameName = rename.withRequiredArg("name", t(AdminText.ARG_NEW_NAME), ArgTypes.GREEDY_STRING);
        rename.handler(c -> {
            Faction f = faction(c, c.arg(renameTag));
            if (f == null) {
                return;
            }
            String newTag = c.arg(renameNew);
            Result r = factions().forceRename(c.uuid(), c.name(), f, newTag, c.arg(renameName).trim());
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.FORCE_RENAMED, "tag", newTag)) : r);
        });
    }

    private Targets.Target anyone(Call c, String name) {
        Targets.Target t = targets.resolveAnywhere(name);
        if (t == null) {
            c.bad(t(AdminText.NO_PLAYER_NAMED, "name", name));
        }
        return t;
    }

    private Faction faction(Call c, String tag) {
        Faction f = factions().byTag(tag);
        if (f == null) {
            c.bad(t(AdminText.NO_FACTION_TAG, "tag", tag));
        }
        return f;
    }

    private void adminAudit(Call c, Faction f, String text) {
        factions().audit(f, c.uuid(), c.name(), "admin", text);
    }

    // ---- power ---------------------------------------------------------------

    private void registerPower(ServerCmd root) {
        ServerCmd power = root.sub(server("power", t(AdminText.CMD_POWER)));
        power.handler(c -> c.info(t(AdminText.POWER_USAGE)));
        for (boolean add : new boolean[] {false, true}) {
            ServerCmd cmd = power.sub(server(add ? "add" : "set", add ? t(AdminText.CMD_POWER_ADD) : t(AdminText.CMD_POWER_SET)));
            RequiredArg<String> who = cmd.withRequiredArg("player", t(AdminText.ARG_PLAYER_NAME), ArgTypes.STRING);
            RequiredArg<Double> value = cmd.withRequiredArg("value", t(AdminText.ARG_POWER), ArgTypes.DOUBLE);
            cmd.handler(c -> {
                Targets.Target t = anyone(c, c.arg(who));
                if (t == null) {
                    return;
                }
                if (!Double.isFinite(c.arg(value))) {
                    c.bad(t(AdminText.POWER_NOT_NUMBER));
                    return;
                }
                if (add) {
                    power().addPower(t.uuid(), c.arg(value));
                } else {
                    power().setPower(t.uuid(), c.arg(value));
                }
                c.good(t(AdminText.POWER_NOW, "player", t.name(), "power", Msg.number(power().powerOf(t.uuid()))));
            });
        }

        ServerCmd boost = root.sub(server("boost", t(AdminText.CMD_BOOST)));
        RequiredArg<String> boostTag = boost.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        RequiredArg<Double> boostValue = boost.withRequiredArg("value", t(AdminText.ARG_BOOST), ArgTypes.DOUBLE);
        boost.handler(c -> {
            Faction f = faction(c, c.arg(boostTag));
            if (f == null) {
                return;
            }
            double v = c.arg(boostValue);
            if (!Double.isFinite(v)) {
                c.bad(t(AdminText.BOOST_NOT_NUMBER));
                return;
            }
            f.setPowerBoost(v);
            adminAudit(c, f, t(AdminText.AUDIT_BOOST, "boost", Msg.number(v)));
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            c.good(t(AdminText.BOOST_NOW, "tag", f.tag(), "boost", Msg.number(v)));
        });
    }

    // ---- grace and eotw ------------------------------------------------------

    /**
     * Grace and end of the world go through the shield service when it is installed. Without it
     * the settings are flipped directly as before, so the commands keep working on a server
     * that runs without shields.
     */
    private void registerModes(ServerCmd root) {
        ServerCmd grace = root.sub(server("grace", t(AdminText.CMD_GRACE)));
        grace.handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            boolean on = shield != null ? shield.graceActive() : ctx.settings().bool("grace.active");
            long ends = shield != null ? shield.graceEndsAt() : ctx.settings().longValue("grace.endsAt");
            if (!on) {
                c.info(t(AdminText.GRACE_IS_OFF));
            } else if (ends > 0) {
                c.info(t(AdminText.GRACE_ENDS_IN, "time", Time.remaining(ends - Time.now())));
            } else {
                c.info(t(AdminText.GRACE_NO_END));
            }
        });
        ServerCmd graceOn = grace.sub(server("on", t(AdminText.CMD_GRACE_ON)));
        RequiredArg<String> duration = graceOn.withRequiredArg("duration", t(AdminText.ARG_DURATION), ArgTypes.STRING);
        graceOn.handler(c -> {
            Duration d = Time.parse(c.arg(duration));
            if (d == null || d.isZero() || d.isNegative()) {
                c.bad(t(AdminText.GRACE_BAD_LENGTH));
                return;
            }
            long ends = Time.now() + d.toMillis();
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            if (shield != null) {
                Result r = shield.setGrace(c.uuid(), ends);
                c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.GRACE_UNTIL, "date", Time.date(ends))) : r);
                return;
            }
            ctx.settings().set("grace.active", true);
            ctx.settings().set("grace.endsAt", ends);
            c.good(t(AdminText.GRACE_UNTIL, "date", Time.date(ends)));
        });
        grace.sub(server("off", t(AdminText.CMD_GRACE_OFF))).handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            if (shield != null) {
                Result r = shield.endGrace(c.uuid());
                c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.GRACE_IS_OFF)) : r);
                return;
            }
            ctx.settings().set("grace.active", false);
            ctx.settings().set("grace.endsAt", 0L);
            c.good(t(AdminText.GRACE_IS_OFF));
        });

        ServerCmd eotw = root.sub(server("eotw", t(AdminText.CMD_EOTW)));
        eotw.handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            boolean on = shield != null ? shield.eotwActive() : ctx.settings().bool("eotw.active");
            c.info(t(on ? AdminText.EOTW_IS_ON : AdminText.EOTW_IS_OFF));
        });
        for (boolean on : new boolean[] {true, false}) {
            eotw.sub(server(on ? "on" : "off", on ? t(AdminText.CMD_EOTW_ON) : t(AdminText.CMD_EOTW_OFF))).handler(c -> {
                String done = t(on ? AdminText.EOTW_STARTED : AdminText.EOTW_IS_OFF);
                ShieldService shield = ctx.serviceOrNull(ShieldService.class);
                if (shield != null) {
                    Result r = shield.setEotw(c.uuid(), on);
                    c.reply(r.success() && r.message().isBlank() ? Result.pass(done) : r);
                    return;
                }
                ctx.settings().set("eotw.active", on);
                c.good(done);
            });
        }
    }

    // ---- economy -------------------------------------------------------------

    private void registerEconomy(ServerCmd root) {
        ServerCmd eco = root.sub(server("eco", t(AdminText.CMD_ECO)));
        eco.handler(c -> c.info(t(AdminText.ECO_USAGE)));
        eco.sub(server("provider", t(AdminText.CMD_ECO_PROVIDER))).handler(c -> {
            EconomyService es = ctx.serviceOrNull(EconomyService.class);
            if (es == null) {
                c.bad(t(AdminText.NOT_INSTALLED));
                return;
            }
            c.info(t(es.enabled() ? AdminText.ECO_PROVIDER : AdminText.ECO_PROVIDER_OFF, "provider", es.providerName()));
        });
        for (String mode : new String[] {"give", "take", "set"}) {
            ServerCmd cmd = eco.sub(server(mode, switch (mode) {
                case "give" -> t(AdminText.CMD_ECO_GIVE);
                case "take" -> t(AdminText.CMD_ECO_TAKE);
                default -> t(AdminText.CMD_ECO_SET);
            }));
            RequiredArg<String> who = cmd.withRequiredArg("player", t(AdminText.ARG_PLAYER_NAME), ArgTypes.STRING);
            RequiredArg<Double> amount = cmd.withRequiredArg("amount", t(AdminText.ARG_AMOUNT), ArgTypes.DOUBLE);
            cmd.handler(c -> {
                Targets.Target t = anyone(c, c.arg(who));
                if (t == null) {
                    return;
                }
                double a = c.arg(amount);
                if (a < 0 || Double.isNaN(a) || Double.isInfinite(a)) {
                    c.bad(t(AdminText.AMOUNT_NEGATIVE));
                    return;
                }
                EconomyService es = economy();
                Result r = switch (mode) {
                    case "give" -> es.deposit(t.uuid(), a, "admin");
                    case "take" -> es.withdraw(t.uuid(), a, "admin");
                    default -> {
                        es.set(t.uuid(), a);
                        yield Result.pass();
                    }
                };
                if (r.failed()) {
                    c.reply(r);
                    return;
                }
                c.good(t(AdminText.BALANCE_NOW, "player", t.name(), "balance", es.format(es.balance(t.uuid()))));
            });
        }
    }

    // ---- maintenance ---------------------------------------------------------

    private void registerMaintenance(ServerCmd root) {
        root.sub(server("backup", t(AdminText.CMD_BACKUP))).handler(c -> {
            c.info(t(AdminText.BACKUP_STARTED));
            Path dir = ctx.repo().root();
            // A manual backup always keeps at least this one archive, even with automatic backups off.
            int keep = Math.max(1, ctx.settings().integer("storage.backupKeep"));
            ctx.runIo("backup", () -> {
                try {
                    Path archive = AdminModule.backup(ctx, keep);
                    c.good(t(AdminText.BACKUP_WRITTEN, "path", dir.relativize(archive)));
                } catch (Exception e) {
                    ctx.logger().at(Level.WARNING).withCause(e).log("Dominion backup failed");
                    c.bad(t(AdminText.BACKUP_FAILED));
                }
            });
        });

        root.sub(server("reload", t(AdminText.CMD_RELOAD))).handler(c -> {
            Messages.LoadReport words = Messages.reload();
            if (words.fileRead()) {
                c.good(Messages.get(CommonText.MESSAGES_RELOADED, "customised", words.customised()));
            } else if (!words.problems().isEmpty()) {
                c.warn(words.problems().get(0));
            }
            for (String p : words.problems().subList(words.fileRead() ? 0 : Math.min(1, words.problems().size()), Math.min(10, words.problems().size()))) {
                c.send(Msg.muted(" " + p));
            }
            Settings.LoadReport report = ctx.settings().reload();
            if (!report.fileRead()) {
                c.bad(report.problems().get(0));
                return;
            }
            if (report.problems().isEmpty()) {
                c.good(t(AdminText.SETTINGS_RELOADED));
                return;
            }
            c.warn(t(report.problems().size() == 1 ? AdminText.SETTINGS_RELOADED_PROBLEM : AdminText.SETTINGS_RELOADED_PROBLEMS,
                    "count", report.problems().size()));
            for (String p : report.problems().subList(0, Math.min(10, report.problems().size()))) {
                c.send(Msg.muted(" " + p));
            }
        });

        root.sub(server("diag", t(AdminText.CMD_DIAG))).handler(this::diag);

        root.sub(server("export", t(AdminText.CMD_EXPORT))).handler(c -> {
            ExportService export = ctx.serviceOrNull(ExportService.class);
            if (export == null) {
                c.bad(t(AdminText.NOT_INSTALLED));
                return;
            }
            ctx.runIo("export", () -> {
                try {
                    export.flush();
                    c.good(t(AdminText.EXPORT_WRITTEN, "path", export.snapshotPath()));
                } catch (Exception e) {
                    ctx.logger().at(Level.WARNING).withCause(e).log("Dominion export failed");
                    c.bad(t(AdminText.EXPORT_FAILED));
                }
            });
        });
    }

    // ---- strikes -------------------------------------------------------------

    private void registerStrikes(ServerCmd root) {
        ServerCmd strike = root.sub(server("strike", t(AdminText.CMD_STRIKE)));
        RequiredArg<String> strikeTag = strike.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        RequiredArg<Integer> strikePoints = strike.withRequiredArg("points", t(AdminText.ARG_STRIKE_POINTS), ArgTypes.INTEGER);
        RequiredArg<String> strikeReason = strike.withRequiredArg("reason", t(AdminText.ARG_REASON), ArgTypes.GREEDY_STRING);
        strike.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(strikeTag));
            if (f == null) {
                return;
            }
            int points = c.arg(strikePoints);
            String reason = c.arg(strikeReason).trim();
            if (points <= 0) {
                c.bad(t(AdminText.STRIKE_TOO_SMALL));
                return;
            }
            if (reason.isEmpty()) {
                c.bad(t(AdminText.STRIKE_NO_REASON));
                return;
            }
            Result r = progress.strike(c.uuid(), c.name(), f, points, reason);
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.STRIKE_GIVEN, "tag", f.tag(), "points", points)) : r);
        });

        ServerCmd list = root.sub(server("strikes", t(AdminText.CMD_STRIKES)));
        RequiredArg<String> listTag = list.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        list.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(listTag));
            if (f == null) {
                return;
            }
            List<Strike> all = progress.strikes(f);
            if (all == null || all.isEmpty()) {
                c.info(t(AdminText.STRIKES_NONE, "tag", f.tag()));
                return;
            }
            c.info(t(AdminText.STRIKES_TITLE, "tag", f.tag()));
            for (Strike st : all) {
                c.send(Message.join(Msg.colored(" " + st.number() + ". ", Msg.GOLD),
                        Msg.colored(t(st.points() == 1 ? AdminText.STRIKE_LINE_ONE : AdminText.STRIKE_LINE_MANY,
                                "points", st.points(), "reason", st.reason()), Msg.TEXT),
                        Msg.muted(", " + t(AdminText.STRIKE_GIVEN_BY, "by",
                                st.byName() == null || st.byName().isBlank() ? t(AdminText.STRIKE_BY_ADMIN) : st.byName(),
                                "ago", Time.ago(st.at())))));
            }
        });

        ServerCmd unstrike = root.sub(server("unstrike", t(AdminText.CMD_UNSTRIKE)));
        RequiredArg<String> unstrikeTag = unstrike.withRequiredArg("tag", t(AdminText.ARG_FACTION_TAG), ArgTypes.STRING);
        RequiredArg<Integer> unstrikeIndex = unstrike.withRequiredArg("index", t(AdminText.ARG_STRIKE_INDEX), ArgTypes.INTEGER);
        unstrike.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(unstrikeTag));
            if (f == null) {
                return;
            }
            int index = c.arg(unstrikeIndex);
            Result r = progress.unstrike(c.uuid(), f, index);
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t(AdminText.STRIKE_REMOVED, "index", index, "tag", f.tag())) : r);
        });
    }

    // ---- import --------------------------------------------------------------

    private void registerImport(ServerCmd root) {
        ServerCmd imp = root.sub(server("import", t(AdminText.CMD_IMPORT)));
        imp.handler(c -> {
            ImportService s = importer(c);
            if (s != null) {
                List<String> sources = s.sources();
                c.info(sources == null || sources.isEmpty() ? t(AdminText.IMPORT_NO_SOURCES)
                        : t(AdminText.IMPORT_SOURCES, "sources", String.join(", ", sources)));
            }
        });
        ServerCmd run = new ServerCmd(t(AdminText.CMD_IMPORT_RUN)).permission(Perms.admin());
        RequiredArg<String> runSource = run.withRequiredArg("source", t(AdminText.ARG_IMPORT_SOURCE), ArgTypes.STRING);
        run.handler(c -> {
            ImportService s = importer(c);
            if (s != null) {
                String source = c.arg(runSource).trim().toLowerCase(Locale.ROOT);
                List<String> known = s.sources();
                if (known == null || !known.contains(source)) {
                    c.bad(t(AdminText.IMPORT_NO_SUCH_SOURCE, "source", source));
                    return;
                }
                if (!previewedRecently(c, source)) {
                    c.bad(t(AdminText.IMPORT_PREVIEW_FIRST, "source", source));
                    return;
                }
                c.info(t(AdminText.IMPORTING, "source", source));
                ctx.runIo("import", () -> {
                    try {
                        Result r = s.run(c.uuid(), c.name(), source);
                        if (r != null && !r.success()) {
                            String why = r.message() == null || r.message().isBlank() ? t(AdminText.IMPORT_REFUSED) : r.message().trim();
                            if (!why.toLowerCase(Locale.ROOT).contains("preview")) {
                                why = why + (why.endsWith(".") ? "" : ".") + " " + t(AdminText.IMPORT_CHECK_PREVIEW, "source", source);
                            }
                            c.bad(why);
                        } else {
                            c.reply(r);
                        }
                    } catch (Exception e) {
                        ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Import from %s failed", source);
                        c.bad(t(AdminText.IMPORT_FAILED));
                    }
                });
            }
        });
        imp.variant(run);

        ServerCmd preview = imp.sub(server("preview", t(AdminText.CMD_IMPORT_PREVIEW)));
        RequiredArg<String> previewSource = preview.withRequiredArg("source", t(AdminText.ARG_IMPORT_SOURCE), ArgTypes.STRING);
        preview.handler(c -> {
            ImportService s = importer(c);
            if (s == null) {
                return;
            }
            String source = c.arg(previewSource).trim().toLowerCase(Locale.ROOT);
            ctx.runIo("import", () -> {
                try {
                    List<String> lines = s.preview(c.uuid(), source);
                    if (s.sources() != null && s.sources().contains(source)) {
                        previews.put(previewKey(c, source), System.currentTimeMillis());
                    }
                    if (lines == null || lines.isEmpty()) {
                        c.info(t(AdminText.IMPORT_NOTHING, "source", source));
                        return;
                    }
                    c.info(t(AdminText.IMPORT_PREVIEW_TITLE, "source", source));
                    for (String line : lines) {
                        c.send(Msg.colored(" " + line, Msg.TEXT));
                    }
                    c.info(t(AdminText.IMPORT_APPLY_HINT, "source", source));
                } catch (Exception e) {
                    ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Import preview from %s failed", source);
                    c.bad(t(AdminText.PREVIEW_FAILED));
                }
            });
        });
    }

    /** When each admin last previewed each import source, keyed by admin and source. */
    private final java.util.Map<String, Long> previews = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PREVIEW_WINDOW_MS = Duration.ofMinutes(10).toMillis();

    private static String previewKey(Call c, String source) {
        UUID id = c.uuid();
        return (id != null ? id.toString() : "console:" + c.name()) + "|" + source;
    }

    private boolean previewedRecently(Call c, String source) {
        long now = System.currentTimeMillis();
        previews.values().removeIf(at -> now - at > PREVIEW_WINDOW_MS);
        Long at = previews.get(previewKey(c, source));
        return at != null && now - at <= PREVIEW_WINDOW_MS;
    }

    private ImportService importer(Call c) {
        ImportService s = ctx.serviceOrNull(ImportService.class);
        if (s == null) {
            c.bad(t(AdminText.NOT_INSTALLED));
        }
        return s;
    }

    private ProgressService progress(Call c) {
        ProgressService p = ctx.serviceOrNull(ProgressService.class);
        if (p == null) {
            c.bad(t(AdminText.NOT_INSTALLED));
        }
        return p;
    }

    // ---- sieges --------------------------------------------------------------

    private void registerSieges(ServerCmd root) {
        ServerCmd siege = root.sub(server("siege", t(AdminText.CMD_SIEGE)));
        siege.handler(c -> {
            SiegeService s = ctx.serviceOrNull(SiegeService.class);
            if (s == null) {
                c.bad(t(AdminText.NOT_INSTALLED));
                return;
            }
            List<Siege> active = s.active();
            if (active.isEmpty()) {
                c.info(t(AdminText.SIEGES_NONE));
                return;
            }
            c.info(t(AdminText.SIEGES_TITLE));
            for (Siege s1 : active) {
                c.send(Message.join(Msg.colored(" " + IdPrefix.shortId(s1.id()), Msg.GOLD), Msg.muted("  " + siegeLabel(s1))));
            }
        });

        ServerCmd cancel = siege.sub(server("cancel", t(AdminText.CMD_SIEGE_CANCEL)));
        RequiredArg<String> id = cancel.withRequiredArg("id", t(AdminText.ARG_SIEGE_ID), ArgTypes.STRING);
        cancel.handler(c -> {
            SiegeService s = ctx.serviceOrNull(SiegeService.class);
            if (s == null) {
                c.bad(t(AdminText.NOT_INSTALLED));
                return;
            }
            Siege target = IdPrefix.resolve(c, c.arg(id), s.active(), Siege::id, this::siegeLabel, "siege");
            if (target == null) {
                return;
            }
            c.reply(s.adminCancel(c.uuid(), c.name(), target.id()));
        });
    }

    private String siegeLabel(Siege s) {
        return t(AdminText.SIEGE_LABEL, "attacker", tagOf(s.attacker()), "defender", tagOf(s.defender()),
                "phase", s.phase().name().toLowerCase(Locale.ROOT), "time", Time.remaining(s.phaseEndsAt() - Time.now()),
                "chunk", Keys.describe(s.chunkIndex()), "world", s.world());
    }

    private String tagOf(UUID factionId) {
        Faction f = factionId == null ? null : factions().byId(factionId);
        return f == null ? t(AdminText.DISBANDED_FACTION) : f.tag();
    }

    private void diag(Call c) {
        c.info(t(AdminText.DIAG_TITLE));
        c.send(Msg.muted(" " + t(AdminText.DIAG_COUNTS, "factions", ctx.repo().factions().size(), "players", ctx.repo().loadedPlayers().size())));
        for (String world : ctx.repo().claimWorlds()) {
            c.send(Msg.muted(" " + t(AdminText.DIAG_CLAIMS, "claims", ctx.repo().claimsIn(world).size(), "world", world)));
        }
        java.util.Map<String, String> states = ctx.plugin().moduleStates();
        int ok = 0;
        StringBuilder loaded = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : states.entrySet()) {
            if ("ok".equals(e.getValue())) {
                ok++;
                loaded.append(loaded.isEmpty() ? "" : ", ").append(e.getKey());
            } else {
                c.send(Message.join(Msg.colored(" " + e.getKey(), Msg.BAD), Msg.muted("  " + e.getValue())));
            }
        }
        c.send(Message.join(Msg.colored(" " + t(AdminText.DIAG_MODULES, "ok", ok, "total", moduleNames().size()), ok == moduleNames().size() ? Msg.GOOD : Msg.WARN),
                Msg.muted("  " + loaded)));
        for (String module : moduleNames()) {
            String shortName = module.substring(module.lastIndexOf('.') + 1);
            boolean present;
            try {
                Class.forName(module, false, AdminCommands.class.getClassLoader());
                present = true;
            } catch (ClassNotFoundException e) {
                present = false;
            }
            if (!present) {
                c.send(Message.join(Msg.colored(" " + shortName, Msg.BAD), Msg.muted("  " + t(AdminText.DIAG_NOT_IN_JAR))));
            }
        }
        CombatTagService tags = ctx.serviceOrNull(CombatTagService.class);
        c.send(Msg.muted(" " + (tags == null ? t(AdminText.DIAG_NO_COMBAT) : tags.status() + ".")));
        ExportService export = ctx.serviceOrNull(ExportService.class);
        if (export != null) {
            boolean on = ctx.settings().bool("export.enabled");
            c.send(Msg.muted(" " + (on ? t(AdminText.DIAG_SNAPSHOT_ON, "seconds", Math.max(1, ctx.settings().integer("export.intervalSeconds")),
                    "path", export.snapshotPath()) : t(AdminText.DIAG_SNAPSHOT_OFF, "path", export.snapshotPath()))));
        }
        c.send(Msg.muted(" " + t(AdminText.DIAG_PRESET, "preset", ctx.settings().text("misc.preset"),
                "seconds", Math.max(10, ctx.settings().integer("storage.autosaveSeconds")))));
    }

    /** /fa integrations lists TrackerAPI, Feats, Atlas and Coinage as bound, missing or off. */
    private void registerIntegrations(ServerCmd root) {
        root.sub(server("integrations", t(AdminText.CMD_INTEGRATIONS))).handler(c -> {
            dev.ryvrook.dominion.integration.Integrations status = ctx.serviceOrNull(dev.ryvrook.dominion.integration.Integrations.class);
            if (status == null) {
                c.send(Msg.warn(t(AdminText.INTEGRATIONS_OFF)));
                return;
            }
            c.send(Msg.info(t(AdminText.INTEGRATIONS_TITLE)));
            for (String line : status.lines()) {
                c.send(Msg.muted(" " + line));
            }
            c.send(Msg.muted(" " + t(AdminText.INTEGRATIONS_SNAPSHOT)));
        });
    }

    /** Wiring keeps its list package private, so it is read reflectively. */
    private static List<String> moduleNames() {
        try {
            Class<?> wiring = Class.forName("dev.ryvrook.dominion.Wiring");
            Field field = wiring.getDeclaredField("MODULES");
            field.setAccessible(true);
            return List.of((String[]) field.get(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }
}
