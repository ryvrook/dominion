package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.chat.ChatServiceImpl;
import dev.ryvrook.dominion.model.AuditEntry;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The /f panel. Tabs on the left, the selected tab on the right, or a join and create view. */
public final class DominionPanel extends DominionPage {
    private static final String PAGE = "Pages/Dominion_Panel.ui";
    private static final int LOG_PAGE_SIZE = 25;
    private static final int GRID_RADIUS = 7;

    static final String OVERVIEW = "overview";
    static final String MEMBERS = "members";
    static final String LAND = "land";
    static final String RELATIONS = "relations";
    static final String BANK = "bank";
    static final String SETTINGS = "settings";
    static final String LOG = "log";
    static final String HELP = "help";
    static final String START = "start";

    /**
     * Faction toggles shown on the settings tab. The second column is the server setting that
     * supplies the default when the faction never changed the toggle, or a literal true or false.
     * These must match the defaults the rules and combat code read.
     */
    private static final String[][] TOGGLES = {
            {"open", "false", "Anyone can join without an invite"},
            {"friendlyFire", "combat.friendlyFireDefault", "Members can hurt each other"},
            {"allyBuild", "relation.allyBuildDefault", "Allies can build in your land"},
            {"truceInteract", "relation.truceInteractDefault", "Truces can use doors in your land"},
            {"allyHome", "false", "Allies can use your home"},
            {"homeOnDeath", "home.respawnAtHomeDefault", "Respawn at the faction home"},
            {"loginAlerts", "chat.loginAlerts", "Announce member logins"}
    };

    private String tab;
    private int logPage;
    private String logFilter = "all";
    private final SiegeTab sieges;
    private final MarketTab market;
    private final ProgressTabs progress;
    private final EventsTab eventsTab;
    private final NationSection nation;
    private final ZonesSection zones;

    /** A label refreshed every second while the page is open, such as an event countdown. */
    record Live(String selector, java.util.function.Supplier<String> text) {}

    private volatile List<Live> live = List.of();
    private List<Live> pendingLive = new ArrayList<>();

    public DominionPanel(PlayerRef player, DominionContext ctx, UiServiceImpl ui, String tab) {
        super(player, ctx, ui);
        this.tab = tab == null || tab.isBlank() ? OVERVIEW : tab.toLowerCase(Locale.ROOT);
        this.sieges = new SiegeTab(ctx, this);
        this.market = new MarketTab(ctx, this);
        this.progress = new ProgressTabs(ctx, this);
        this.eventsTab = new EventsTab(ctx, this);
        this.nation = new NationSection(ctx, this);
        this.zones = new ZonesSection(ctx, this);
    }

