package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Faction value and leaderboard, upgrades, missions, levels and strikes. */
public final class ProgressServiceImpl implements ProgressService {
    private static final long TOP_CACHE_MS = 60_000L;
    private static final String MISSION_PREFIX = "mission.";

    private static final List<String> UPGRADE_KEYS = List.of("members", "regen", "maxpower", "warps", "vault", "shield", "immunity");
    private static final Map<String, Integer> MAX_LEVELS = Map.of(
            "members", 4, "regen", 3, "maxpower", 3, "warps", 4, "vault", 4, "shield", 2, "immunity", 3);
    private static final Map<String, String> UPGRADE_EFFECTS = Map.of(
            "members", "Members plus 5 per level",
            "regen", "Power regenerates 25 percent faster for every member per level",
            "maxpower", "Maximum power plus 2 per member per level",
            "warps", "Warps plus 2 per level",
            "vault", "Vault plus 1 page per level",
            "shield", "The shield covers one more ring of chunks around the home per level",
            "immunity", "One more hour of siege immunity after a defence per level");

    private final DominionContext ctx;
    private final Strikes strikes;
    private final Object upgradeLock = new Object();

    private volatile List<Faction> topCache = List.of();
    private volatile Map<UUID, Integer> rankCache = Map.of();
    private volatile long topCachedAt;

    private long lastDay = Long.MIN_VALUE;
    private long lastWeek = Long.MIN_VALUE;

