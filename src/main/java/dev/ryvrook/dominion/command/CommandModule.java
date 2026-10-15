package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.service.UiService;

import java.util.List;
import java.util.Set;

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
        PlayerCmd root = new PlayerCmd(rootName, "Factions. Opens the faction panel.");
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

        // Children attach bottom up before the root is registered and finalised.
        root.seal();
        ctx.plugin().getCommandRegistry().registerCommand(root);
        for (PlayerCmd cmd : economy.topLevel()) {
            if (PlayerCmd.skipped(cmd, off)) {
                continue;
            }
            cmd.seal();
            ctx.plugin().getCommandRegistry().registerCommand(cmd);
        }
        ctx.everyMinute(confirmations::sweep);
        ctx.onPlayerQuit(confirmations::clear);
    }
}
