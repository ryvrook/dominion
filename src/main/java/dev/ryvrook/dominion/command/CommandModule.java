package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Messages;

import java.util.List;

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
        Targets targets = new Targets(ctx);
        PlayerCmd root = new PlayerCmd("f", Messages.get(CommandText.DESC_FACTIONS_OPENS_THE_FACTION_PANEL));
        root.addAliases("faction", "factions");
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
            cmd.seal();
            ctx.plugin().getCommandRegistry().registerCommand(cmd);
        }
        ctx.everyMinute(confirmations::sweep);
        ctx.onPlayerQuit(confirmations::clear);
    }
}
