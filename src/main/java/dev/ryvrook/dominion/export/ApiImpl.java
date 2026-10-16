package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionApi;
import dev.ryvrook.dominion.admin.AdminText;
import dev.ryvrook.dominion.api.EventBus;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.api.SiegeInfo;
import dev.ryvrook.dominion.api.TerritoryProvider;
import dev.ryvrook.dominion.faction.FactionText;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Thin public API over the services, published through ApiHolder. */
public final class ApiImpl implements DominionApi {
    private final DominionContext ctx;
    private final TerritoryProvider territories;

    ApiImpl(DominionContext ctx, TerritoryProvider territories) {
        this.ctx = ctx;
        this.territories = territories;
    }

    @Override
    public Optional<Faction> factionOf(UUID player) {
        FactionService f = ctx.serviceOrNull(FactionService.class);
        return player == null || f == null ? Optional.empty() : Optional.ofNullable(f.factionOf(player));
    }

    @Override
    public Optional<Faction> factionByTag(String tag) {
        return Optional.ofNullable(ctx.repo().factionByTag(tag));
    }

    @Override
    public Optional<Faction> factionAt(String world, int blockX, int blockZ) {
        return world == null ? Optional.empty() : Optional.ofNullable(ctx.repo().ownerAt(world, Keys.chunkAtBlock(blockX, blockZ)));
    }

    @Override
    public Relation relationBetween(UUID playerA, UUID playerB) {
        RelationService r = ctx.serviceOrNull(RelationService.class);
        return r == null || playerA == null || playerB == null ? Relation.NEUTRAL : r.betweenPlayers(playerA, playerB);
    }

    @Override
    public double powerOf(UUID player) {
        PowerService p = ctx.serviceOrNull(PowerService.class);
        return p == null || player == null ? 0.0 : p.powerOf(player);
    }

    @Override
    public double factionPower(UUID factionId) {
        PowerService p = ctx.serviceOrNull(PowerService.class);
        Faction f = ctx.repo().faction(factionId);
        return p == null || f == null ? 0.0 : p.factionPower(f);
    }

    @Override
    public int claimCount(UUID factionId) {
        return factionId == null ? 0 : ctx.repo().claimCount(factionId);
    }

    @Override
    public boolean raidable(UUID factionId) {
        ClaimService c = ctx.serviceOrNull(ClaimService.class);
        Faction f = ctx.repo().faction(factionId);
        return c != null && f != null && c.raidable(f);
    }