    public ProgressServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.strikes = new Strikes(ctx.files(), ctx.repo().root().resolve("progress").resolve("strikes.json"));
        this.strikes.load();
    }

    // ---- value and leaderboard ---------------------------------------------

    @Override
    public double value(Faction faction) {
        return valueBreakdown(faction).getOrDefault("total", 0.0);
    }

    @Override
    public Map<String, Double> valueBreakdown(Faction faction) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (faction == null) {
            out.put("total", 0.0);
            return out;
        }
        var s = ctx.settings();
        PowerService power = ctx.serviceOrNull(PowerService.class);
        out.put("claims", ctx.repo().claimCount(faction.id()) * s.decimal("value.weightClaims"));
        out.put("members", faction.memberCount() * s.decimal("value.weightMembers"));
        out.put("power", (power == null ? 0.0 : power.factionPower(faction)) * s.decimal("value.weightPower"));
        out.put("kills", kills(faction) * s.decimal("value.weightKills"));
        out.put("sieges", faction.stat("siegesWon") * s.decimal("value.weightSieges"));
        out.put("bank", faction.bank() / 100.0 * s.decimal("value.weightBankPer100"));
        out.put("outposts", faction.stat("outpostSeconds") / 3600.0 * s.decimal("value.weightOutpostHours"));
        double subtotal = 0;
        for (double v : out.values()) {
            subtotal += v;
        }
        double percent = Math.min(100.0, Math.max(0.0, strikes.weight(faction.id()) * s.decimal("value.strikePercent")));
        double penalty = subtotal > 0 ? subtotal * percent / 100.0 : 0.0;
        out.put("strikes", -penalty);
        out.put("total", Math.round((subtotal - penalty) * 100.0) / 100.0);
        return out;
    }

    /** Faction kills. Uses the faction stat when it is maintained, otherwise the member records. */
    public static long kills(Faction faction) {
        long sum = 0;
        for (Member m : faction.members()) {
            sum += m.kills();
        }
        return Math.max(faction.stat("kills"), sum);
    }

    public static long deaths(Faction faction) {
        long sum = 0;
        for (Member m : faction.members()) {
            sum += m.deaths();
        }
        return Math.max(faction.stat("deaths"), sum);
    }

    @Override
    public List<Faction> top(int limit) {
        List<Faction> sorted = sortedCached();
        return new ArrayList<>(sorted.subList(0, Math.max(0, Math.min(limit, sorted.size()))));
    }

    @Override
    public int rank(Faction faction) {
        if (faction == null || faction.type() != FactionType.NORMAL) {
            return 0;
        }
        sortedCached();
        Integer r = rankCache.get(faction.id());
        return r == null ? 0 : r;
    }

    private List<Faction> sortedCached() {
        long now = Time.now();
        if (now - topCachedAt < TOP_CACHE_MS) {
            return topCache;
        }
        synchronized (this) {
            if (now - topCachedAt < TOP_CACHE_MS) {
                return topCache;
            }
            Map<UUID, Double> values = new HashMap<>();
            List<Faction> list = new ArrayList<>(ctx.repo().factions(FactionType.NORMAL));
            for (Faction f : list) {
                values.put(f.id(), value(f));
            }
            list.sort(Comparator.comparingDouble((Faction f) -> values.get(f.id())).reversed()
                    .thenComparing(f -> f.tag().toLowerCase(Locale.ROOT)));
            Map<UUID, Integer> ranks = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                ranks.put(list.get(i).id(), i + 1);
            }
            topCache = List.copyOf(list);
            rankCache = Map.copyOf(ranks);
            topCachedAt = now;
            return topCache;
        }
    }

    /** Forces the next top or rank call to recompute. */
    public void invalidate() {
        topCachedAt = 0;
    }

    // ---- upgrades ------------------------------------------------------------

    @Override
    public List<String> upgradeKeys() {
        return UPGRADE_KEYS;
    }

    @Override
    public int upgradeLevel(Faction faction, String key) {
        return faction == null || key == null ? 0 : faction.upgrade(key);
    }

    @Override
    public int upgradeMaxLevel(String key) {
        return key == null ? 0 : MAX_LEVELS.getOrDefault(key, 0);
    }

    @Override
    public double upgradeCost(Faction faction, String key) {
        return cost(ctx.settings().decimal("upgrades.baseCost"), ctx.settings().decimal("upgrades.costGrowth"),
                upgradeLevel(faction, key));
    }

    /**
     * Price of the next level when the faction owns level levels, rounded to cents. Each level costs
     * growth times the one before. A negative setting makes the upgrade free and a broken one makes it unaffordable.
     */
    static double cost(double base, double growth, int level) {
        double cost = base * Math.pow(growth, Math.max(0, level));
        if (Double.isNaN(cost) || cost == Double.POSITIVE_INFINITY) {
            // A broken setting makes the upgrade unaffordable rather than free.
            return Double.MAX_VALUE;
        }
        if (cost <= 0) {
            return 0.0;
        }
        return Math.round(cost * 100.0) / 100.0;
    }

    /** Points price of an upgrade, the coin price rounded up. */
    static int pointsCost(double cost) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(cost));
    }

    @Override
    public String upgradeDescription(String key, int level) {
        String effect = key == null ? null : UPGRADE_EFFECTS.get(key);
        if (effect == null) {
            return "Unknown upgrade.";
        }
        return effect + ", level " + level + " of " + upgradeMaxLevel(key);
    }

    @Override
    public Result buyUpgrade(UUID actor, String key) {
        FactionService factions = ctx.service(FactionService.class);
        Faction f = factions.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (f.isSystem()) {
            return Result.fail("Server zones cannot buy upgrades.");
        }
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (!MAX_LEVELS.containsKey(k)) {
            return Result.fail("There is no upgrade called " + key + ". Choose one of " + String.join(", ", UPGRADE_KEYS) + ".");
        }
        if (!factions.can(actor, FactionAction.SETTINGS)) {
            return Result.fail("Your rank cannot buy upgrades.");
        }
        int level = f.upgrade(k);
        int max = upgradeMaxLevel(k);
        if (level >= max) {
            return Result.fail("This upgrade is already at its highest level.");
        }
        double cost = upgradeCost(f, k);
        String label = k + " upgrade level " + (level + 1);
        String paid;
        if (ctx.settings().bool("upgrades.useCoins")) {
            EconomyService eco = ctx.serviceOrNull(EconomyService.class);
            if (eco == null || !eco.enabled()) {
                return Result.fail("The economy is turned off so upgrades cannot be bought with coins.");
            }
            // The upgrade lock, not the faction monitor, guards the purchase. The economy takes the
            // ledger lock and then the faction monitor, so holding the monitor here could deadlock.
            synchronized (upgradeLock) {
                if (f.upgrade(k) != level) {
                    return Result.fail("The upgrade changed while you were buying it. Try again.");
                }
                Result charge = eco.bankCharge(f, cost, label);
                if (charge.failed()) {
                    return Result.fail("The faction bank needs " + eco.format(cost) + " for this upgrade.");
                }
                f.setUpgrade(k, level + 1);
            }
            paid = eco.format(cost);
        } else {
            int pointsCost = pointsCost(cost);
            synchronized (upgradeLock) {
                // Points change under the faction monitor elsewhere, so the check and the spend hold it too.
                synchronized (f) {
                    if (f.upgrade(k) != level) {
                        return Result.fail("The upgrade changed while you were buying it. Try again.");
                    }
                    if (f.points() < pointsCost) {
                        return Result.fail("Your faction needs " + Msg.number(pointsCost) + " points for this upgrade and has "
                                + Msg.number(f.points()) + ".");
                    }
                    f.setPoints(f.points() - pointsCost);
                    f.setUpgrade(k, level + 1);
                }
            }
            paid = Msg.number(pointsCost) + " points";
        }
        String who = nameOf(actor);
        factions.audit(f, actor, who, "upgrade", who + " bought the " + label + " for " + paid + ".");
        ctx.bus().publish(new DominionEvent.UpgradeBought(f.id(), k, level + 1));
        invalidate();
        return Result.pass("Your faction bought the " + label + " for " + paid + ".");
    }

    // ---- missions ------------------------------------------------------------

    @Override
    public List<Mission> activeMissions(boolean weekly) {
        int count = ctx.settings().integer(weekly ? "missions.weekly" : "missions.daily");
        double coins = ctx.settings().decimal("missions.rewardCoins");
        int points = ctx.settings().integer("missions.rewardPoints");
        List<Mission> out = new ArrayList<>();
        for (MissionPool.Template t : MissionPool.pick(weekly, MissionPool.period(weekly), count)) {
            out.add(t.toMission(coins, points));
        }
        return out;
    }

    /** Active mission by id across both periods, or null. */
    public Mission activeMission(String id) {
        for (boolean weekly : new boolean[] { false, true }) {
            for (Mission m : activeMissions(weekly)) {
                if (m.id().equals(id)) {
                    return m;
                }
            }
        }
        return null;
    }

    public static String statKey(Mission mission) {
        return MISSION_PREFIX + mission.id() + "." + MissionPool.period(mission.weekly());
    }

    @Override
    public long missionProgress(Faction faction, Mission mission) {
        return faction == null || mission == null ? 0 : Math.min(mission.target(), faction.stat(statKey(mission)));
    }

    @Override
    public boolean missionDone(Faction faction, Mission mission) {
        return faction != null && mission != null && faction.stat(statKey(mission)) >= mission.target();
    }

    @Override
    public void advance(UUID player, String kind, long amount) {
        if (player == null || kind == null || amount <= 0) {
            return;
        }
        FactionService factions = ctx.serviceOrNull(FactionService.class);
        Faction f = factions == null ? null : factions.factionOf(player);
        if (f == null || f.isSystem()) {
            return;
        }
        List<Mission> missions = new ArrayList<>(activeMissions(false));
        missions.addAll(activeMissions(true));
        for (Mission m : missions) {
            if (!m.kind().equals(kind)) {
                continue;
            }
            String key = statKey(m);
            boolean completed;
            synchronized (f) {
                long before = f.stat(key);
                long credit = credit(before, amount, m.target());
                if (credit <= 0) {
                    continue;
                }
                long after = f.addStat(key, credit);
                completed = after >= m.target();
            }
            if (completed) {
                complete(f, m, player);
            }
        }
    }

    /** How much of amount still counts toward a mission at before of target. Never more than the gap. */
    static long credit(long before, long amount, long target) {
        if (amount <= 0 || before >= target) {
            return 0;
        }
        return Math.min(amount, target - before);
    }

    private void complete(Faction f, Mission m, UUID player) {
        String reward = "";
        double boost = missionBoost(f);
        double coins = m.rewardCoins() * boost;
        int points = (int) Math.round(m.rewardPoints() * boost);
        if (coins > 0) {
            EconomyService eco = ctx.serviceOrNull(EconomyService.class);
            if (eco != null && eco.enabled()) {
                if (eco.wholeUnits()) {
                    coins = Math.floor(coins);
                }
                if (coins > 0 && eco.bankCredit(f, coins, "Mission " + m.name()).success()) {
                    reward = eco.format(coins) + " for the bank";
                }
            }
        }
        if (points > 0) {
            synchronized (f) {
                f.setPoints(f.points() + points);
            }
            reward = reward.isEmpty() ? Msg.number(points) + " points"
                    : reward + " and " + Msg.number(points) + " points";
        }
        String text = "Your faction completed the " + (m.weekly() ? "weekly" : "daily") + " mission " + m.name()
                + (reward.isEmpty() ? "." : " and earned " + reward + ".");
        FactionService factions = ctx.serviceOrNull(FactionService.class);
        if (factions != null) {
            factions.audit(f, player, nameOf(player), "mission", "The faction completed the mission " + m.name()
                    + (reward.isEmpty() ? "." : " and earned " + reward + "."));
        }
        ctx.bus().publish(new DominionEvent.MissionCompleted(f.id(), m.id()));
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat != null) {
            chat.alert(f, Msg.good(text));
        }
        invalidate();
    }

    /** Mission reward multiplier from outposts the faction holds, 1 when the events module is absent. */
    private double missionBoost(Faction f) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return 1.0;
        }
        try {
            double b = events.boostFor(f, "missions");
            return Double.isFinite(b) && b > 0 ? b : 1.0;
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    // ---- level and points ------------------------------------------------------

    @Override
    public int level(Faction faction) {
        return faction == null ? 0 : 1 + Math.max(0, faction.points()) / 100;
    }

    @Override
    public int points(Faction faction) {
        return faction == null ? 0 : faction.points();
    }

    // ---- strikes -----------------------------------------------------------------

    @Override
    public List<dev.ryvrook.dominion.model.Strike> strikes(Faction faction) {
        if (faction == null) {
            return List.of();
        }
        List<Strikes.Strike> raw = strikes.of(faction.id());
        List<dev.ryvrook.dominion.model.Strike> out = new java.util.ArrayList<>(raw.size());
        int n = 1;
        for (Strikes.Strike s : raw) {
            out.add(new dev.ryvrook.dominion.model.Strike(n++, s.points(), s.reason(), s.by(), s.byName(), s.at()));
        }
        return out;
    }

    /** Raw strike records kept for the admin listing command. */
    public List<Strikes.Strike> rawStrikes(Faction faction) {
        return faction == null ? List.of() : strikes.of(faction.id());
    }

    @Override
    public Result strike(UUID admin, String adminName, Faction faction, int points, String reason) {
        if (faction == null) {
            return Result.fail("That faction does not exist.");
        }
        if (points <= 0) {
            return Result.fail("A strike must be worth at least one point.");
        }
        if (reason == null || reason.isBlank()) {
            return Result.fail("Give a reason for the strike.");
        }
        String who = adminName == null || adminName.isBlank() ? "An admin" : adminName;
        String why = reason.trim();
        int number = strikes.add(faction.id(), new Strikes.Strike(points, why, admin, who, Time.now()));
        audit(faction, admin, who, who + " gave the faction a strike worth " + points + (points == 1 ? " point" : " points")
                + " because " + why);
        invalidate();
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        return Result.pass(faction.tag() + " received strike number " + number + " worth " + points
                + (points == 1 ? " point" : " points") + ". Its value is now " + Msg.number(value(faction)) + ".");
    }

    /** Index is one based, matching the numbered list shown to admins. */
    @Override
    public Result unstrike(UUID admin, Faction faction, int index) {
        if (faction == null) {
            return Result.fail("That faction does not exist.");
        }
        Strikes.Strike removed = strikes.remove(faction.id(), index - 1);
        if (removed == null) {
            return Result.fail(faction.tag() + " has no strike number " + index + ".");
        }
        String who = admin == null ? "An admin" : nameOf(admin);
        audit(faction, admin, who, who + " removed the strike given for " + removed.reason());
        invalidate();
        ctx.bus().publish(new DominionEvent.FactionChanged(faction.id()));
        return Result.pass("Removed strike number " + index + " from " + faction.tag() + ". Its value is now "
                + Msg.number(value(faction)) + ".");
    }

    private void audit(Faction f, UUID who, String whoName, String text) {
        FactionService factions = ctx.serviceOrNull(FactionService.class);
        String line = text.endsWith(".") ? text : text + ".";
        if (factions != null) {
            factions.audit(f, who, whoName, "strike", line);
        }
    }

    /** Called by the module when a faction disbands so stale strikes do not linger. */
    void forget(UUID factionId) {
        strikes.forget(factionId);
    }

    // ---- rotation ----------------------------------------------------------------

    @Override
    public synchronized void tick() {
        long day = MissionPool.today();
        long week = MissionPool.thisWeek();
        if (day == lastDay && week == lastWeek) {
            return;
        }
        lastDay = day;
        lastWeek = week;
        for (Faction f : ctx.repo().factions()) {
            for (String key : f.stats().keySet()) {
                if (staleMissionKey(key, day, week)) {
                    f.removeStat(key);
                }
            }
        }
    }

    /**
     * True for a mission counter that belongs to an earlier day or week, or to a mission that no
     * longer exists. Keys look like mission.id.period. Other stats are never stale.
     */
    static boolean staleMissionKey(String key, long day, long week) {
        if (key == null || !key.startsWith(MISSION_PREFIX)) {
            return false;
        }
        int dot = key.lastIndexOf('.');
        String id = dot > MISSION_PREFIX.length() ? key.substring(MISSION_PREFIX.length(), dot) : "";
        MissionPool.Template t = MissionPool.byId(id);
        if (t == null) {
            return true;
        }
        long current = t.weekly() ? week : day;
        return !key.substring(dot + 1).equals(Long.toString(current));
    }

    private String nameOf(UUID player) {
        if (player == null) {
            return "Console";
        }
        String n = ctx.player(player).name();
        return n == null || n.isBlank() ? "Someone" : n;
    }
}
