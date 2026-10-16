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
import dev.ryvrook.dominion.command.PlayerToggles;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.faction.FactionText;
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
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
    /** Most chunks on each side of the grid, Dominion_GridRow.ui has 15 cells. */
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
            {"open", "false", UiText.TOGGLE_OPEN},
            {"friendlyFire", "combat.friendlyFireDefault", UiText.TOGGLE_FRIENDLY_FIRE},
            {"allyBuild", "relation.allyBuildDefault", UiText.TOGGLE_ALLY_BUILD},
            {"truceInteract", "relation.truceInteractDefault", UiText.TOGGLE_TRUCE_INTERACT},
            {"allyHome", "false", UiText.TOGGLE_ALLY_HOME},
            {"homeOnDeath", "home.respawnAtHomeDefault", UiText.TOGGLE_HOME_ON_DEATH},
            {"loginAlerts", "chat.loginAlerts", UiText.TOGGLE_LOGIN_ALERTS}
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
        // The tag reads like a clan tag in the faction colour, then the name in the text colour.
        // When the tag only repeats the name it is hidden, and the name keeps its flex space so the
        // power and rank columns stay put.
        boolean both = f.tagDiffers();
        commands.set("#HeaderTag.Visible", both);
        commands.set("#HeaderTag.Text", both ? Faction.bracketed(f.tag()) : "");
        commands.set("#HeaderTag.Style.TextColor", safeColor(f.color(), "#7fd6ff"));
        commands.set("#HeaderName.Text", both ? f.name() : f.label());
        commands.set("#HeaderPower.Text", Messages.get(UiText.POWER_OF_MAX,
                "power", Msg.number(power.factionPower(f)), "max", Msg.number(power.factionMaxPower(f))));
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
        Set<String> hidden = hiddenTabs();
        if (hidden.contains(tab)) {
            tab = OVERVIEW;
        }

        Body tabs = new Body(commands, events, "#Tabs").hide(hidden);
        int online = onlineCount(f);
        tabs.tab(OVERVIEW, Messages.get(UiText.TAB_OVERVIEW), "", OVERVIEW.equals(tab), "tab");
        tabs.tab(MEMBERS, Messages.get(UiText.LABEL_MEMBERS), online + "/" + f.memberCount(), MEMBERS.equals(tab), "tab");
        tabs.tab(LAND, Messages.get(UiText.LABEL_LAND), String.valueOf(ctx.service(ClaimService.class).claimCount(f)), LAND.equals(tab), "tab");
        int pending = incomingRelationRequests(f).size();
        tabs.tab(RELATIONS, Messages.get(UiText.LABEL_RELATIONS), pending > 0 ? Messages.get(UiText.NEW_COUNT, "count", pending) : "",
                RELATIONS.equals(tab), "tab");
        if (eco != null) {
            tabs.tab(BANK, Messages.get(UiText.TAB_BANK), "", BANK.equals(tab), "tab");
        }
        if (hasSieges) {
            tabs.tab(SiegeTab.ID, sieges.label(), sieges.count(f), SiegeTab.ID.equals(tab), "tab");
        }
        if (hasMarket) {
            tabs.tab(MarketTab.ID, Messages.get(UiText.MARKET), market.count(), MarketTab.ID.equals(tab), "tab");
        }
        if (hasVault) {
            tabs.tab(ProgressTabs.VAULT, Messages.get(UiText.VAULT), "", ProgressTabs.VAULT.equals(tab), "tab");
        }
        if (hasProgress) {
            tabs.tab(ProgressTabs.UPGRADES, Messages.get(UiText.UPGRADES), "", ProgressTabs.UPGRADES.equals(tab), "tab");
            tabs.tab(ProgressTabs.MISSIONS, Messages.get(UiText.MISSIONS), "", ProgressTabs.MISSIONS.equals(tab), "tab");
        }
        if (hasEvents) {
            tabs.tab(EventsTab.ID, Messages.get(UiText.EVENTS), eventsTab.count(), EventsTab.ID.equals(tab), "tab");
        }
        tabs.tab(SETTINGS, Messages.get(UiText.SETTINGS_TITLE), "", SETTINGS.equals(tab), "tab");
        tabs.tab(LOG, Messages.get(UiText.TAB_LOG), "", LOG.equals(tab), "tab");
        tabs.tab(HELP, Messages.get(UiText.TAB_HELP), "", HELP.equals(tab), "tab");

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

    /**
     * Tabs left out of the sidebar. A tab goes when the sub command of the same name is in
     * commands.disabled or its feature switch is off. Overview and help always stay.
     */
    Set<String> hiddenTabs() {
        Set<String> out = new HashSet<>(CommandNames.disabled());
        out.remove(OVERVIEW);
        out.remove(HELP);
        if (out.contains("siege")) {
            out.add(SiegeTab.ID);
        }
        if (!ctx.settings().bool("upgrades.enabled")) {
            out.add(ProgressTabs.UPGRADES);
        }
        if (!ctx.settings().bool("missions.enabled")) {
            out.add(ProgressTabs.MISSIONS);
        }
        if (!ctx.settings().bool("vault.enabled")) {
            out.add(ProgressTabs.VAULT);
        }
        return out;
    }

    /** The feature switch behind a panel action, or null when the action has none. */
    static String switchOf(String action) {
        return switch (action == null ? "" : action) {
            case "home", "sethome", "ally_home" -> "home.enabled";
            case "channel" -> "chat.enabled";
            case "hud" -> "hud.enabled";
            case "top" -> "top.enabled";
            default -> null;
        };
    }

    void title(UICommandBuilder c, String title, String hint) {
        c.set("#TabTitle.Text", title);
        c.set("#TabHint.Text", hint == null ? "" : hint);
    }

    private void buildNoFaction(UICommandBuilder c, UIEventBuilder events) {
        PowerService power = ctx.service(PowerService.class);
        c.set("#HeaderTag.Text", Messages.get(UiText.HEADER_DOMINION));
        c.set("#HeaderTag.Style.TextColor", Msg.GOLD);
        c.set("#HeaderName.Text", Messages.get(UiText.HEADER_NO_FACTION));
        c.set("#HeaderPowerLabel.Text", Messages.get(UiText.HEADER_YOUR_POWER));
        c.set("#HeaderPower.Text", Messages.get(UiText.POWER_OF_MAX,
                "power", Msg.number(power.powerOf(uuid())), "max", Msg.number(power.maxPowerOf(uuid()))));
        c.set("#HeaderRank.Text", Messages.get(UiText.RANK_NONE));

        if (!HELP.equals(tab)) {
            tab = START;
        }
        Body tabs = new Body(c, events, "#Tabs");
        tabs.tab(START, Messages.get(UiText.TAB_JOIN_OR_CREATE), "", START.equals(tab), "tab");
        tabs.tab(HELP, Messages.get(UiText.TAB_HELP), "", HELP.equals(tab), "tab");

        Body body = new Body(c, events, "#Body");
        if (HELP.equals(tab)) {
            buildHelp(c, body);
            return;
        }
        title(c, Messages.get(UiText.TAB_JOIN_OR_CREATE), Messages.get(UiText.JOIN_OR_CREATE_HINT));
        body.text(Messages.get(UiText.JOIN_OR_CREATE_TEXT));
        // Tag and name are separate fields, and the hints follow the live limits.
        body.form(new String[] {Messages.get(UiText.FIELD_TAG), Messages.get(UiText.FIELD_NAME)},
                Btn.of(Messages.get(UiText.CREATE), "create"));
        body.text(Messages.get(UiText.CREATE_TAG_HINT, "min", ctx.settings().integer("faction.tagMin"),
                "max", ctx.settings().integer("faction.tagMax")), Msg.MUTED);
        body.text(Messages.get(UiText.CREATE_NAME_HINT, "max", ctx.settings().integer("faction.nameMax")), Msg.MUTED);
        body.buttons(Btn.of(Messages.get(UiText.TUTORIAL_BUTTON), "tutorial"), Btn.of(Messages.get(UiText.TOP_FACTIONS), "top"));

        body.section(Messages.get(UiText.FACTIONS_YOU_CAN_JOIN));
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
            String state = invited ? Messages.get(UiText.INVITED) : open ? Messages.get(UiText.STATE_OPEN) : Messages.get(UiText.INVITE_ONLY);
            Btn join = open || invited ? Btn.of(Messages.get(UiText.JOIN), "join", f.tag())
                    : Btn.of(Messages.get(UiText.REQUEST), "request", f.tag());
            body.row(f.label(), safeColor(f.color(), null),
                    new String[] {Messages.get(UiText.MEMBERS_COUNT, "count", f.memberCount()), state, "", ""},
                    join, Btn.of(Messages.get(UiText.INFO), "info", f.tag()));
            if (++shown >= 40) {
                break;
            }
        }
        if (shown == 0) {
            body.text(Messages.get(UiText.NO_FACTIONS_EXIST), Msg.MUTED);
        }
    }

    private void buildOverview(UICommandBuilder c, Body body, Faction f, EconomyService eco) {
        ClaimService claims = ctx.service(ClaimService.class);
        title(c, Messages.get(UiText.TAB_OVERVIEW), Messages.get(UiText.CREATED_ON, "date", Time.date(f.createdAt())));
        Member leader = f.leader() == null ? null : f.member(f.leader());
        body.stat(f.rankName(Rank.LEADER), leader == null ? Messages.get(UiText.NOBODY) : leader.name());
        body.stat(Messages.get(UiText.LABEL_MEMBERS), Messages.get(UiText.ONLINE_OF, "online", onlineCount(f), "total", f.memberCount()));
        body.stat(Messages.get(UiText.LABEL_LAND), Messages.get(UiText.LAND_OF_MAX, "claims", claims.claimCount(f), "max", claims.maxClaims(f)));
        body.stat(Messages.get(UiText.LABEL_ALLIES), names(f, Relation.ALLY), Relation.ALLY.color());
        body.stat(Messages.get(UiText.LABEL_ENEMIES), names(f, Relation.ENEMY), Relation.ENEMY.color());
        if (eco != null) {
            body.stat(Messages.get(UiText.TAB_BANK), eco.format(f.bank()));
        }
        progress.overviewStats(body, f);

        body.section(Messages.get(UiText.ANNOUNCEMENT));
        if (f.motd().isBlank()) {
            body.text(Messages.get(UiText.NO_ANNOUNCEMENT), Msg.MUTED);
        } else {
            body.text(f.motd());
            AuditEntry posted = latestAnnouncement(f);
            if (posted != null) {
                body.text(posted.whoName() == null || posted.whoName().isBlank()
                        ? Messages.get(UiText.POSTED, "when", Time.ago(posted.at()))
                        : Messages.get(UiText.POSTED_BY, "when", Time.ago(posted.at()), "name", posted.whoName()), Msg.MUTED);
            }
        }
        if (factions().can(uuid(), FactionAction.ANNOUNCE)) {
            body.field(Messages.get(UiText.NEW_ANNOUNCEMENT), "", Btn.of(Messages.get(UiText.POST), "announce"));
        }
        body.section(Messages.get(UiText.DESCRIPTION));
        body.text(f.description().isBlank() ? Messages.get(UiText.NO_DESCRIPTION_YET) : f.description());

        PlayerData me = ctx.player(uuid());
        body.section(Messages.get(UiText.QUICK_ACTIONS));
        body.buttons(Btn.of(Messages.get(UiText.HOME), "home"), Btn.of(Messages.get(UiText.MAP), "tab", LAND),
                Btn.of(Messages.get(UiText.CHAT_CHANNEL, "channel", channelLabel(me)), "channel"),
                Btn.of(me.alerts() ? Messages.get(UiText.ALERTS_ON) : Messages.get(UiText.ALERTS_OFF), "alerts"));
        body.buttons(Btn.of(me.hud() ? Messages.get(UiText.SIDEBAR_ON) : Messages.get(UiText.SIDEBAR_OFF), "hud"),
                Btn.of(me.mapHidden() ? Messages.get(UiText.WEB_MAP_HIDDEN) : Messages.get(UiText.WEB_MAP_SHOWN), "maphide"),
                Btn.of(Messages.get(UiText.TUTORIAL_BUTTON), "tutorial"), Btn.of(Messages.get(UiText.TOP_FACTIONS), "top"));
    }

    private void buildMembers(UICommandBuilder c, Body body, Faction f) {
        PowerService power = ctx.service(PowerService.class);
        title(c, Messages.get(UiText.LABEL_MEMBERS), Messages.get(UiText.ONLINE_OF, "online", onlineCount(f), "total", f.memberCount()));
        body.row(Messages.get(UiText.COL_NAME), Msg.MUTED, new String[] {Messages.get(UiText.COL_RANK), Messages.get(UiText.LABEL_POWER),
                Messages.get(UiText.COL_LAST_SEEN), Messages.get(UiText.COL_TITLE)});
        List<Member> members = new ArrayList<>(f.members());
        UUID leaderId = f.leader();
        members.sort(Comparator.comparing((Member m) -> m.uuid().equals(leaderId) ? Rank.LEADER : m.rank()).reversed()
                .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        boolean canPromote = can(FactionAction.PROMOTE);
        boolean canKick = can(FactionAction.KICK);
        boolean canInvite = can(FactionAction.INVITE);
        for (Member m : members) {
            boolean online = Players.isOnline(m.uuid());
            Rank rank = m.uuid().equals(leaderId) ? Rank.LEADER : m.rank();
            String[] cols = {f.rankName(rank), Msg.number(power.powerOf(m.uuid())),
                    online ? Messages.get(UiText.ONLINE) : Time.ago(m.lastSeen()), m.title()};
            String row;
            if (m.uuid().equals(uuid())) {
                row = body.row(Messages.get(UiText.NAME_YOU, "name", m.name()), rank == Rank.LEADER ? Msg.GOLD : null, cols);
            } else {
                String id = m.uuid().toString();
                // Buttons only for what the viewer's rank may do. The service still checks every press.
                boolean leaderRow = rank == Rank.LEADER;
                row = body.row(m.name(), leaderRow ? Msg.GOLD : null, cols,
                        canPromote && !leaderRow ? Btn.of(Messages.get(UiText.PROMOTE), "promote", id) : null,
                        canPromote && !leaderRow ? Btn.of(Messages.get(UiText.DEMOTE), "demote", id) : null,
                        canKick && !leaderRow ? Btn.of(Messages.get(UiText.KICK), "kick", id) : null);
            }
            body.dot(row, online);
        }

        if (canInvite) {
            body.section(Messages.get(UiText.INVITE_SECTION));
            body.field(Messages.get(UiText.PLAYER_NAME), "", Btn.of(Messages.get(UiText.INVITE), "invite"));
        }

        body.section(Messages.get(UiText.JOIN_REQUESTS));
        List<UUID> requests = f.requests();
        if (requests.isEmpty()) {
            body.text(Messages.get(UiText.NO_PENDING_REQUESTS), Msg.MUTED);
        } else if (!canInvite) {
            body.text(Messages.get(UiText.INVITE_RIGHT_HINT), Msg.MUTED);
        }
        for (UUID r : requests) {
            String id = r.toString();
            body.row(nameOf(r), null, new String[] {Messages.get(UiText.WANTS_TO_JOIN), "", "", ""},
                    canInvite ? Btn.of(Messages.get(UiText.ACCEPT), "req_accept", id) : null,
                    canInvite ? Btn.of(Messages.get(UiText.DECLINE), "req_decline", id) : null);
        }
    }

    private void buildLand(UICommandBuilder c, Body body, Faction f) {
        ClaimService claims = ctx.service(ClaimService.class);
        PlayerData me = ctx.player(uuid());
        String world = Players.worldName(player());
        long chunk = Players.chunkOf(player());
        title(c, Messages.get(UiText.LABEL_LAND), Messages.get(UiText.LAND_CLAIMED_OF_MAX, "claims", claims.claimCount(f), "max", claims.maxClaims(f)));

        if (world != null && chunk != Long.MIN_VALUE) {
            Faction owner = claims.ownerAt(world, chunk);
            ClaimEntry entry = claims.claimAt(world, chunk);
            body.stat(Messages.get(UiText.STANDING_IN), Messages.get(UiText.CHUNK_OF_WORLD, "chunk", Keys.describe(chunk), "world", world));
            if (owner == null) {
                body.stat(Messages.get(UiText.OWNER), Messages.get(UiText.WILDERNESS), "#5fd37a");
            } else {
                Relation rel = ctx.service(RelationService.class).playerToFaction(uuid(), owner);
                body.stat(Messages.get(UiText.OWNER), owner.label(), rel == null ? Msg.TEXT : rel.color());
                if (entry != null) {
                    body.stat(Messages.get(UiText.CLAIMED), entry.claimedBy() == null ? Time.ago(entry.claimedAt())
                            : Messages.get(UiText.CLAIMED_BY, "when", Time.ago(entry.claimedAt()), "name", nameOf(entry.claimedBy())));
                }
            }
        }
        boolean canClaim = can(FactionAction.CLAIM);
        body.buttons(canClaim ? Btn.of(Messages.get(UiText.CLAIM), "claim") : null,
                can(FactionAction.UNCLAIM) ? Btn.of(Messages.get(UiText.UNCLAIM), "unclaim") : null,
                canClaim || me.autoClaim() ? Btn.of(me.autoClaim() ? Messages.get(UiText.AUTOCLAIM_ON)
                        : Messages.get(UiText.AUTOCLAIM_OFF), "autoclaim") : null,
                can(FactionAction.SET_HOME) ? Btn.of(Messages.get(UiText.SET_HOME), "sethome") : null,
                Btn.of(Messages.get(UiText.REFRESH), "refresh"));
        if (!canClaim) {
            body.text(Messages.get(UiText.CANNOT_CLAIM), Msg.MUTED);
        }

        body.section(Messages.get(UiText.NEARBY_CHUNKS));
        int gridRadius = Math.max(2, Math.min(GRID_RADIUS, ctx.settings().integer("ui.gridRadius")));
        int size = gridRadius * 2 + 1;
        List<String[]> cells;
        try {
            cells = claims.gridMap(uuid(), gridRadius);
        } catch (Exception e) {
            cells = List.of();
        }
        if (cells == null || cells.size() < size * size) {
            body.text(Messages.get(UiText.MAP_UNAVAILABLE), Msg.MUTED);
        } else {
            for (int r = 0; r < size; r++) {
                String[] glyphs = new String[size];
                String[] text = new String[size];
                String[] fill = new String[size];
                for (int col = 0; col < size; col++) {
                    String[] cell = cells.get(r * size + col);
                    String color = cell != null && cell.length > 0 ? safeColor(cell[0], null) : null;
                    String tag = cell != null && cell.length > 1 && cell[1] != null ? cell[1].trim() : "";
                    String flags = cell != null && cell.length > 2 && cell[2] != null ? cell[2] : "";
                    boolean centre = r == gridRadius && col == gridRadius;
                    boolean claimed = !tag.isEmpty() && color != null;
                    glyphs[col] = centre ? "@" : claimed ? tag.substring(0, 1).toUpperCase(Locale.ROOT) : "";
                    text[col] = centre ? "#ffffff" : "#f2f4f7";
                    if (claimed) {
                        // Zone chunks sit a little lighter than the rest of the owner's land.
                        fill[col] = shade(color, flags.contains("zone") ? 0.6 : 0.45);
                    } else {
                        fill[col] = centre ? "#4a5260" : "#2a2f38";
                    }
                }
                body.gridRow(glyphs, text, fill);
            }
            body.text(Messages.get(UiText.MAP_LEGEND), Msg.MUTED);
        }
    }

    /** Darkens a #rrggbb colour to the given share of its brightness so a white letter reads on it. */
    static String shade(String hex, double keep) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double k = Math.max(0, Math.min(1, keep));
        int r = (int) Math.round(((rgb >> 16) & 0xff) * k);
        int g = (int) Math.round(((rgb >> 8) & 0xff) * k);
        int b = (int) Math.round((rgb & 0xff) * k);
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }

    private void buildRelations(UICommandBuilder c, Body body, Faction f) {
        title(c, Messages.get(UiText.LABEL_RELATIONS), Messages.get(UiText.RELATIONS_COUNT,
                "allies", f.countRelation(Relation.ALLY), "enemies", f.countRelation(Relation.ENEMY)));
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
        String[] headings = {UiText.LABEL_ALLIES, UiText.LABEL_TRUCES, UiText.LABEL_ENEMIES};
        int i = 0;
        boolean canRelate = can(FactionAction.RELATIONS);
        for (Map.Entry<Relation, List<Faction>> g : grouped.entrySet()) {
            body.section(Messages.get(headings[i++]));
            if (g.getValue().isEmpty()) {
                body.text(Messages.get(UiText.NONE_DOT), Msg.MUTED);
            }
            for (Faction o : g.getValue()) {
                Btn enemy = g.getKey() == Relation.ENEMY || !canRelate ? null : Btn.of(Messages.get(UiText.ENEMY), "rel_enemy", o.tag());
                body.row(o.label(), g.getKey().color(),
                        new String[] {Messages.get(UiText.MEMBERS_COUNT, "count", o.memberCount()), "", "", ""},
                        canRelate ? Btn.of(Messages.get(UiText.NEUTRAL), "rel_neutral", o.tag()) : null, enemy,
                        Btn.of(Messages.get(UiText.INFO), "info", o.tag()));
            }
        }

        List<Faction> allyHomes = usableAllyHomes(f);
        if (!allyHomes.isEmpty()) {
            body.section(Messages.get(UiText.ALLY_HOMES));
            for (Faction o : allyHomes) {
                body.row(o.label(), Relation.ALLY.color(),
                        new String[] {Messages.get(UiText.HOME_OPEN), "", "", ""}, Btn.of(Messages.get(UiText.VISIT), "ally_home", o.tag()));
            }
        }

        body.section(Messages.get(UiText.REQUESTS));
        List<Map.Entry<Faction, Faction.RelationRequest>> incoming = incomingRelationRequests(f);
        List<Map.Entry<Faction, Faction.RelationRequest>> outgoing = outgoingRelationRequests(f);
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            body.text(Messages.get(UiText.NO_PENDING_REQUESTS), Msg.MUTED);
        }
        for (Map.Entry<Faction, Faction.RelationRequest> e : incoming) {
            Faction o = e.getKey();
            body.row(o.label(), e.getValue().type().color(),
                    new String[] {Messages.get(UiText.OFFERS, "relation", e.getValue().type().displayName().toLowerCase(Locale.ROOT)),
                            Messages.get(UiText.EXPIRES_IN, "time", Time.remaining(e.getValue().expiresAt() - Time.now())), "", ""},
                    canRelate ? Btn.of(Messages.get(UiText.ACCEPT), "rel_accept", o.tag()) : null,
                    canRelate ? Btn.of(Messages.get(UiText.DECLINE), "rel_decline", o.tag()) : null);
        }
        for (Map.Entry<Faction, Faction.RelationRequest> e : outgoing) {
            Faction o = e.getKey();
            body.row(o.label(), e.getValue().type().color(),
                    new String[] {Messages.get(UiText.YOU_OFFERED, "relation", e.getValue().type().displayName().toLowerCase(Locale.ROOT)),
                            Messages.get(UiText.EXPIRES_IN, "time", Time.remaining(e.getValue().expiresAt() - Time.now())), "", ""},
                    canRelate ? Btn.of(Messages.get(UiText.WITHDRAW), "rel_neutral", o.tag()) : null);
        }

        if (canRelate) {
            body.section(Messages.get(UiText.NEW_RELATION));
            body.field(Messages.get(UiText.FACTION_TAG), "", Btn.of(Messages.get(UiText.ALLY), "rel_ally"),
                    Btn.of(Messages.get(UiText.TRUCE), "rel_truce"), Btn.of(Messages.get(UiText.ENEMY), "rel_enemy_tag"));
        } else {
            body.text(Messages.get(UiText.RELATIONS_RIGHT_HINT), Msg.MUTED);
        }

        nation.build(body, f);
    }

    private void buildBank(UICommandBuilder c, Body body, Faction f, EconomyService eco) {
        title(c, Messages.get(UiText.TAB_BANK), Messages.get(UiText.BANK_HINT));
        body.stat(Messages.get(UiText.FACTION_BALANCE), eco.format(f.bank()), Msg.GOLD);
        body.stat(Messages.get(UiText.YOUR_WALLET), eco.format(eco.balance(uuid())));
        boolean canDeposit = can(FactionAction.DEPOSIT);
        boolean canWithdraw = can(FactionAction.WITHDRAW);
        if (canDeposit || canWithdraw) {
            body.field(Messages.get(UiText.FIELD_AMOUNT), "", canDeposit ? Btn.of(Messages.get(UiText.DEPOSIT), "deposit") : null,
                    canWithdraw ? Btn.of(Messages.get(UiText.WITHDRAW), "withdraw") : null);
        } else {
            body.text(Messages.get(UiText.CANNOT_MOVE_MONEY), Msg.MUTED);
        }
        body.section(Messages.get(UiText.RECENT_BANK));
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
            body.text(Messages.get(UiText.NO_BANK_ACTIVITY), Msg.MUTED);
        }
    }

    private void buildSettings(UICommandBuilder c, Body body, Faction f) {
        boolean canSet = can(FactionAction.SETTINGS);
        title(c, Messages.get(UiText.SETTINGS_TITLE), canSet ? Messages.get(UiText.SETTINGS_APPLY_AT_ONCE) : Messages.get(UiText.SETTINGS_LOOK_ONLY));
        body.section(Messages.get(UiText.RULES));
        for (String[] t : TOGGLES) {
            boolean on = f.setting(t[0], toggleDefault(t[1]));
            body.toggle(Messages.get(t[2]), t[0], on, "toggle", t[0], canSet);
        }
        body.section(Messages.get(UiText.IDENTITY));
        if (canSet) {
            body.palette(safeColor(f.color(), null), "color");
            body.field(Messages.get(UiText.FIELD_NAME), f.name(), Btn.of(Messages.get(UiText.SAVE), "rename"));
            body.field(Messages.get(UiText.DESCRIPTION), f.description(), Btn.of(Messages.get(UiText.SAVE), "describe"));
            body.field(Messages.get(UiText.MOTD), f.motd(), Btn.of(Messages.get(UiText.SAVE), "motd"));
        } else {
            body.stat(Messages.get(UiText.FIELD_NAME), f.name());
            body.stat(Messages.get(UiText.DESCRIPTION), f.description().isBlank() ? Messages.get(UiText.RANK_NONE) : f.description());
        }
        body.buttons(Btn.of(Messages.get(UiText.PERMISSIONS), "perms"));
        zones.buildRankNames(body, f);
        zones.buildZones(body, f);

        boolean leader = uuid().equals(f.leader());
        boolean canDisband = can(FactionAction.DISBAND);
        body.section(leader || canDisband ? Messages.get(UiText.DANGER_ZONE) : Messages.get(UiText.LEAVE));
        if (leader || canDisband) {
            body.text(Messages.get(UiText.CANNOT_BE_UNDONE), Msg.WARN);
        }
        if (leader) {
            body.field(Messages.get(UiText.NEW_LEADER), "", Btn.of(Messages.get(UiText.TRANSFER), "transfer"));
        }
        body.buttons(canDisband ? Btn.of(Messages.get(UiText.UNCLAIM_ALL), "unclaimall") : null,
                canDisband ? Btn.of(Messages.get(UiText.DISBAND), "disband") : null,
                Btn.of(Messages.get(UiText.LEAVE), "leave"));
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
        int logPageSize = Math.max(1, ctx.settings().integer("ui.logPageSize"));
        int pages = Math.max(1, (filtered.size() + logPageSize - 1) / logPageSize);
        logPage = Math.max(0, Math.min(logPage, pages - 1));
        title(c, Messages.get(UiText.TAB_LOG), Messages.get(UiText.LOG_PAGE, "count", filtered.size(), "page", logPage + 1, "pages", pages));

        List<Btn> pills = new ArrayList<>();
        String allLabel = Messages.get(UiText.LOG_ALL);
        pills.add(Btn.of("all".equals(logFilter) ? "[ " + allLabel + " ]" : allLabel, "log_filter", "all"));
        for (String cat : categories) {
            if (pills.size() >= 6) {
                break;
            }
            pills.add(Btn.of(cat.equals(logFilter) ? "[ " + cat + " ]" : cat, "log_filter", cat));
        }
        body.buttons(pills.toArray(new Btn[0]));

        int from = logPage * logPageSize;
        int to = Math.min(filtered.size(), from + logPageSize);
        for (int i = from; i < to; i++) {
            AuditEntry e = filtered.get(i);
            body.row(e.text(), null, new String[] {e.category() == null ? "" : e.category(),
                    e.whoName() == null ? "" : e.whoName(), Time.ago(e.at()), ""});
        }
        if (filtered.isEmpty()) {
            body.text(Messages.get(UiText.NOTHING_LOGGED), Msg.MUTED);
        }
        body.buttons(logPage > 0 ? Btn.of(Messages.get(UiText.PREVIOUS), "log_prev") : null,
                logPage < pages - 1 ? Btn.of(Messages.get(UiText.NEXT), "log_next") : null);
    }

    private void buildHelp(UICommandBuilder c, Body body) {
        title(c, Messages.get(UiText.TAB_HELP), Messages.get(UiText.HELP_HINT));
        body.section(Messages.get(UiText.LABEL_POWER));
        body.text(Messages.get(UiText.HELP_POWER));
        body.section(Messages.get(UiText.HELP_CLAIMS_TITLE));
        body.text(Messages.get(UiText.HELP_CLAIMS));
        body.section(Messages.get(UiText.LABEL_RELATIONS));
        body.text(Messages.get(UiText.HELP_RELATIONS));
        body.section(Messages.get(UiText.HELP_RAIDING_TITLE));
        body.text(Messages.get(UiText.HELP_RAIDING));
        if (sieges.available()) {
            body.section(Messages.get(UiText.HELP_SIEGES_TITLE));
            body.text(Messages.get(UiText.HELP_SIEGES));
        }
        if (market.available()) {
            body.section(Messages.get(UiText.MARKET));
            body.text(Messages.get(UiText.HELP_MARKET));
        }
        if (nation.nations() != null) {
            body.section(Messages.get(UiText.HELP_NATIONS_TITLE));
            body.text(Messages.get(UiText.HELP_NATIONS));
        }
        if (eventsTab.available()) {
            body.section(Messages.get(UiText.EVENTS));
            body.text(Messages.get(UiText.HELP_EVENTS));
        }
        body.section(Messages.get(UiText.HELP_COMMANDS_TITLE));
        body.text(Messages.get(UiText.HELP_COMMANDS), Msg.MUTED);
    }

    // ---- actions ---------------------------------------------------------

    @Override
    protected boolean onAction(PageEvent e) {
        String v = e.value();
        String text = e.text();
        String feature = switchOf(e.action);
        if ((!"tab".equals(e.action) && CommandNames.disabled(e.action)) || (feature != null && !ctx.settings().bool(feature))) {
            // A button for a sub command that commands.disabled left out, or for a feature that is off, is refused like the command.
            reply(Result.fail(Messages.get(CommonText.FEATURE_OFF)));
            return true;
        }
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
                    Msg.send(player(), Msg.bad(Messages.get(UiText.FACTION_TAG_NEEDED)));
                    return false;
                }
                Result r = factions().create(uuid(), username(), text, e.text2());
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
                        : Messages.get(UiText.CHANNEL_CHAT, "channel", ch.name().toLowerCase(Locale.ROOT));
                Msg.send(player(), Msg.good(Messages.get(UiText.TALK_IN, "channel", name)));
            }
            case "alerts" -> {
                PlayerData me = ctx.player(uuid());
                me.setAlerts(!me.alerts());
                Msg.send(player(), Msg.good(me.alerts() ? Messages.get(UiText.FACTION_ALERTS_ON) : Messages.get(UiText.FACTION_ALERTS_OFF)));
            }
            case "hud" -> {
                boolean next = !ctx.player(uuid()).hud();
                ui.setHudVisible(player(), next);
                Msg.send(player(), Msg.good(next ? Messages.get(UiText.SIDEBAR_NOW_ON) : Messages.get(UiText.SIDEBAR_NOW_OFF)));
            }
            case "maphide" -> {
                PlayerData me = ctx.player(uuid());
                Msg.send(player(), Msg.good(PlayerToggles.webMap(me, !me.mapHidden())));
            }
            case "promote" -> reply(withTarget(v, t -> factions().promote(uuid(), t)));
            case "demote" -> reply(withTarget(v, t -> factions().demote(uuid(), t)));
            case "kick" -> reply(withTarget(v, t -> factions().kick(uuid(), t)));
            case "req_accept" -> reply(withTarget(v, t -> factions().acceptRequest(uuid(), t)));
            case "req_decline" -> reply(withTarget(v, t -> factions().declineRequest(uuid(), t)));
            case "invite" -> {
                UUID target = resolvePlayer(text);
                if (target == null) {
                    reply(Result.fail(text.isEmpty() ? Messages.get(UiText.PLAYER_NAME_NEEDED)
                            : Messages.get(UiText.NO_PLAYER_NAMED, "name", text)));
                } else {
                    reply(factions().invite(uuid(), target, nameOr(target, text)));
                }
            }
            case "claim", "unclaim" -> {
                String world = Players.worldName(player());
                long chunk = Players.chunkOf(player());
                if (world == null || chunk == Long.MIN_VALUE) {
                    reply(Result.fail(Messages.get(UiText.POSITION_UNKNOWN_RETRY)));
                } else {
                    ClaimService claims = ctx.service(ClaimService.class);
                    reply("claim".equals(e.action) ? claims.claim(uuid(), world, chunk) : claims.unclaim(uuid(), world, chunk));
                }
            }
            case "autoclaim" -> {
                PlayerData me = ctx.player(uuid());
                me.setAutoClaim(!me.autoClaim());
                Msg.send(player(), Msg.good(me.autoClaim() ? Messages.get(UiText.AUTOCLAIM_NOW_ON)
                        : Messages.get(UiText.AUTOCLAIM_NOW_OFF)));
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
            case "announce" -> reply(text.isEmpty() ? Result.fail(Messages.get(UiText.ANNOUNCEMENT_FIRST))
                    : ctx.service(ChatService.class).announce(uuid(), text));
            case "rel_accept" -> reply(ctx.service(RelationService.class).accept(uuid(), v));
            case "rel_decline" -> reply(ctx.service(RelationService.class).decline(uuid(), v));
            case "rel_neutral" -> reply(ctx.service(RelationService.class).request(uuid(), v, Relation.NEUTRAL));
            case "rel_enemy" -> reply(ctx.service(RelationService.class).request(uuid(), v, Relation.ENEMY));
            case "rel_ally", "rel_truce", "rel_enemy_tag" -> {
                if (text.isEmpty()) {
                    reply(Result.fail(Messages.get(UiText.FACTION_TAG_FIRST)));
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
                    reply(Result.fail(Messages.get(UiText.ECONOMY_OFF)));
                } else {
                    double amount = parseAmount(text);
                    if (amount <= 0) {
                        reply(Result.fail(Messages.get(UiText.POSITIVE_AMOUNT_FIRST)));
                    } else {
                        reply("deposit".equals(e.action) ? eco.bankDeposit(uuid(), amount) : eco.bankWithdraw(uuid(), amount));
                    }
                }
            }
            case "toggle" -> {
                if (f == null || !isToggle(v)) {
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
                // A null tag keeps the current one, the same as /f name.
                reply(text.isEmpty() ? Result.fail(Messages.get(UiText.NAME_FIRST)) : factions().rename(uuid(), null, text));
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
                    reply(Result.fail(text.isEmpty() ? Messages.get(UiText.NEW_LEADER_FIRST)
                            : Messages.get(UiText.NO_PLAYER_NAMED, "name", text)));
                    return true;
                }
                String name = nameOr(target, text);
                ui.confirm(player(), Messages.get(UiText.TRANSFER_TITLE),
                        Messages.get(UiText.TRANSFER_BODY, "name", name),
                        yes -> {
                            if (yes) {
                                reply(factions().transferLeader(uuid(), target, true));
                            }
                            ui.openPanel(player(), SETTINGS);
                        });
                return false;
            }
            case "unclaimall" -> {
                ui.confirm(player(), Messages.get(UiText.UNCLAIM_ALL_TITLE),
                        Messages.get(UiText.UNCLAIM_ALL_BODY),
                        yes -> {
                            if (yes) {
                                reply(ctx.service(ClaimService.class).unclaimAll(uuid(), true));
                            }
                            ui.openPanel(player(), SETTINGS);
                        });
                return false;
            }
            case "disband" -> {
                ui.confirm(player(), Messages.get(UiText.DISBAND_TITLE),
                        Messages.get(UiText.DISBAND_BODY),
                        yes -> {
                            if (yes) {
                                reply(factions().disband(uuid(), true));
                            }
                            ui.openPanel(player(), OVERVIEW);
                        });
                return false;
            }
            case "leave" -> {
                ui.confirm(player(), Messages.get(UiText.LEAVE_TITLE),
                        Messages.get(UiText.LEAVE_BODY),
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
    static AuditEntry latestAnnouncement(Faction f) {
        // The audit line is worded by messages.json, so match the wording in use as well as the shipped one older entries carry.
        String changed = Messages.get(FactionText.AUDIT_MOTD_CHANGED).toLowerCase(Locale.ROOT);
        String cleared = Messages.get(FactionText.AUDIT_MOTD_CLEARED).toLowerCase(Locale.ROOT);
        AuditEntry best = null;
        for (AuditEntry e : f.audit()) {
            String text = e.text() == null ? "" : e.text().toLowerCase(Locale.ROOT);
            boolean match = "announce".equalsIgnoreCase(e.category()) || text.contains("message of the day")
                    || text.equals(changed) || text.equals(cleared);
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
            return Result.fail(Messages.get(UiText.PLAYER_NOT_FOUND));
        }
    }

    /** Only the toggles this page shows can be switched, whatever value a client sends. */
    private static boolean isToggle(String key) {
        for (String[] t : TOGGLES) {
            if (t[0].equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean can(FactionAction action) {
        try {
            return factions().can(uuid(), action);
        } catch (RuntimeException e) {
            return false;
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
        return out.isEmpty() ? Messages.get(UiText.RANK_NONE) : String.join(", ", out);
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
        return Messages.get(UiText.UNKNOWN_PLAYER);
    }

    private String nameOr(UUID player, String fallback) {
        String n = nameOf(player);
        return Messages.get(UiText.UNKNOWN_PLAYER).equals(n) ? fallback : n;
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