    @Override
    public boolean canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ) {
        RulesService r = ctx.serviceOrNull(RulesService.class);
        return r == null || r.canAct(player, action, world, blockX, blockY, blockZ).success();
    }

    @Override
    public List<Faction> top(int limit) {
        ProgressService p = ctx.serviceOrNull(ProgressService.class);
        if (p != null) {
            return p.top(limit);
        }
        List<Faction> list = new ArrayList<>(ctx.repo().factions(FactionType.NORMAL));
        list.sort(Comparator.comparingInt((Faction f) -> ctx.repo().claimCount(f.id())).reversed());
        return list.subList(0, Math.max(0, Math.min(limit, list.size())));
    }

    /** Reads the outpost boost of the player's faction from the events module, 1.0 when it is absent. */
    @Override
    public double boostFor(UUID player, String kind) {
        if (player == null || kind == null || kind.isBlank()) {
            return 1.0;
        }
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return 1.0;
        }
        Faction f = factionOf(player).orElse(null);
        if (f == null) {
            return 1.0;
        }
        try {
            double m = events.boostFor(f, kind);
            return Double.isFinite(m) && m > 0 ? m : 1.0;
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    @Override
    public TerritoryProvider territoryProvider() {
        return territories;
    }

    @Override
    public EventBus events() {
        return ctx.bus();
    }

    // ---- factions and sieges, API version 2 ----------------------------------

    @Override
    public List<Faction> factions() {
        List<Faction> list = new ArrayList<>(ctx.repo().factions());
        list.sort(Comparator.comparing((Faction f) -> f.tag() == null ? "" : f.tag().toLowerCase(Locale.ROOT)));
        return list;
    }

    @Override
    public Optional<Faction> factionById(UUID factionId) {
        return factionId == null ? Optional.empty() : Optional.ofNullable(ctx.repo().faction(factionId));
    }

    @Override
    public List<SiegeInfo> sieges() {
        SiegeService s = ctx.serviceOrNull(SiegeService.class);
        List<SiegeInfo> out = new ArrayList<>();
        if (s != null) {
            for (Siege siege : s.active()) {
                out.add(info(siege));
            }
            out.sort(Comparator.comparingLong(SiegeInfo::startedAt));
        }
        return out;
    }

    @Override
    public List<SiegeInfo> siegeHistory(UUID factionId, int limit) {
        SiegeService s = ctx.serviceOrNull(SiegeService.class);
        List<SiegeInfo> out = new ArrayList<>();
        if (s == null || limit <= 0) {
            return out;
        }
        // The service matches by a Faction object, which a disbanded faction no longer has, so the id is matched here.
        for (Siege siege : s.history(null, Integer.MAX_VALUE)) {
            if (out.size() >= limit) {
                break;
            }
            if (factionId == null || factionId.equals(siege.attacker()) || factionId.equals(siege.defender())) {
                out.add(info(siege));
            }
        }
        return out;
    }

    SiegeInfo info(Siege s) {
        long chunk = s.chunkIndex();
        return new SiegeInfo(s.id(), s.attacker(), tagOf(s.attacker()), s.defender(), tagOf(s.defender()), s.world(), chunk,
                Keys.chunkX(chunk), Keys.chunkZ(chunk), s.x(), s.y(), s.z(), s.phase().name(), s.phaseEndsAt(), s.startedAt(),
                s.startedBy(), s.active(), s.blocksBroken(), s.containersOpened(), s.attackerDeaths(), s.defenderDeaths());
    }

    private String tagOf(UUID factionId) {
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        return f == null ? null : f.tag();
    }

    // ---- faction chat, API version 2 -------------------------------------------

    @Override
    public Result sendFactionChat(UUID factionId, String name, String text) {
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat == null) {
            return Result.fail(Messages.get(CommonText.FEATURE_OFF));
        }
        Faction f = factionId == null ? null : ctx.repo().faction(factionId);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        return chat.sendExternal(f, name, text);
    }

    // ---- staff actions, API version 2 ------------------------------------------

    /** The name written to the faction log and the server log for an API caller that gave none. */
    static String by(String by) {
        return by == null || by.isBlank() ? "another mod" : by.strip();
    }

    private Faction faction(UUID factionId) {
        return factionId == null ? null : ctx.repo().faction(factionId);
    }

    @Override
    public Result disband(UUID factionId, String by) {
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        Faction f = faction(factionId);
        if (fs == null || f == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        String who = by(by);
        String tag = f.tag();
        Result r = fs.forceDisband(null, who, f);
        if (r.success()) {
            ctx.logger().at(Level.INFO).log("%s force disbanded %s through the Dominion API", who, tag);
            return Result.pass(Messages.get(AdminText.FORCE_DISBANDED, "tag", tag));
        }
        return r;
    }

    @Override
    public Result setLeader(UUID factionId, UUID player, String by) {
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        Faction f = faction(factionId);
        if (fs == null || f == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        String who = by(by);
        Result r = fs.forceLeader(null, who, f, player);
        if (r.success()) {
            ctx.logger().at(Level.INFO).log("%s made %s the leader of %s through the Dominion API", who, player, f.tag());
        }
        return r;
    }

    @Override
    public Result setPower(UUID player, double power, String by) {
        PowerService p = ctx.serviceOrNull(PowerService.class);
        if (p == null) {
            return Result.fail(Messages.get(CommonText.FEATURE_OFF));
        }
        if (!Double.isFinite(power)) {
            return Result.fail(Messages.get(AdminText.POWER_NOT_NUMBER));
        }
        if (!ctx.repo().knows(player)) {
            // ctx.player would make a record for any id, so an unknown one is refused instead.
            return Result.fail(Messages.get(FactionText.PLAYER_NOT_KNOWN));
        }
        p.setPower(player, power);
        double now = p.powerOf(player);
        String name = ctx.player(player).name();
        ctx.logger().at(Level.INFO).log("%s set the power of %s to %s through the Dominion API", by(by), player, Msg.number(now));
        return Result.pass(Messages.get(AdminText.POWER_NOW, "player", name == null || name.isBlank() ? player.toString() : name,
                "power", Msg.number(now)));
    }

    @Override
    public Result unclaim(String world, int chunkX, int chunkZ, String by) {
        ClaimService c = ctx.serviceOrNull(ClaimService.class);
        if (c == null) {
            return Result.fail(Messages.get(CommonText.FEATURE_OFF));
        }
        if (world == null || world.isBlank()) {
            return Result.fail(Messages.get(AdminText.NOTHING_TO_RELEASE));
        }
        Result r = c.adminUnclaim(null, world, Keys.chunk(chunkX, chunkZ));
        if (r.success()) {
            ctx.logger().at(Level.INFO).log("%s released chunk %d, %d in %s through the Dominion API", by(by), chunkX, chunkZ, world);
        }
        return r;
    }

    @Override
    public Result unclaimAll(UUID factionId, String by) {
        ClaimService c = ctx.serviceOrNull(ClaimService.class);
        Faction f = faction(factionId);
        if (c == null) {
            return Result.fail(Messages.get(CommonText.FEATURE_OFF));
        }
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        int released = 0;
        for (String world : List.copyOf(ctx.repo().claimWorlds())) {
            for (long index : ctx.repo().claimsOf(world, f.id())) {
                if (c.adminUnclaim(null, world, index).success()) {
                    released++;
                }
            }
        }
        if (released == 0) {
            return Result.fail(Messages.get(AdminText.NOTHING_TO_RELEASE));
        }
        ctx.logger().at(Level.INFO).log("%s released all %d chunks of %s through the Dominion API", by(by), released, f.tag());
        return Result.pass(Messages.get(AdminText.RELEASED, "count", released));
    }
}
