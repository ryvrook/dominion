package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Messages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Registers /f with every sub command, plus /balance, /pay and /baltop. */
public final class CommandModule implements DominionModule {
    private final Confirmations confirmations = new Confirmations();

    public CommandModule() {}

    @Override
    public String name() {
        return "command";
    }

    @Override
    public void init(DominionContext ctx) {
        // Commands register in start once every service exists.
    }

    @Override
    public void start(DominionContext ctx) {
        confirmations.timeout(() -> ctx.settings().integer("commands.confirmSeconds") * 1000L);
        Targets targets = new Targets(ctx);
        // The names are read once here, so a change applies at the next start or /plugin reload.
        Settings settings = ctx.settings();
        String rootName = CommandNames.name(settings.text("commands.root"), CommandNames.DEFAULT_ROOT);
        Set<String> off = CommandNames.disabledIn(settings.text("commands.disabled"));
        PlayerCmd root = new PlayerCmd(rootName, Messages.get(CommandText.DESC_FACTIONS_OPENS_THE_FACTION_PANEL));
        List<String> aliases = CommandNames.aliases(settings.text("commands.rootAliases"), rootName);
        if (!aliases.isEmpty()) {
            root.addAliases(aliases.toArray(String[]::new));
        }
        root.skip(off);
        CommandNames.registeredRoot(rootName, off);
        root.handler(c -> ctx.service(UiService.class).openPanel(c.player(), "overview"));

        EconomyCommands economy = new EconomyCommands(ctx, confirmations, targets);
        List<Section> sections = List.of(
                new InfoCommands(ctx, confirmations, targets),
                new MembershipCommands(ctx, confirmations, targets),
                new IdentityCommands(ctx, confirmations, targets),
                new LandCommands(ctx, confirmations, targets),
                new DiplomacyCommands(ctx, confirmations, targets),
                new HomeCommands(ctx, confirmations, targets),
                new SocialCommands(ctx, confirmations, targets),
                economy,
                new WarCommands(ctx, confirmations, targets),
                new MarketCommands(ctx, confirmations, targets),
                new ProgressCommands(ctx, confirmations, targets),
                new NationCommands(ctx, confirmations, targets),
                new ZoneCommands(ctx, confirmations, targets),
                new ShopCommands(ctx, confirmations, targets));
        for (Section s : sections) {
            s.register(root);
        }
        root.gateChildren(gates(ctx));
        // Again with the names an alias in commands.disabled stands for, so the panel hides the right tabs.
        CommandNames.registeredRoot(rootName, root.skippedNames());

        // Children attach bottom up before the root is registered and finalised.
        root.seal();
        ctx.plugin().getCommandRegistry().registerCommand(root);
        for (PlayerCmd cmd : economy.topLevel()) {
            if (PlayerCmd.skipped(cmd, off)) {
                continue;
            }
            cmd.gate(() -> settings.bool("eco.enabled"));
            cmd.seal();
            ctx.plugin().getCommandRegistry().registerCommand(cmd);
        }
        ctx.everyMinute(confirmations::sweep);
        ctx.onPlayerQuit(confirmations::clear);
    }

    /** The feature switch behind each sub command of the player root, read on every use. */
    static Map<String, BooleanSupplier> gates(DominionContext ctx) {
        Settings s = ctx.settings();
        Map<String, BooleanSupplier> g = new HashMap<>();
        BooleanSupplier eco = () -> s.bool("eco.enabled");
        BooleanSupplier market = () -> s.bool("market.enabled") && s.bool("eco.enabled");
        BooleanSupplier home = () -> s.bool("home.enabled");
        for (String name : List.of("bank", "deposit", "withdraw", "pay")) {
            g.put(name, eco);
        }
        for (String name : List.of("market", "auction", "storefront")) {
            g.put(name, market);
        }
        for (String name : List.of("home", "sethome", "delhome", "allyhome", "warp", "setwarp", "delwarp", "warps", "stuck")) {
            g.put(name, home);
        }
        g.put("shop", () -> s.bool("shop.factionEnabled") && s.bool("eco.enabled"));
        g.put("vault", () -> s.bool("vault.enabled"));
        g.put("missions", () -> s.bool("missions.enabled"));
        g.put("upgrade", () -> s.bool("upgrades.enabled"));
        g.put("upgrades", () -> s.bool("upgrades.enabled"));
        g.put("chat", () -> s.bool("chat.enabled"));
        g.put("hud", () -> s.bool("hud.enabled"));
        g.put("map", () -> s.bool("map.enabled"));
        g.put("top", () -> s.bool("top.enabled"));
        g.put("nation", () -> s.bool("nation.enabled"));
        g.put("shield", () -> s.bool("shield.enabled"));
        g.put("siege", () -> s.bool("raid.enabled") && s.bool("raid.modeSiege"));
        return g;
    }
}
