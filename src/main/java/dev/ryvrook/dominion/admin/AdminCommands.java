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

    private static final String NOT_INSTALLED = "That feature is not installed.";

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
        ServerCmd root = server(rootName, "Dominion admin tools.");
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
        // Attach every sub command and variant before the caller registers the root.
        root.seal();
        return root;
    }

    private void usage(Call c) {
        c.send(Msg.info("Admin commands"));
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
        root.sub(player("bypass", "Toggle protection bypass.")).handler(c -> {
            boolean on = !rules().bypassing(c.uuid());
            rules().setBypass(c.uuid(), on);
            c.good(on ? "Bypass is on. Protection no longer applies to you." : "Bypass is off.");
        });

        PlayerCmd settings = root.sub(player("settings", "Open the admin settings page."));
        settings.handler(c -> ui().openAdminSettings(c.player(), firstGroup()));
        PlayerCmd settingsGroup = playerVariant("Open one settings group.");
        RequiredArg<String> group = settingsGroup.withRequiredArg("group", "Settings group", ArgTypes.STRING);
        settingsGroup.handler(c -> ui().openAdminSettings(c.player(), c.arg(group)));
        settings.variant(settingsGroup);

        ServerCmd set = root.sub(server("set", "Change a setting."));
        RequiredArg<String> setKey = set.withRequiredArg("key", "Setting key", ArgTypes.STRING);
        RequiredArg<String> setValue = set.withRequiredArg("value", "New value", ArgTypes.GREEDY_STRING);
        set.handler(c -> {
            String key = c.arg(setKey);
            String value = c.arg(setValue).trim();
            if (ctx.settings().fileOnly(key)) {
                c.bad(key + " can only be changed in config.json. Edit the file and run /fa reload.");
                return;
            }
            String why = ctx.settings().trySet(key, value);
            if (why == null) {
                c.good(key + " is now " + ctx.settings().raw(key) + ".");
            } else {
                c.bad(why);
            }
        });

        ServerCmd get = root.sub(server("get", "Show a setting."));
        RequiredArg<String> getKey = get.withRequiredArg("key", "Setting key", ArgTypes.STRING);
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
                c.bad("There is no setting called " + key + ".");
                return;
            }
            c.info(key + " is " + ctx.settings().raw(key) + ".");
            c.send(Msg.muted(" " + def.label() + ", in " + def.group() + ", default " + def.defaultValue() + "."));
            Settings.Range range = ctx.settings().rangeOf(key);
            List<String> allowed = ctx.settings().choicesOf(key);
            if (range != null) {
                c.send(Msg.muted(" Allowed from " + Msg.number(range.min()) + " to "
                        + (range.max() >= Long.MAX_VALUE / 2.0 ? "any size" : Msg.number(range.max())) + "."));
            } else if (!allowed.isEmpty()) {
                c.send(Msg.muted(" Allowed values are " + String.join(", ", allowed) + "."));
            }
            if (ctx.settings().fileOnly(key)) {
                c.send(Msg.muted(" Only config.json can change it."));
            }
        });

        ServerCmd preset = root.sub(server("preset", "Apply a settings preset."));
        RequiredArg<String> presetName = preset.withRequiredArg("name", "classic, singleplayer, hardcore or peaceful", ArgTypes.STRING);
        preset.handler(c -> {
            String name = c.arg(presetName).toLowerCase(Locale.ROOT);
            if (!ctx.plugin().applyPreset(name)) {
                c.bad("Presets are " + String.join(", ", PRESETS) + ".");
                return;
            }
            c.good("Applied the " + name + " preset.");
        });
    }

    private String firstGroup() {
        List<Settings.Def> defs = ctx.settings().definitions();
        return defs.isEmpty() ? "" : defs.get(0).group();
    }

    // ---- zones and land ------------------------------------------------------

    private void registerZones(ServerCmd root) {
        ServerCmd zone = root.sub(server("zone", "Create server zones."));
        zone.handler(c -> c.info("Use /fa zone safe or /fa zone war with a name."));
        PlayerCmd safe = zone.sub(player("safe", "Create a safe zone."));
        RequiredArg<String> safeName = safe.withRequiredArg("name", "Zone name", ArgTypes.STRING);
        safe.handler(c -> c.reply(claims().createZone(c.uuid(), FactionType.SAFEZONE, c.arg(safeName))));
        PlayerCmd war = zone.sub(player("war", "Create a war zone."));
        RequiredArg<String> warName = war.withRequiredArg("name", "Zone name", ArgTypes.STRING);
        war.handler(c -> c.reply(claims().createZone(c.uuid(), FactionType.WARZONE, c.arg(warName))));
    }

    private void registerLand(ServerCmd root) {
        PlayerCmd claim = root.sub(player("claim", "Claim this chunk for any faction or zone."));
        RequiredArg<String> claimTag = claim.withRequiredArg("tag", "Faction or zone tag", ArgTypes.STRING);
        claim.handler(c -> adminClaim(c, c.arg(claimTag), 0));
        PlayerCmd claimRadius = playerVariant("Claim a square for any faction or zone.");
        RequiredArg<String> radiusTag = claimRadius.withRequiredArg("tag", "Faction or zone tag", ArgTypes.STRING);
        RequiredArg<Integer> radius = claimRadius.withRequiredArg("radius", "Radius in chunks", ArgTypes.INTEGER);
        claimRadius.handler(c -> adminClaim(c, c.arg(radiusTag), c.arg(radius)));
        claim.variant(claimRadius);

        PlayerCmd unclaim = root.sub(player("unclaim", "Release this chunk whoever owns it."));
        unclaim.handler(c -> adminUnclaim(c, 0));
        PlayerCmd unclaimRadius = playerVariant("Release a square whoever owns it.");
        RequiredArg<Integer> unRadius = unclaimRadius.withRequiredArg("radius", "Radius in chunks", ArgTypes.INTEGER);
        unclaimRadius.handler(c -> adminUnclaim(c, c.arg(unRadius)));
        unclaim.variant(unclaimRadius);

        root.sub(player("inspect", "Inspect the chunk you stand in.")).handler(this::inspect);
    }

    private void adminClaim(Call c, String tag, int radius) {
        Faction target = factions().byTag(tag);
        if (target == null) {
            c.bad("No faction or zone uses the tag " + tag + ".");
            return;
        }
        String world = c.world().getName();
        long center = Players.chunkOf(c.player());
        if (center == Long.MIN_VALUE) {
            c.bad("Your position is not known yet.");
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
            c.reply(last.failed() ? last : Result.fail("Nothing was claimed."));
        } else {
            c.good("Claimed " + ok + " of " + total + " chunks for " + target.tag() + ".");
        }
    }

    private void adminUnclaim(Call c, int radius) {
        String world = c.world().getName();
        long center = Players.chunkOf(c.player());
        if (center == Long.MIN_VALUE) {
            c.bad("Your position is not known yet.");
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
            c.bad("There was no claimed land to release.");
        } else {
            c.good("Released " + ok + " chunks.");
        }
    }

    private void inspect(Call c) {
        String world = c.world().getName();
        long chunk = Players.chunkOf(c.player());
        if (chunk == Long.MIN_VALUE) {
            c.bad("Your position is not known yet.");
            return;
        }
        c.info("Chunk " + Keys.describe(chunk) + " in " + world);
        Faction owner = claims().ownerAt(world, chunk);
        if (owner == null) {
            c.send(Msg.muted(" Wilderness, nobody owns this chunk."));
            return;
        }
        c.send(Message.join(Msg.muted(" Owned by "), Msg.colored(owner.tag(), owner.color()).bold(true),
                Msg.muted("  " + owner.type().name().toLowerCase(Locale.ROOT))));
        ClaimEntry entry = claims().claimAt(world, chunk);
        if (entry != null) {
            String by = entry.claimedBy() == null ? "the server" : targets.nameOf(entry.claimedBy());
            String zone = entry.zoneId() == null ? "" : " in zone " + entry.zoneId();
            c.send(Msg.muted(" Claimed " + Time.ago(entry.claimedAt()) + " by " + by + zone + "."));
        }
        if (owner.type() == FactionType.NORMAL) {
            boolean raidable = claims().raidable(owner);
            c.send(Msg.colored(raidable ? " Raidable right now." : " Not raidable right now.", raidable ? Msg.BAD : Msg.GOOD));
            c.send(Msg.muted(" Power " + Msg.number(power().factionPower(owner)) + " of " + Msg.number(power().factionMaxPower(owner))
                    + ", claims " + claims().claimCount(owner) + " of " + claims().maxClaims(owner)
                    + ", boost " + Msg.number(owner.powerBoost()) + "."));
        }
        c.send(Msg.muted(" Grace " + (rules().graceActive() ? "on" : "off") + ", end of the world " + (rules().eotwActive() ? "on" : "off")
                + ", your bypass " + (rules().bypassing(c.uuid()) ? "on" : "off") + "."));
    }

    // ---- force ---------------------------------------------------------------

    private void registerForce(ServerCmd root) {
        ServerCmd force = root.sub(server("force", "Override faction membership."));
        force.handler(c -> c.info("Use /fa force join, kick, leader, disband or rename."));

        ServerCmd join = force.sub(server("join", "Put a player into a faction."));
        RequiredArg<String> joinWho = join.withRequiredArg("player", "Player name", ArgTypes.STRING);
        RequiredArg<String> joinTag = join.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        join.handler(c -> {
            Targets.Target t = anyone(c, c.arg(joinWho));
            Faction f = t == null ? null : faction(c, c.arg(joinTag));
            if (f == null) {
                return;
            }
            Result r = factions().forceJoin(c.uuid(), c.name(), t.uuid(), t.name(), f);
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t.name() + " joined " + f.tag() + ".") : r);
        });

        ServerCmd kick = force.sub(server("kick", "Remove a player from their faction."));
        RequiredArg<String> kickWho = kick.withRequiredArg("player", "Player name", ArgTypes.STRING);
        kick.handler(c -> {
            Targets.Target t = anyone(c, c.arg(kickWho));
            if (t == null) {
                return;
            }
            Result r = factions().forceKick(c.uuid(), c.name(), t.uuid());
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t.name() + " was removed from their faction.") : r);
        });

        ServerCmd leader = force.sub(server("leader", "Make a member the leader."));
        RequiredArg<String> leaderTag = leader.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        RequiredArg<String> leaderWho = leader.withRequiredArg("player", "Player name", ArgTypes.STRING);
        leader.handler(c -> {
            Faction f = faction(c, c.arg(leaderTag));
            Targets.Target t = f == null ? null : anyone(c, c.arg(leaderWho));
            if (t == null) {
                return;
            }
            Result r = factions().forceLeader(c.uuid(), c.name(), f, t.uuid());
            c.reply(r.success() && r.message().isBlank() ? Result.pass(t.name() + " now leads " + f.tag() + ".") : r);
        });

        ServerCmd disband = force.sub(server("disband", "Disband any faction."));
        RequiredArg<String> disbandTag = disband.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
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
            c.reply(r.success() && r.message().isBlank() ? Result.pass(tag + " was disbanded.") : r);
        });

        ServerCmd rename = force.sub(server("rename", "Change any faction's tag and name."));
        RequiredArg<String> renameTag = rename.withRequiredArg("tag", "Current faction tag", ArgTypes.STRING);
        RequiredArg<String> renameNew = rename.withRequiredArg("newTag", "New tag", ArgTypes.STRING);
        RequiredArg<String> renameName = rename.withRequiredArg("name", "New display name", ArgTypes.GREEDY_STRING);
        rename.handler(c -> {
            Faction f = faction(c, c.arg(renameTag));
            if (f == null) {
                return;
            }
            String newTag = c.arg(renameNew);
            Result r = factions().forceRename(c.uuid(), c.name(), f, newTag, c.arg(renameName).trim());
            c.reply(r.success() && r.message().isBlank() ? Result.pass("The faction is now " + newTag + ".") : r);
        });
    }

    private Targets.Target anyone(Call c, String name) {
        Targets.Target t = targets.resolveAnywhere(name);
        if (t == null) {
            c.bad("No player named " + name + " could be found.");
        }
        return t;
    }

    private Faction faction(Call c, String tag) {
        Faction f = factions().byTag(tag);
        if (f == null) {
            c.bad("No faction uses the tag " + tag + ".");
        }
        return f;
    }

    private void adminAudit(Call c, Faction f, String text) {
        factions().audit(f, c.uuid(), c.name(), "admin", text);
    }

    // ---- power ---------------------------------------------------------------

    private void registerPower(ServerCmd root) {
        ServerCmd power = root.sub(server("power", "Change a player's power."));
        power.handler(c -> c.info("Use /fa power set or /fa power add with a player and a value."));
        for (boolean add : new boolean[] {false, true}) {
            ServerCmd cmd = power.sub(server(add ? "add" : "set", add ? "Add to a player's power." : "Set a player's power."));
            RequiredArg<String> who = cmd.withRequiredArg("player", "Player name", ArgTypes.STRING);
            RequiredArg<Double> value = cmd.withRequiredArg("value", "Power", ArgTypes.DOUBLE);
            cmd.handler(c -> {
                Targets.Target t = anyone(c, c.arg(who));
                if (t == null) {
                    return;
                }
                if (!Double.isFinite(c.arg(value))) {
                    c.bad("Give the power as a plain number such as 5 or -2.5.");
                    return;
                }
                if (add) {
                    power().addPower(t.uuid(), c.arg(value));
                } else {
                    power().setPower(t.uuid(), c.arg(value));
                }
                c.good(t.name() + " now has " + Msg.number(power().powerOf(t.uuid())) + " power.");
            });
        }

        ServerCmd boost = root.sub(server("boost", "Set a faction's power boost."));
        RequiredArg<String> boostTag = boost.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        RequiredArg<Double> boostValue = boost.withRequiredArg("value", "Boost", ArgTypes.DOUBLE);
        boost.handler(c -> {
            Faction f = faction(c, c.arg(boostTag));
            if (f == null) {
                return;
            }
            double v = c.arg(boostValue);
            if (!Double.isFinite(v)) {
                c.bad("Give the boost as a plain number such as 10 or -5.");
                return;
            }
            f.setPowerBoost(v);
            adminAudit(c, f, "Set the power boost to " + Msg.number(v));
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            c.good(f.tag() + " now has a power boost of " + Msg.number(v) + ".");
        });
    }

    // ---- grace and eotw ------------------------------------------------------

    /**
     * Grace and end of the world go through the shield service when it is installed. Without it
     * the settings are flipped directly as before, so the commands keep working on a server
     * that runs without shields.
     */
    private void registerModes(ServerCmd root) {
        ServerCmd grace = root.sub(server("grace", "Start or stop the grace period."));
        grace.handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            boolean on = shield != null ? shield.graceActive() : ctx.settings().bool("grace.active");
            long ends = shield != null ? shield.graceEndsAt() : ctx.settings().longValue("grace.endsAt");
            if (!on) {
                c.info("Grace is off.");
            } else if (ends > 0) {
                c.info("Grace is on and ends in " + Time.remaining(ends - Time.now()) + ".");
            } else {
                c.info("Grace is on with no end time.");
            }
        });
        ServerCmd graceOn = grace.sub(server("on", "Start a grace period."));
        RequiredArg<String> duration = graceOn.withRequiredArg("duration", "Length such as 2d or 12h", ArgTypes.STRING);
        graceOn.handler(c -> {
            Duration d = Time.parse(c.arg(duration));
            if (d == null || d.isZero() || d.isNegative()) {
                c.bad("Give a length such as 7d, 12h or 1d12h.");
                return;
            }
            long ends = Time.now() + d.toMillis();
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            if (shield != null) {
                Result r = shield.setGrace(c.uuid(), ends);
                c.reply(r.success() && r.message().isBlank() ? Result.pass("Grace is on until " + Time.date(ends) + ".") : r);
                return;
            }
            ctx.settings().set("grace.active", true);
            ctx.settings().set("grace.endsAt", ends);
            c.good("Grace is on until " + Time.date(ends) + ".");
        });
        grace.sub(server("off", "End the grace period now.")).handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            if (shield != null) {
                Result r = shield.endGrace(c.uuid());
                c.reply(r.success() && r.message().isBlank() ? Result.pass("Grace is off.") : r);
                return;
            }
            ctx.settings().set("grace.active", false);
            ctx.settings().set("grace.endsAt", 0L);
            c.good("Grace is off.");
        });

        ServerCmd eotw = root.sub(server("eotw", "Toggle end of the world mode."));
        eotw.handler(c -> {
            ShieldService shield = ctx.serviceOrNull(ShieldService.class);
            boolean on = shield != null ? shield.eotwActive() : ctx.settings().bool("eotw.active");
            c.info("End of the world mode is " + (on ? "on." : "off."));
        });
        for (boolean on : new boolean[] {true, false}) {
            eotw.sub(server(on ? "on" : "off", on ? "Start end of the world mode." : "Stop end of the world mode.")).handler(c -> {
                String done = on ? "End of the world mode is on. Every faction can be raided." : "End of the world mode is off.";
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
        ServerCmd eco = root.sub(server("eco", "Change player balances."));
        eco.handler(c -> c.info("Use /fa eco give, take or set with a player and an amount, or /fa eco provider."));
        eco.sub(server("provider", "Show which economy provider is active.")).handler(c -> {
            EconomyService es = ctx.serviceOrNull(EconomyService.class);
            if (es == null) {
                c.bad(NOT_INSTALLED);
                return;
            }
            c.info("The economy provider is " + es.providerName() + (es.enabled() ? "." : " and the economy is turned off."));
        });
        for (String mode : new String[] {"give", "take", "set"}) {
            ServerCmd cmd = eco.sub(server(mode, switch (mode) {
                case "give" -> "Give money to a player.";
                case "take" -> "Take money from a player.";
                default -> "Set a player's balance.";
            }));
            RequiredArg<String> who = cmd.withRequiredArg("player", "Player name", ArgTypes.STRING);
            RequiredArg<Double> amount = cmd.withRequiredArg("amount", "Amount", ArgTypes.DOUBLE);
            cmd.handler(c -> {
                Targets.Target t = anyone(c, c.arg(who));
                if (t == null) {
                    return;
                }
                double a = c.arg(amount);
                if (a < 0 || Double.isNaN(a) || Double.isInfinite(a)) {
                    c.bad("The amount cannot be negative.");
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
                c.good(t.name() + " now has " + es.format(es.balance(t.uuid())) + ".");
            });
        }
    }

    // ---- maintenance ---------------------------------------------------------

    private void registerMaintenance(ServerCmd root) {
        root.sub(server("backup", "Zip the Dominion data folder.")).handler(c -> {
            c.info("Backing up Dominion data.");
            Path dir = ctx.repo().root();
            // A manual backup always keeps at least this one archive, even with automatic backups off.
            int keep = Math.max(1, ctx.settings().integer("storage.backupKeep"));
            ctx.runIo("backup", () -> {
                try {
                    Path archive = AdminModule.backup(ctx, keep);
                    c.good("Backup written to " + dir.relativize(archive) + ".");
                } catch (Exception e) {
                    ctx.logger().at(Level.WARNING).withCause(e).log("Dominion backup failed");
                    c.bad("The backup failed. Check the server log.");
                }
            });
        });

        root.sub(server("reload", "Reload settings and messages from disk.")).handler(c -> {
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
                c.good("Settings reloaded from config.json.");
                return;
            }
            c.warn("Settings reloaded with " + report.problems().size()
                    + (report.problems().size() == 1 ? " problem." : " problems.") + " config.json was left as it is.");
            for (String p : report.problems().subList(0, Math.min(10, report.problems().size()))) {
                c.send(Msg.muted(" " + p));
            }
        });

        root.sub(server("diag", "Show diagnostics.")).handler(this::diag);

        root.sub(server("export", "Write the web map snapshot now.")).handler(c -> {
            ExportService export = ctx.serviceOrNull(ExportService.class);
            if (export == null) {
                c.bad(NOT_INSTALLED);
                return;
            }
            ctx.runIo("export", () -> {
                try {
                    export.flush();
                    c.good("The snapshot is at " + export.snapshotPath() + ".");
                } catch (Exception e) {
                    ctx.logger().at(Level.WARNING).withCause(e).log("Dominion export failed");
                    c.bad("The export failed. Check the server log.");
                }
            });
        });
    }

    // ---- strikes -------------------------------------------------------------

    private void registerStrikes(ServerCmd root) {
        ServerCmd strike = root.sub(server("strike", "Give a faction a strike."));
        RequiredArg<String> strikeTag = strike.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        RequiredArg<Integer> strikePoints = strike.withRequiredArg("points", "Points to take", ArgTypes.INTEGER);
        RequiredArg<String> strikeReason = strike.withRequiredArg("reason", "Reason", ArgTypes.GREEDY_STRING);
        strike.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(strikeTag));
            if (f == null) {
                return;
            }
            int points = c.arg(strikePoints);
            String reason = c.arg(strikeReason).trim();
            if (points <= 0) {
                c.bad("A strike must be worth at least one point.");
                return;
            }
            if (reason.isEmpty()) {
                c.bad("Give a reason for the strike.");
                return;
            }
            Result r = progress.strike(c.uuid(), c.name(), f, points, reason);
            c.reply(r.success() && r.message().isBlank() ? Result.pass(f.tag() + " received a strike of " + points + " points.") : r);
        });

        ServerCmd list = root.sub(server("strikes", "List a faction's strikes with their numbers."));
        RequiredArg<String> listTag = list.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        list.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(listTag));
            if (f == null) {
                return;
            }
            List<Strike> all = progress.strikes(f);
            if (all == null || all.isEmpty()) {
                c.info(f.tag() + " has no strikes.");
                return;
            }
            c.info("Strikes of " + f.tag() + ". Remove one with /fa unstrike and its number.");
            for (Strike st : all) {
                c.send(Message.join(Msg.colored(" " + st.number() + ". ", Msg.GOLD),
                        Msg.colored(st.points() + (st.points() == 1 ? " point" : " points") + " for " + st.reason(), Msg.TEXT),
                        Msg.muted(", given by " + (st.byName() == null || st.byName().isBlank() ? "an admin" : st.byName())
                                + " " + Time.ago(st.at()))));
            }
        });

        ServerCmd unstrike = root.sub(server("unstrike", "Remove one of a faction's strikes."));
        RequiredArg<String> unstrikeTag = unstrike.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        RequiredArg<Integer> unstrikeIndex = unstrike.withRequiredArg("index", "Strike number from /fa strikes, starting at 1", ArgTypes.INTEGER);
        unstrike.handler(c -> {
            ProgressService progress = progress(c);
            Faction f = progress == null ? null : faction(c, c.arg(unstrikeTag));
            if (f == null) {
                return;
            }
            int index = c.arg(unstrikeIndex);
            Result r = progress.unstrike(c.uuid(), f, index);
            c.reply(r.success() && r.message().isBlank() ? Result.pass("Strike " + index + " was removed from " + f.tag() + ".") : r);
        });
    }

    // ---- import --------------------------------------------------------------

    private void registerImport(ServerCmd root) {
        ServerCmd imp = root.sub(server("import", "Import claims and factions from another mod."));
        imp.handler(c -> {
            ImportService s = importer(c);
            if (s != null) {
                List<String> sources = s.sources();
                c.info(sources == null || sources.isEmpty() ? "No import sources are available."
                        : "Import sources are " + String.join(", ", sources) + ". Preview first with /fa import preview and a source.");
            }
        });
        ServerCmd run = new ServerCmd("Import from one source.").permission(Perms.admin());
        RequiredArg<String> runSource = run.withRequiredArg("source", "Source such as simpleclaims", ArgTypes.STRING);
        run.handler(c -> {
            ImportService s = importer(c);
            if (s != null) {
                String source = c.arg(runSource).trim().toLowerCase(Locale.ROOT);
                List<String> known = s.sources();
                if (known == null || !known.contains(source)) {
                    c.bad("There is no import source called " + source + ". Run /fa import to list them.");
                    return;
                }
                if (!previewedRecently(c, source)) {
                    c.bad("Preview the import first with /fa import preview " + source
                            + " and then run /fa import " + source + " within 10 minutes.");
                    return;
                }
                c.info("Importing from " + source + ". This can take a while on large data.");
                ctx.runIo("import", () -> {
                    try {
                        Result r = s.run(c.uuid(), c.name(), source);
                        if (r != null && !r.success()) {
                            String why = r.message() == null || r.message().isBlank() ? "The import was refused." : r.message().trim();
                            if (!why.toLowerCase(Locale.ROOT).contains("preview")) {
                                why = why + (why.endsWith(".") ? "" : ".") + " Check /fa import preview " + source + " before trying again.";
                            }
                            c.bad(why);
                        } else {
                            c.reply(r);
                        }
                    } catch (Exception e) {
                        ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Import from %s failed", source);
                        c.bad("The import failed. Check the server log.");
                    }
                });
            }
        });
        imp.variant(run);

        ServerCmd preview = imp.sub(server("preview", "Show what an import would change without changing anything."));
        RequiredArg<String> previewSource = preview.withRequiredArg("source", "Source such as simpleclaims", ArgTypes.STRING);
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
                        c.info("The import from " + source + " would change nothing.");
                        return;
                    }
                    c.info("Import preview for " + source);
                    for (String line : lines) {
                        c.send(Msg.colored(" " + line, Msg.TEXT));
                    }
                    c.info("Run /fa import " + source + " within 10 minutes to apply it.");
                } catch (Exception e) {
                    ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Import preview from %s failed", source);
                    c.bad("The preview failed. Check the server log.");
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
            c.bad(NOT_INSTALLED);
        }
        return s;
    }

    private ProgressService progress(Call c) {
        ProgressService p = ctx.serviceOrNull(ProgressService.class);
        if (p == null) {
            c.bad(NOT_INSTALLED);
        }
        return p;
    }

    // ---- sieges --------------------------------------------------------------

    private void registerSieges(ServerCmd root) {
        ServerCmd siege = root.sub(server("siege", "List sieges in progress with their ids."));
        siege.handler(c -> {
            SiegeService s = ctx.serviceOrNull(SiegeService.class);
            if (s == null) {
                c.bad(NOT_INSTALLED);
                return;
            }
            List<Siege> active = s.active();
            if (active.isEmpty()) {
                c.info("No sieges are running right now.");
                return;
            }
            c.info("Sieges in progress");
            for (Siege s1 : active) {
                c.send(Message.join(Msg.colored(" " + IdPrefix.shortId(s1.id()), Msg.GOLD), Msg.muted("  " + siegeLabel(s1))));
            }
        });

        ServerCmd cancel = siege.sub(server("cancel", "Cancel a siege by id."));
        RequiredArg<String> id = cancel.withRequiredArg("id", "Siege id", ArgTypes.STRING);
        cancel.handler(c -> {
            SiegeService s = ctx.serviceOrNull(SiegeService.class);
            if (s == null) {
                c.bad(NOT_INSTALLED);
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
        return tagOf(s.attacker()) + " against " + tagOf(s.defender()) + ", " + s.phase().name().toLowerCase(Locale.ROOT)
                + " with " + Time.remaining(s.phaseEndsAt() - Time.now()) + " left at chunk " + Keys.describe(s.chunkIndex())
                + " in " + s.world();
    }

    private String tagOf(UUID factionId) {
        Faction f = factionId == null ? null : factions().byId(factionId);
        return f == null ? "a disbanded faction" : f.tag();
    }

    private void diag(Call c) {
        c.info("Dominion diagnostics");
        c.send(Msg.muted(" " + ctx.repo().factions().size() + " factions and " + ctx.repo().loadedPlayers().size() + " loaded players."));
        for (String world : ctx.repo().claimWorlds()) {
            c.send(Msg.muted(" " + ctx.repo().claimsIn(world).size() + " claims in " + world + "."));
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
        c.send(Message.join(Msg.colored(" " + ok + " of " + moduleNames().size() + " modules running", ok == moduleNames().size() ? Msg.GOOD : Msg.WARN),
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
                c.send(Message.join(Msg.colored(" " + shortName, Msg.BAD), Msg.muted("  not in the jar")));
            }
        }
        CombatTagService tags = ctx.serviceOrNull(CombatTagService.class);
        c.send(Msg.muted(" " + (tags == null ? "The combat module is not running, so only Dominion's own combat tag applies"
                : tags.status()) + "."));
        ExportService export = ctx.serviceOrNull(ExportService.class);
        if (export != null) {
            boolean on = ctx.settings().bool("export.enabled");
            c.send(Msg.muted(" Map snapshot " + (on ? "is written every " + Math.max(1, ctx.settings().integer("export.intervalSeconds"))
                    + " seconds to " : "is turned off. The last one is at ") + export.snapshotPath() + "."));
        }
        c.send(Msg.muted(" Settings preset " + ctx.settings().text("misc.preset") + ", autosave every "
                + Math.max(10, ctx.settings().integer("storage.autosaveSeconds")) + " seconds."));
    }

    /** /fa integrations lists TrackerAPI, Feats and Atlas as bound, missing or off. */
    private void registerIntegrations(ServerCmd root) {
        root.sub(server("integrations", "List the sibling mods Dominion links to.")).handler(c -> {
            dev.ryvrook.dominion.integration.Integrations status = ctx.serviceOrNull(dev.ryvrook.dominion.integration.Integrations.class);
            if (status == null) {
                c.send(Msg.warn("The integration module is not running, so Dominion runs on its own."));
                return;
            }
            c.send(Msg.info("Integrations"));
            for (String line : status.lines()) {
                c.send(Msg.muted(" " + line));
            }
            c.send(Msg.muted(" Chronicle Stats and Atlas also read the map snapshot, which needs no link."));
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