    private EconomyService economy() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && eco.enabled() ? eco : null;
    }

    // ---- build -----------------------------------------------------------

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        pendingLive = new ArrayList<>();
        try {
            renderPanel(commands, events);
        } finally {
            live = List.copyOf(pendingLive);
        }
    }

    private void renderPanel(UICommandBuilder commands, UIEventBuilder events) {
        commands.append(PAGE);
        Faction f = factions().factionOf(uuid());
        if (f == null) {
            buildNoFaction(commands, events);
            return;
        }
        PowerService power = ctx.service(PowerService.class);
        Rank rank = f.rankOf(uuid());
        commands.set("#HeaderTag.Text", f.tag());
        commands.set("#HeaderTag.Style.TextColor", safeColor(f.color(), "#7fd6ff"));
        commands.set("#HeaderName.Text", f.name());
        commands.set("#HeaderPower.Text", Msg.number(power.factionPower(f)) + " / " + Msg.number(power.factionMaxPower(f)));
        commands.set("#HeaderRank.Text", f.rankName(rank == null ? Rank.MEMBER : rank));

        EconomyService eco = economy();
        if (BANK.equals(tab) && eco == null) {
            tab = OVERVIEW;
        }
        if (START.equals(tab)) {
            tab = OVERVIEW;
        }
        // Optional tabs such as sieges, market, vault, upgrades, missions and events exist only while their service is installed.
        boolean hasSieges = sieges.available();
        boolean hasMarket = market.available();
        boolean hasVault = progress.vault() != null;
        boolean hasProgress = progress.progress() != null;
        boolean hasEvents = eventsTab.available();
        if ((SiegeTab.ID.equals(tab) && !hasSieges) || (MarketTab.ID.equals(tab) && !hasMarket)
                || (ProgressTabs.VAULT.equals(tab) && !hasVault)
                || ((ProgressTabs.UPGRADES.equals(tab) || ProgressTabs.MISSIONS.equals(tab)) && !hasProgress)
                || (EventsTab.ID.equals(tab) && !hasEvents)) {
            tab = OVERVIEW;
        }

        Body tabs = new Body(commands, events, "#Tabs");
        int online = onlineCount(f);
        tabs.tab(OVERVIEW, "Overview", "", OVERVIEW.equals(tab), "tab");
        tabs.tab(MEMBERS, "Members", online + "/" + f.memberCount(), MEMBERS.equals(tab), "tab");
        tabs.tab(LAND, "Land", String.valueOf(ctx.service(ClaimService.class).claimCount(f)), LAND.equals(tab), "tab");
        int pending = incomingRelationRequests(f).size();
        tabs.tab(RELATIONS, "Relations", pending > 0 ? pending + " new" : "", RELATIONS.equals(tab), "tab");
        if (eco != null) {
            tabs.tab(BANK, "Bank", "", BANK.equals(tab), "tab");
        }
        if (hasSieges) {
            tabs.tab(SiegeTab.ID, sieges.label(), sieges.count(f), SiegeTab.ID.equals(tab), "tab");
        }
        if (hasMarket) {
            tabs.tab(MarketTab.ID, "Market", market.count(), MarketTab.ID.equals(tab), "tab");
        }
        if (hasVault) {
            tabs.tab(ProgressTabs.VAULT, "Vault", "", ProgressTabs.VAULT.equals(tab), "tab");
        }
        if (hasProgress) {
            tabs.tab(ProgressTabs.UPGRADES, "Upgrades", "", ProgressTabs.UPGRADES.equals(tab), "tab");
            tabs.tab(ProgressTabs.MISSIONS, "Missions", "", ProgressTabs.MISSIONS.equals(tab), "tab");
        }
        if (hasEvents) {
            tabs.tab(EventsTab.ID, "Events", eventsTab.count(), EventsTab.ID.equals(tab), "tab");
        }
        tabs.tab(SETTINGS, "Settings", "", SETTINGS.equals(tab), "tab");
        tabs.tab(LOG, "Log", "", LOG.equals(tab), "tab");
        tabs.tab(HELP, "Help", "", HELP.equals(tab), "tab");

        Body body = new Body(commands, events, "#Body");
        switch (tab) {
            case SiegeTab.ID -> sieges.build(commands, body, f);
            case MarketTab.ID -> market.build(commands, body, f);
            case ProgressTabs.VAULT -> progress.buildVault(commands, body, f);
            case ProgressTabs.UPGRADES -> progress.buildUpgrades(commands, body, f);
            case ProgressTabs.MISSIONS -> progress.buildMissions(commands, body, f);
            case EventsTab.ID -> eventsTab.build(commands, body, f);
            case MEMBERS -> buildMembers(commands, body, f);
            case LAND -> buildLand(commands, body, f);
            case RELATIONS -> buildRelations(commands, body, f);
            case BANK -> buildBank(commands, body, f, eco);
            case SETTINGS -> buildSettings(commands, body, f);
            case LOG -> buildLog(commands, body, f);
            case HELP -> buildHelp(commands, body);
            default -> {
                tab = OVERVIEW;
                buildOverview(commands, body, f, eco);
            }
        }
    }

    void title(UICommandBuilder c, String title, String hint) {
        c.set("#TabTitle.Text", title);
        c.set("#TabHint.Text", hint == null ? "" : hint);
    }

    private void buildNoFaction(UICommandBuilder c, UIEventBuilder events) {
        PowerService power = ctx.service(PowerService.class);
        c.set("#HeaderTag.Text", "Dominion");
        c.set("#HeaderTag.Style.TextColor", Msg.GOLD);
        c.set("#HeaderName.Text", "You are not in a faction yet");
        c.set("#HeaderPowerLabel.Text", "Your power");
        c.set("#HeaderPower.Text", Msg.number(power.powerOf(uuid())) + " / " + Msg.number(power.maxPowerOf(uuid())));
        c.set("#HeaderRank.Text", "None");

        if (!HELP.equals(tab)) {
            tab = START;
        }
        Body tabs = new Body(c, events, "#Tabs");
        tabs.tab(START, "Join or create", "", START.equals(tab), "tab");
        tabs.tab(HELP, "Help", "", HELP.equals(tab), "tab");

        Body body = new Body(c, events, "#Body");
        if (HELP.equals(tab)) {
            buildHelp(c, body);
            return;
        }
        title(c, "Join or create", "Factions share land, power and a bank");
        body.text("A faction is a group of players who claim land together. Claimed land is protected from outsiders, "
                + "and every member adds power that keeps the land safe. Type a short tag below to found your own faction, "
                + "or join one of the factions listed underneath.");
        body.field("New faction tag", "", Btn.of("Create", "create"));
        body.buttons(Btn.of("Tutorial", "tutorial"), Btn.of("Top factions", "top"));

        body.section("Factions you can join");
        long now = Time.now();
        int shown = 0;
        List<Faction> all = new ArrayList<>(factions().all());
        all.sort(Comparator.comparingInt(Faction::memberCount).reversed());
        for (Faction f : all) {
            if (f.isSystem()) {
                continue;
            }
            boolean open = f.setting("open", false);
            boolean invited = f.hasValidInvite(uuid(), now);
            String state = invited ? "Invited" : open ? "Open" : "Invite only";
            Btn join = open || invited ? Btn.of("Join", "join", f.tag()) : Btn.of("Request", "request", f.tag());
            body.row(f.tag() + "  " + f.name(), safeColor(f.color(), null),
                    new String[] {f.memberCount() + " members", state, "", ""},
                    join, Btn.of("Info", "info", f.tag()));
            if (++shown >= 40) {
                break;
            }
        }
        if (shown == 0) {
            body.text("No factions exist yet. Be the first to found one.", Msg.MUTED);
        }
    }

    private void buildOverview(UICommandBuilder c, Body body, Faction f, EconomyService eco) {
        ClaimService claims = ctx.service(ClaimService.class);
        title(c, "Overview", "Created " + Time.date(f.createdAt()));
        Member leader = f.leader() == null ? null : f.member(f.leader());
        body.stat(f.rankName(Rank.LEADER), leader == null ? "Nobody" : leader.name());
        body.stat("Members", onlineCount(f) + " online of " + f.memberCount());
        body.stat("Land", claims.claimCount(f) + " of " + claims.maxClaims(f) + " chunks");
        body.stat("Allies", names(f, Relation.ALLY), Relation.ALLY.color());
        body.stat("Enemies", names(f, Relation.ENEMY), Relation.ENEMY.color());
        if (eco != null) {
            body.stat("Bank", eco.format(f.bank()));
        }
        progress.overviewStats(body, f);

        body.section("Announcement");
        if (f.motd().isBlank()) {
            body.text("No announcement yet.", Msg.MUTED);
        } else {
            body.text(f.motd());
            AuditEntry posted = latestAnnouncement(f);
            if (posted != null) {
                body.text("Posted " + Time.ago(posted.at()) + (posted.whoName() == null || posted.whoName().isBlank()
                        ? "." : " by " + posted.whoName() + "."), Msg.MUTED);
            }
        }
        if (factions().can(uuid(), FactionAction.ANNOUNCE)) {
            body.field("New announcement", "", Btn.of("Post", "announce"));
        }
        body.section("Description");
        body.text(f.description().isBlank() ? "No description yet. Officers can write one on the settings tab." : f.description());

        PlayerData me = ctx.player(uuid());
        body.section("Quick actions");
        body.buttons(Btn.of("Home", "home"), Btn.of("Map", "tab", LAND),
                Btn.of("Chat " + channelLabel(me), "channel"),
                Btn.of(me.alerts() ? "Alerts on" : "Alerts off", "alerts"));
        body.buttons(Btn.of(me.hud() ? "Sidebar on" : "Sidebar off", "hud"),
                Btn.of("Tutorial", "tutorial"), Btn.of("Top factions", "top"));
    }

    private void buildMembers(UICommandBuilder c, Body body, Faction f) {
        PowerService power = ctx.service(PowerService.class);
        title(c, "Members", onlineCount(f) + " online of " + f.memberCount());
        body.row("Name", Msg.MUTED, new String[] {"Rank", "Power", "Last seen", "Title"});
        List<Member> members = new ArrayList<>(f.members());
        UUID leaderId = f.leader();
        members.sort(Comparator.comparing((Member m) -> m.uuid().equals(leaderId) ? Rank.LEADER : m.rank()).reversed()
                .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        for (Member m : members) {
            boolean online = Players.isOnline(m.uuid());
            Rank rank = m.uuid().equals(leaderId) ? Rank.LEADER : m.rank();
            String[] cols = {f.rankName(rank), Msg.number(power.powerOf(m.uuid())),
                    online ? "Online" : Time.ago(m.lastSeen()), m.title()};
            String row;
            if (m.uuid().equals(uuid())) {
                row = body.row(m.name() + "  (you)", rank == Rank.LEADER ? Msg.GOLD : null, cols);
            } else {
                String id = m.uuid().toString();
                row = body.row(m.name(), rank == Rank.LEADER ? Msg.GOLD : null, cols,
                        Btn.of("Promote", "promote", id), Btn.of("Demote", "demote", id), Btn.of("Kick", "kick", id));
            }
            body.dot(row, online);
        }

        body.section("Invite");
        body.field("Player name", "", Btn.of("Invite", "invite"));

        body.section("Join requests");
        List<UUID> requests = f.requests();
        if (requests.isEmpty()) {
            body.text("No pending requests.", Msg.MUTED);
        }
        for (UUID r : requests) {
            String id = r.toString();
            body.row(nameOf(r), null, new String[] {"Wants to join", "", "", ""},
                    Btn.of("Accept", "req_accept", id), Btn.of("Decline", "req_decline", id));
        }
    }

    private void buildLand(UICommandBuilder c, Body body, Faction f) {
        ClaimService claims = ctx.service(ClaimService.class);
        PlayerData me = ctx.player(uuid());
        String world = Players.worldName(player());
        long chunk = Players.chunkOf(player());
        title(c, "Land", claims.claimCount(f) + " of " + claims.maxClaims(f) + " chunks claimed");

        if (world != null && chunk != Long.MIN_VALUE) {
            Faction owner = claims.ownerAt(world, chunk);
            ClaimEntry entry = claims.claimAt(world, chunk);
            body.stat("You are standing in", "Chunk " + Keys.describe(chunk) + " of " + world);
            if (owner == null) {
                body.stat("Owner", "Wilderness", "#5fd37a");
            } else {
                Relation rel = ctx.service(RelationService.class).playerToFaction(uuid(), owner);
                body.stat("Owner", owner.tag() + "  " + owner.name(), rel == null ? Msg.TEXT : rel.color());
                if (entry != null) {
                    body.stat("Claimed", Time.ago(entry.claimedAt()) + (entry.claimedBy() == null ? "" : " by " + nameOf(entry.claimedBy())));
                }
            }
        }
        body.buttons(Btn.of("Claim", "claim"), Btn.of("Unclaim", "unclaim"),
                Btn.of(me.autoClaim() ? "Autoclaim on" : "Autoclaim off", "autoclaim"),
                Btn.of("Set home", "sethome"), Btn.of("Refresh", "refresh"));

        body.section("Nearby chunks");
        int size = GRID_RADIUS * 2 + 1;
        List<String[]> cells;
        try {
            cells = claims.gridMap(uuid(), GRID_RADIUS);
        } catch (Exception e) {
            cells = List.of();
        }
        if (cells == null || cells.size() < size * size) {
            body.text("The map is not available here.", Msg.MUTED);
        } else {
            for (int r = 0; r < size; r++) {
                String[] glyphs = new String[size];
                String[] colors = new String[size];
                for (int col = 0; col < size; col++) {
                    String[] cell = cells.get(r * size + col);
                    String color = cell != null && cell.length > 0 ? cell[0] : null;
                    String tag = cell != null && cell.length > 1 && cell[1] != null ? cell[1] : "";
                    boolean centre = r == GRID_RADIUS && col == GRID_RADIUS;
                    glyphs[col] = centre ? "@" : tag.isBlank() ? "-" : tag.substring(0, 1).toUpperCase(Locale.ROOT);
                    colors[col] = centre && tag.isBlank() ? "#ffffff" : safeColor(color, "#4a5260");
                }
                body.gridRow(glyphs, colors);
            }
            body.text("You are the @ in the middle. North is up. Letters show the first letter of the owning faction "
                    + "in its relation colour. Green is yours, purple allies, blue truces, red enemies.", Msg.MUTED);
        }
    }

    private void buildRelations(UICommandBuilder c, Body body, Faction f) {
        title(c, "Relations", f.countRelation(Relation.ALLY) + " allies and " + f.countRelation(Relation.ENEMY) + " enemies");
        Map<Relation, List<Faction>> grouped = new LinkedHashMap<>();
        grouped.put(Relation.ALLY, new ArrayList<>());
        grouped.put(Relation.TRUCE, new ArrayList<>());
        grouped.put(Relation.ENEMY, new ArrayList<>());
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            Faction other = factions().byId(e.getKey());
            List<Faction> list = grouped.get(e.getValue());
            if (other != null && list != null) {
                list.add(other);
            }
        }
        String[] headings = {"Allies", "Truces", "Enemies"};
        int i = 0;
        for (Map.Entry<Relation, List<Faction>> g : grouped.entrySet()) {
            body.section(headings[i++]);
            if (g.getValue().isEmpty()) {
                body.text("None.", Msg.MUTED);
            }
            for (Faction o : g.getValue()) {
                Btn enemy = g.getKey() == Relation.ENEMY ? null : Btn.of("Enemy", "rel_enemy", o.tag());
                body.row(o.tag() + "  " + o.name(), g.getKey().color(),
                        new String[] {o.memberCount() + " members", "", "", ""},
                        Btn.of("Neutral", "rel_neutral", o.tag()), enemy, Btn.of("Info", "info", o.tag()));
            }
        }

        List<Faction> allyHomes = usableAllyHomes(f);
        if (!allyHomes.isEmpty()) {
            body.section("Ally homes");
            for (Faction o : allyHomes) {
                body.row(o.tag() + "  " + o.name(), Relation.ALLY.color(),
                        new String[] {"Home open", "", "", ""}, Btn.of("Visit", "ally_home", o.tag()));
            }
        }

        body.section("Requests");
        List<Map.Entry<Faction, Faction.RelationRequest>> incoming = incomingRelationRequests(f);
        List<Map.Entry<Faction, Faction.RelationRequest>> outgoing = outgoingRelationRequests(f);
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            body.text("No pending requests.", Msg.MUTED);
        }
        for (Map.Entry<Faction, Faction.RelationRequest> e : incoming) {
            Faction o = e.getKey();
            body.row(o.tag() + "  " + o.name(), e.getValue().type().color(),
                    new String[] {"Offers " + e.getValue().type().displayName().toLowerCase(Locale.ROOT),
                            "Expires in " + Time.remaining(e.getValue().expiresAt() - Time.now()), "", ""},
                    Btn.of("Accept", "rel_accept", o.tag()), Btn.of("Decline", "rel_decline", o.tag()));
        }
        for (Map.Entry<Faction, Faction.RelationRequest> e : outgoing) {
            Faction o = e.getKey();
            body.row(o.tag() + "  " + o.name(), e.getValue().type().color(),
                    new String[] {"You offered " + e.getValue().type().displayName().toLowerCase(Locale.ROOT),
                            "Expires in " + Time.remaining(e.getValue().expiresAt() - Time.now()), "", ""},
                    Btn.of("Withdraw", "rel_neutral", o.tag()));
        }

        body.section("New relation");
        body.field("Faction tag", "", Btn.of("Ally", "rel_ally"), Btn.of("Truce", "rel_truce"),
                Btn.of("Enemy", "rel_enemy_tag"));

        nation.build(body, f);
    }

    private void buildBank(UICommandBuilder c, Body body, Faction f, EconomyService eco) {
        title(c, "Bank", "Shared funds for upkeep and upgrades");
        body.stat("Faction balance", eco.format(f.bank()), Msg.GOLD);
        body.stat("Your wallet", eco.format(eco.balance(uuid())));
        body.field("Amount", "", Btn.of("Deposit", "deposit"), Btn.of("Withdraw", "withdraw"));
        body.section("Recent bank activity");
        int shown = 0;
        for (AuditEntry e : f.audit()) {
            if (e.category() == null || !e.category().toLowerCase(Locale.ROOT).contains("bank")) {
                continue;
            }
            body.row(e.text(), null, new String[] {"", e.whoName() == null ? "" : e.whoName(), Time.ago(e.at()), ""});
            if (++shown >= 20) {
                break;
            }
        }
        if (shown == 0) {
            body.text("No bank activity yet.", Msg.MUTED);
        }
    }

    private void buildSettings(UICommandBuilder c, Body body, Faction f) {
        title(c, "Settings", "Changes need the settings permission");
        body.section("Rules");
        for (String[] t : TOGGLES) {
            boolean on = f.setting(t[0], toggleDefault(t[1]));
            body.toggle(t[2], t[0], on, "toggle", t[0]);
        }
        body.section("Identity");
        body.palette(safeColor(f.color(), null), "color");
        body.field("Name", f.name(), Btn.of("Save", "rename"));
        body.field("Description", f.description(), Btn.of("Save", "describe"));
        body.field("Message of the day", f.motd(), Btn.of("Save", "motd"));
        body.buttons(Btn.of("Permissions", "perms"));
        zones.buildRankNames(body, f);
        zones.buildZones(body, f);

        body.section("Danger zone");
        body.text("These cannot be undone. Each one asks you to confirm first.", Msg.WARN);
        body.field("New leader", "", Btn.of("Transfer", "transfer"));
        body.buttons(Btn.of("Unclaim all", "unclaimall"), Btn.of("Disband", "disband"), Btn.of("Leave", "leave"));
    }

    private void buildLog(UICommandBuilder c, Body body, Faction f) {
        List<AuditEntry> all = f.audit();
        Set<String> categories = new LinkedHashSet<>();
        for (AuditEntry e : all) {
            if (e.category() != null && !e.category().isBlank()) {
                categories.add(e.category().toLowerCase(Locale.ROOT));
            }
        }
        List<AuditEntry> filtered = new ArrayList<>();
        for (AuditEntry e : all) {
            if ("all".equals(logFilter) || (e.category() != null && e.category().equalsIgnoreCase(logFilter))) {
                filtered.add(e);
            }
        }
        int pages = Math.max(1, (filtered.size() + LOG_PAGE_SIZE - 1) / LOG_PAGE_SIZE);
        logPage = Math.max(0, Math.min(logPage, pages - 1));
        title(c, "Log", filtered.size() + " entries, page " + (logPage + 1) + " of " + pages);

        List<Btn> pills = new ArrayList<>();
        pills.add(Btn.of("all".equals(logFilter) ? "[ All ]" : "All", "log_filter", "all"));
        for (String cat : categories) {
            if (pills.size() >= 6) {
                break;
            }
            pills.add(Btn.of(cat.equals(logFilter) ? "[ " + cat + " ]" : cat, "log_filter", cat));
        }
        body.buttons(pills.toArray(new Btn[0]));

        int from = logPage * LOG_PAGE_SIZE;
        int to = Math.min(filtered.size(), from + LOG_PAGE_SIZE);
        for (int i = from; i < to; i++) {
            AuditEntry e = filtered.get(i);
            body.row(e.text(), null, new String[] {e.category() == null ? "" : e.category(),
                    e.whoName() == null ? "" : e.whoName(), Time.ago(e.at()), ""});
        }
        if (filtered.isEmpty()) {
            body.text("Nothing logged yet.", Msg.MUTED);
        }
        body.buttons(logPage > 0 ? Btn.of("Previous", "log_prev") : null,
                logPage < pages - 1 ? Btn.of("Next", "log_next") : null);
    }

    private void buildHelp(UICommandBuilder c, Body body) {
        title(c, "Help", "How factions work");
        body.section("Power");
        body.text("Every player has power that grows while online and drops when they die. A faction's power is the sum "
                + "of its members. Power decides how much land the faction can hold.");
        body.section("Claims");
        body.text("Stand in a chunk and press Claim on the land tab, or use /f claim. Claimed chunks are protected from "
                + "outsiders. You can hold more chunks as your power grows. Turn on auto claim to claim as you walk.");
        body.section("Relations");
        body.text("Factions can be allies, have a truce, stay neutral or be enemies. Ally and truce need both sides to "
                + "agree. Enemy and neutral take effect at once. Relations decide who can build, fight and use your home.");
        body.section("Raiding");
        body.text("When a faction holds more land than its power can defend it becomes raidable, and enemies can take "
                + "its chunks. Keep members active and avoid needless deaths to stay safe. Watch the sidebar for warnings.");
        if (sieges.available()) {
            body.section("Sieges and shields");
            body.text("A siege plants a banner on enemy land. If the attackers hold it until the timer ends, the chunk is "
                    + "breached and opens to them for a while. Each faction picks a daily shield window that blocks sieges "
                    + "on its core land.");
        }
        if (market.available()) {
            body.section("Market");
            body.text("Sell the item in your hand, place buy orders, bid in auctions and collect your earnings from the "
                    + "mailbox on the market tab.");
        }
        if (nation.nations() != null) {
            body.section("Nations");
            body.text("Allied factions can join a nation under one tag. Nation members share a chat channel. "
                    + "Found or join a nation on the relations tab.");
        }
        if (eventsTab.available()) {
            body.section("Events");
            body.text("Hold a hill to capture it for your faction, keep outposts for their boosts, loot envoy chests "
                    + "when they drop and claim bounties by defeating marked players. The events tab shows what is running.");
        }
        body.section("Commands");
        body.text("Open this panel with /f. Use /f help for the full command list.", Msg.MUTED);
    }

    // ---- actions ---------------------------------------------------------

    @Override
    protected boolean onAction(PageEvent e) {
        String v = e.value();
        String text = e.text();
        Faction f = factions().factionOf(uuid());
        Boolean handled = sieges.handle(e);
        if (handled == null) {
            handled = market.handle(e);
        }
        if (handled == null) {
            handled = progress.handle(e);
        }
        if (handled == null) {
            handled = eventsTab.handle(e);
        }
        if (handled == null) {
            handled = nation.handle(e);
        }
        if (handled == null) {
            handled = zones.handle(e);
        }
        if (handled != null) {
            return handled;
        }
        switch (e.action) {
            case "tab" -> {
                tab = v;
                logPage = 0;
                return true;
            }
            case "refresh" -> {
                return true;
            }
            case "tutorial" -> {
                ui.openTutorial(player());
                return false;
            }
            case "top" -> {
                ui.openTop(player());
                return false;
            }
            case "info" -> {
                ui.openFactionInfo(player(), v);
                return false;
            }
            case "create" -> {
                if (text.isEmpty()) {
                    Msg.send(player(), Msg.bad("Type a tag for your faction first."));
                    return false;
                }
                Result r = factions().create(uuid(), username(), text, text);
                reply(r);
                if (r.success()) {
                    tab = OVERVIEW;
                }
                return true;
            }
            case "join" -> {
                Result r = factions().join(uuid(), username(), v);
                reply(r);
                if (r.success()) {
                    tab = OVERVIEW;
                }
                return true;
            }
            case "request" -> reply(factions().requestJoin(uuid(), username(), v));
            case "home" -> {
                Result r = ctx.service(HomeService.class).home(uuid());
                reply(r);
                if (r.success()) {
                    closePage();
                    return false;
                }
            }
            case "channel" -> {
                ChatService chat = ctx.service(ChatService.class);
                PlayerData.Channel ch = chat.cycleChannel(uuid());
                String name = chat instanceof ChatServiceImpl impl ? impl.currentChannelName(uuid())
                        : ch.name().toLowerCase(Locale.ROOT) + " chat";
                Msg.send(player(), Msg.good("You now talk in " + name + "."));
            }
            case "alerts" -> {
                PlayerData me = ctx.player(uuid());
                me.setAlerts(!me.alerts());
                Msg.send(player(), Msg.good(me.alerts() ? "Faction alerts are on." : "Faction alerts are off."));
            }
            case "hud" -> {
                boolean next = !ctx.player(uuid()).hud();
                ui.setHudVisible(player(), next);
                Msg.send(player(), Msg.good(next ? "The faction sidebar is on." : "The faction sidebar is off."));
            }
            case "promote" -> reply(withTarget(v, t -> factions().promote(uuid(), t)));
            case "demote" -> reply(withTarget(v, t -> factions().demote(uuid(), t)));
            case "kick" -> reply(withTarget(v, t -> factions().kick(uuid(), t)));
            case "req_accept" -> reply(withTarget(v, t -> factions().acceptRequest(uuid(), t)));
            case "req_decline" -> reply(withTarget(v, t -> factions().declineRequest(uuid(), t)));
            case "invite" -> {
                UUID target = resolvePlayer(text);
                if (target == null) {
                    reply(Result.fail(text.isEmpty() ? "Type a player name first." : "No player named " + text + " is known."));
                } else {
                    reply(factions().invite(uuid(), target, nameOr(target, text)));
                }
            }
            case "claim", "unclaim" -> {
                String world = Players.worldName(player());
                long chunk = Players.chunkOf(player());
                if (world == null || chunk == Long.MIN_VALUE) {
                    reply(Result.fail("Your position is not known yet. Try again in a moment."));
                } else {
                    ClaimService claims = ctx.service(ClaimService.class);
                    reply("claim".equals(e.action) ? claims.claim(uuid(), world, chunk) : claims.unclaim(uuid(), world, chunk));
                }
            }
            case "autoclaim" -> {
                PlayerData me = ctx.player(uuid());
                me.setAutoClaim(!me.autoClaim());
                Msg.send(player(), Msg.good(me.autoClaim() ? "Auto claim is on. Walk into wilderness to claim it."
                        : "Auto claim is off."));
            }
            case "sethome" -> reply(ctx.service(HomeService.class).setHome(uuid()));
            case "ally_home" -> {
                Result r = ctx.service(HomeService.class).allyHome(uuid(), v);
                reply(r);
                if (r.success()) {
                    closePage();
                    return false;
                }
            }
            case "announce" -> reply(text.isEmpty() ? Result.fail("Type the announcement first.")
                    : ctx.service(ChatService.class).announce(uuid(), text));
            case "rel_accept" -> reply(ctx.service(RelationService.class).accept(uuid(), v));
            case "rel_decline" -> reply(ctx.service(RelationService.class).decline(uuid(), v));
            case "rel_neutral" -> reply(ctx.service(RelationService.class).request(uuid(), v, Relation.NEUTRAL));
            case "rel_enemy" -> reply(ctx.service(RelationService.class).request(uuid(), v, Relation.ENEMY));
            case "rel_ally", "rel_truce", "rel_enemy_tag" -> {
                if (text.isEmpty()) {
                    reply(Result.fail("Type a faction tag first."));
                } else {
                    Relation rel = switch (e.action) {
                        case "rel_ally" -> Relation.ALLY;
                        case "rel_truce" -> Relation.TRUCE;
                        default -> Relation.ENEMY;
                    };
                    reply(ctx.service(RelationService.class).request(uuid(), text, rel));
                }
            }
            case "deposit", "withdraw" -> {
                EconomyService eco = economy();
                if (eco == null) {
                    reply(Result.fail("The economy is turned off on this server."));
                } else {
                    double amount = parseAmount(text);
                    if (amount <= 0) {
                        reply(Result.fail("Type a positive amount first."));
                    } else {
                        reply("deposit".equals(e.action) ? eco.bankDeposit(uuid(), amount) : eco.bankWithdraw(uuid(), amount));
                    }
                }
            }
            case "toggle" -> {
                if (f == null) {
                    return true;
                }
                boolean current = f.setting(v, defaultToggle(v));
                Result r = "open".equals(v) ? factions().setOpen(uuid(), !current) : factions().setSetting(uuid(), v, !current);
                reply(r);
            }
            case "color" -> reply(factions().setColor(uuid(), v));
            case "rename" -> {
                if (f == null) {
                    return true;
                }
                reply(text.isEmpty() ? Result.fail("Type a name first.") : factions().rename(uuid(), f.tag(), text));
            }
            case "describe" -> reply(factions().setDescription(uuid(), text));
            case "motd" -> reply(factions().setMotd(uuid(), text));
            case "perms" -> {
                ui.openPermissions(player());
                return false;
            }
            case "transfer" -> {
                UUID target = resolvePlayer(text);
                if (target == null) {
                    reply(Result.fail(text.isEmpty() ? "Type the new leader's name first." : "No player named " + text + " is known."));
                    return true;
                }
                String name = nameOr(target, text);
                ui.confirm(player(), "Transfer leadership",
                        "You will hand the faction to " + name + " and become an officer. Only the new leader can give it back.",
                        yes -> {
                            if (yes) {
                                reply(factions().transferLeader(uuid(), target, true));
                            }
                            ui.openPanel(player(), SETTINGS);
                        });
                return false;
            }
            case "unclaimall" -> {
                ui.confirm(player(), "Unclaim all land",
                        "Every chunk your faction owns becomes wilderness. Anyone can build there or claim it.",
                        yes -> {
                            if (yes) {
                                reply(ctx.service(ClaimService.class).unclaimAll(uuid(), true));
                            }
                            ui.openPanel(player(), SETTINGS);
                        });
                return false;
            }
            case "disband" -> {
                ui.confirm(player(), "Disband the faction",
                        "The faction, its land, its bank and its history are deleted for good. Every member becomes factionless.",
                        yes -> {
                            if (yes) {
                                reply(factions().disband(uuid(), true));
                            }
                            ui.openPanel(player(), OVERVIEW);
                        });
                return false;
            }
            case "leave" -> {
                ui.confirm(player(), "Leave the faction",
                        "You lose access to the faction land, home and bank. You need a new invite to come back.",
                        yes -> {
                            if (yes) {
                                reply(factions().leave(uuid()));
                            }
                            ui.openPanel(player(), OVERVIEW);
                        });
                return false;
            }
            case "log_filter" -> {
                logFilter = v.isEmpty() ? "all" : v;
                logPage = 0;
            }
            case "log_next" -> logPage++;
            case "log_prev" -> logPage = Math.max(0, logPage - 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- helpers ---------------------------------------------------------

    /** Registers a label for the once a second refresh. Only valid during render. */
    void addLive(String selector, java.util.function.Supplier<String> text) {
        pendingLive.add(new Live(selector, text));
    }

    boolean hasLive() {
        return !live.isEmpty();
    }

    /** Pushes fresh text into every live label. Runs on the player's world thread. */
    void pushLive() {
        List<Live> current = live;
        if (current.isEmpty()) {
            return;
        }
        UICommandBuilder c = new UICommandBuilder();
        for (Live l : current) {
            String text;
            try {
                text = l.text().get();
            } catch (RuntimeException ex) {
                continue;
            }
            c.set(l.selector() + ".Text", text == null ? "" : text);
        }
        sendUpdate(c, false);
    }

    /** Shows the confirm page, runs the action on yes and reopens the panel on a tab. */
    void confirmThen(String title, String consequence, java.util.function.Supplier<Result> action, String backTab) {
        ui.confirm(player(), title, consequence, yes -> {
            if (yes) {
                reply(action.get());
            }
            ui.openPanel(player(), backTab);
        });
    }

    void openZonePerms(String zoneId) {
        ui.openZonePerms(player(), zoneId);
    }

    private String channelLabel(PlayerData me) {
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat instanceof ChatServiceImpl impl) {
            return impl.currentChannelName(uuid()).replace(" chat", "");
        }
        return me.channel().name().toLowerCase(Locale.ROOT);
    }

    /** The newest audit entry that changed the message of the day, which announcements also set. */
    private static AuditEntry latestAnnouncement(Faction f) {
        AuditEntry best = null;
        for (AuditEntry e : f.audit()) {
            String text = e.text() == null ? "" : e.text().toLowerCase(Locale.ROOT);
            boolean match = "announce".equalsIgnoreCase(e.category()) || text.contains("message of the day");
            if (match && (best == null || e.at() > best.at())) {
                best = e;
            }
        }
        return best;
    }

    /** Allies whose home this faction's members may use, matching the home service rules. */
    private List<Faction> usableAllyHomes(Faction f) {
        List<Faction> out = new ArrayList<>();
        RelationService rs = ctx.service(RelationService.class);
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            if (e.getValue() != Relation.ALLY) {
                continue;
            }
            Faction o = factions().byId(e.getKey());
            if (o != null && o.home() != null && rs.between(f, o) == Relation.ALLY && o.setting("allyHome", false)
                    && o.relationCan(Relation.ALLY, FactionAction.USE_HOME)) {
                out.add(o);
            }
        }
        return out;
    }

    private interface TargetAction {
        Result apply(UUID target);
    }

    private static Result withTarget(String id, TargetAction action) {
        try {
            return action.apply(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Result.fail("That player could not be found.");
        }
    }

    private boolean defaultToggle(String key) {
        for (String[] t : TOGGLES) {
            if (t[0].equals(key)) {
                return toggleDefault(t[1]);
            }
        }
        return false;
    }

    private boolean toggleDefault(String source) {
        if ("true".equals(source) || "false".equals(source)) {
            return Boolean.parseBoolean(source);
        }
        try {
            return ctx.settings().bool(source);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static double parseAmount(String text) {
        try {
            double d = Double.parseDouble(text.replace(",", "").replace("_", "").trim());
            return Double.isFinite(d) ? d : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int onlineCount(Faction f) {
        int n = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                n++;
            }
        }
        return n;
    }

    private String names(Faction f, Relation relation) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            if (e.getValue() == relation) {
                Faction o = factions().byId(e.getKey());
                if (o != null) {
                    out.add(o.tag());
                }
            }
        }
        return out.isEmpty() ? "None" : String.join(", ", out);
    }

    private String nameOf(UUID player) {
        PlayerRef online = Players.online(player);
        if (online != null) {
            return online.getUsername();
        }
        PlayerData data = ctx.repo().playerIfLoaded(player);
        if (data != null && !data.name().isBlank()) {
            return data.name();
        }
        for (Faction f : factions().all()) {
            Member m = f.member(player);
            if (m != null) {
                return m.name();
            }
        }
        return "Unknown player";
    }

    private String nameOr(UUID player, String fallback) {
        String n = nameOf(player);
        return "Unknown player".equals(n) ? fallback : n;
    }

    /**
     * The relation service stores a request on the faction that receives it, keyed by the
     * sender. Incoming requests therefore live on our faction and outgoing ones on the others.
     */
    private List<Map.Entry<Faction, Faction.RelationRequest>> incomingRelationRequests(Faction f) {
        List<Map.Entry<Faction, Faction.RelationRequest>> out = new ArrayList<>();
        long now = Time.now();
        for (Map.Entry<UUID, Faction.RelationRequest> e : f.relationRequests().entrySet()) {
            Faction.RelationRequest req = e.getValue();
            Faction other = factions().byId(e.getKey());
            if (req != null && other != null && req.expiresAt() > now) {
                out.add(Map.entry(other, req));
            }
        }
        return out;
    }

    private List<Map.Entry<Faction, Faction.RelationRequest>> outgoingRelationRequests(Faction f) {
        List<Map.Entry<Faction, Faction.RelationRequest>> out = new ArrayList<>();
        long now = Time.now();
        for (Faction other : factions().all()) {
            if (other.id().equals(f.id())) {
                continue;
            }
            Faction.RelationRequest req = other.relationRequests().get(f.id());
            if (req != null && req.expiresAt() > now) {
                out.add(Map.entry(other, req));
            }
        }
        return out;
    }
}
