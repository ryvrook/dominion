package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Messages;

/** Shared plumbing for each group of /f sub commands. Services are looked up on every call. */
abstract class Section {
    protected final DominionContext ctx;
    protected final Confirmations confirmations;
    protected final Targets targets;

    Section(DominionContext ctx, Confirmations confirmations, Targets targets) {
        this.ctx = ctx;
        this.confirmations = confirmations;
        this.targets = targets;
    }

    abstract void register(PlayerCmd root);

    protected FactionService factions() { return ctx.service(FactionService.class); }
    protected ClaimService claims() { return ctx.service(ClaimService.class); }
    protected PowerService power() { return ctx.service(PowerService.class); }
    protected RelationService relations() { return ctx.service(RelationService.class); }
    protected RulesService rules() { return ctx.service(RulesService.class); }
    protected EconomyService economy() { return ctx.service(EconomyService.class); }
    protected UiService ui() { return ctx.service(UiService.class); }
    protected ChatService chat() { return ctx.service(ChatService.class); }
    protected HomeService homes() { return ctx.service(HomeService.class); }

    /** The caller's faction, or null after telling them they need one. */
    protected Faction requireFaction(Call call) {
        Faction f = factions().factionOf(call.uuid());
        if (f == null) {
            call.bad(Messages.get(CommandText.YOU_ARE_NOT_IN_A_FACTION));
        }
        return f;
    }

    /** Resolves a typed name or tells the caller nobody matched. */
    protected Targets.Target target(Call call, String name) {
        Targets.Target t = targets.resolve(call.uuid(), name);
        if (t == null) {
            call.bad(Messages.get(CommandText.NO_PLAYER_NAMED, "name", name));
        }
        return t;
    }

    /** Resolves a tag or tells the caller nothing matched. */
    protected Faction factionByTag(Call call, String tag) {
        Faction f = factions().byTag(tag);
        if (f == null) {
            call.bad(Messages.get(CommandText.NO_FACTION_WITH_TAG, "tag", tag));
        }
        return f;
    }

    /** An optional service, or null after telling the caller it is not installed. */
    protected <T> T feature(Call call, Class<T> type) {
        T s = ctx.serviceOrNull(type);
        if (s == null) {
            call.bad(Messages.get(NOT_INSTALLED));
        }
        return s;
    }

    /** The id of the sentence for a feature whose module is not installed. */
    static final String NOT_INSTALLED = CommandText.NOT_INSTALLED;

    protected static int clampPage(int page) {
        return Math.max(1, page);
    }
}
