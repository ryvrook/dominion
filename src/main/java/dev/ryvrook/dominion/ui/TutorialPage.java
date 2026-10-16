package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.integration.TutorialCredit;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * A guided tour of factions, one step per screen. Each step explains a feature, shows its commands in code
 * rows and, where it can, offers a button that does the step through the same services the panel uses.
 * Progress is kept in PlayerData. Finishing or skipping marks the tutorial as finished.
 */
public final class TutorialPage extends DominionPage {
    static final String PAGE = "Pages/Dominion_Tutorial.ui";
    static final String CODE = "Pages/Dominion_Tutorial_Code.ui";

    static final String FOUND = "found";
    static final String CLAIM = "claim";
    static final String MAP = "map";
    static final String HOME = "home";
    static final String INVITE = "invite";
    static final String RELATIONS = "relations";
    static final String RAIDS = "raids";
    static final String BANK = "bank";
    static final String CHAT = "chat";
    static final String COMMANDS = "commands";
    /** Every step in the order the page shows them. misc.tutorialSteps can leave some out but never reorders them. */
    public static final List<String> STEPS = List.of(FOUND, CLAIM, MAP, HOME, INVITE, RELATIONS, RAIDS, BANK, CHAT, COMMANDS);
    /** Dots in Dominion_Tutorial.ui, one per step. */
    static final int DOTS = 10;
    private static final String DOT_AHEAD = "#4a5260";
    private static final String GREEN = "#5fd37a";
    /** Join rows shown on the first step. */
    private static final int JOIN_ROWS = 4;

    /** The button that does the step, with the action it sends and the form fields it submits. */
    private record Act(String label, String action, String... inputs) {}

    private String current;
    private String reply;
    private boolean replyGood;

    /**
     * Opens at the first step, or with resume at the furthest step the player reached when the
     * tutorial is not finished yet.
     */
    public TutorialPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui, boolean resume) {
        super(player, ctx, ui);
        List<String> steps = steps();
        PlayerData data = ctx.player(player.getUuid());
        current = steps.get(startIndex(steps, data.tutorialStep(), data.tutorialFinished(), resume));
    }

    // ---- step order, pure so the tests can reach it ------------------------

    /** The steps misc.tutorialSteps names, in page order. Unknown ids are ignored, and an empty list means every step. */
    static List<String> configured(String csv) {
        Set<String> wanted = new HashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String id = part.trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty()) {
                    wanted.add(id);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String id : STEPS) {
            if (wanted.contains(id)) {
                out.add(id);
            }
        }
        return out.isEmpty() ? STEPS : List.copyOf(out);
    }

    /**
     * The configured steps whose feature is available. Never empty, the commands step stands in when
     * nothing else is left.
     */
    static List<String> visible(String csv, Predicate<String> available) {
        List<String> out = new ArrayList<>();
        for (String id : configured(csv)) {
            if (available.test(id)) {
                out.add(id);
            }
        }
        return out.isEmpty() ? List.of(COMMANDS) : List.copyOf(out);
    }

    /**
     * Where a step id lands among the visible steps. A step that was trimmed out lands on the next
     * visible one, or the last when none follows. An unknown or null id lands on the first.
     */
    static int indexFor(List<String> steps, String id) {
        if (id == null) {
            return 0;
        }
        int at = steps.indexOf(id);
        if (at >= 0) {
            return at;
        }
        int order = STEPS.indexOf(id);
        if (order < 0) {
            return 0;
        }
        for (int i = 0; i < steps.size(); i++) {
            if (STEPS.indexOf(steps.get(i)) > order) {
                return i;
            }
        }
        return steps.size() - 1;
    }

    /** The step a page opens on. Only a resumed, unfinished tutorial starts past the first step. */
    static int startIndex(List<String> steps, String reached, boolean finished, boolean resume) {
        if (!resume || finished || reached == null) {
            return 0;
        }
        return indexFor(steps, reached);
    }

    /** The later of two step ids in page order, so going back never lowers the step reached. */
    static String furthest(String a, String b) {
        if (a == null || !STEPS.contains(a)) {
            return b;
        }
        if (b == null || !STEPS.contains(b)) {
            return a;
        }
        return STEPS.indexOf(b) > STEPS.indexOf(a) ? b : a;
    }

    private List<String> steps() {
        return visible(ctx.settings().text("misc.tutorialSteps"), this::available);
    }

    /** False for a step whose feature is switched off or whose commands are disabled on this server. */
    private boolean available(String id) {
        Settings s = ctx.settings();
        return switch (id) {
            case MAP -> s.bool("map.enabled") && !CommandNames.disabled("map");
            case HOME -> s.bool("home.enabled") && !CommandNames.disabled("sethome");
            case INVITE -> !CommandNames.disabled("invite");
            case RAIDS -> s.bool("raid.enabled");
            case BANK -> economy() != null || ctx.serviceOrNull(MarketService.class) != null;
            case CHAT -> s.bool("chat.enabled");
            default -> true;
        };
    }

    private EconomyService economy() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && eco.enabled() ? eco : null;
    }

    // ---- render --------------------------------------------------------------

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        List<String> steps = steps();
        int index = indexFor(steps, current);
        current = steps.get(index);
        PlayerData data = ctx.player(uuid());
        data.setTutorialStep(furthest(data.tutorialStep(), current));

        Faction f = factions().factionOf(uuid());
        String accent = f == null ? safeColor(Msg.GOLD, "#E8A93B") : safeColor(f.color(), safeColor(Msg.GOLD, "#E8A93B"));
        c.append(PAGE);
        c.set("#PageTitle.Text", Messages.get(UiText.TUTORIAL_TITLE));
        c.set("#Heading.Style.TextColor", accent);
        c.set("#Counter.Text", Messages.get(UiText.TUTORIAL_STEP, "step", index + 1, "total", steps.size()));
        for (int i = 0; i < Math.min(DOTS, steps.size()); i++) {
            String dot = "#D" + i;
            c.set(dot + ".Visible", true);
            String color = i < index ? GREEN : i == index ? accent : DOT_AHEAD;
            c.setObject(dot + ".Background", new PatchStyle().setColor(Value.of(color)));
        }

        Body body = new Body(c, events, "#Body");
        boolean[] done = {false};
        Act act = switch (current) {
            case FOUND -> found(c, body, f, done);
            case CLAIM -> claim(c, body, f, done);
            case MAP -> map(c, body);
            case HOME -> home(c, body, f, done);
            case INVITE -> invite(c, body, f, done);
            case RELATIONS -> relations(c, body);
            case RAIDS -> raids(c, body);
            case BANK -> bank(c, body, f);
            case CHAT -> chat(c, body);
            default -> commands(c, body);
        };

        boolean last = index == steps.size() - 1;
        if (act != null && !done[0]) {
            c.set("#Action.Visible", true);
            c.set("#Action.Text", act.label());
            body.bindWithTexts("#Action", act.action(), "", act.inputs());
            c.set("#Next.Visible", true);
            c.set("#Next.Text", Messages.get(last ? UiText.FINISH : UiText.NEXT));
            Body.on(events, "#Next", last ? "finish" : "next", "");
        } else {
            // Nothing left to do on this step, so the primary button moves on.
            c.set("#Action.Visible", true);
            c.set("#Action.Text", Messages.get(last ? UiText.TUTORIAL_CLOSE : UiText.NEXT));
            Body.on(events, "#Action", last ? "finish" : "next", "");
        }
        if (index > 0) {
            c.set("#Back.Visible", true);
            c.set("#Back.Text", Messages.get(UiText.BACK));
            Body.on(events, "#Back", "back", "");
        }
        if (!last) {
            c.set("#Skip.Visible", true);
            c.set("#Skip.Text", Messages.get(UiText.SKIP));
            Body.on(events, "#Skip", "skip", "");
        }
        if (reply != null) {
            c.set("#Reply.Visible", true);
            c.set("#Reply.Text", CommandNames.localize(reply));
            c.set("#Reply.Style.TextColor", replyGood ? GREEN : safeColor(Msg.BAD, "#ff6b6b"));
        }
    }

    private void heading(UICommandBuilder c, String titleId) {
        c.set("#Heading.Text", Messages.get(titleId));
    }

    /** A sentence with the live command root filled in. Extra pairs fill the other placeholders. */
    private static String t(String id, Object... pairs) {
        Object[] all = new Object[pairs.length + 2];
        System.arraycopy(pairs, 0, all, 0, pairs.length);
        all[pairs.length] = "root";
        all[pairs.length + 1] = CommandNames.root();
        return Messages.get(id, all);
    }

    /** The command as a player types it, with the registered root. */
    static String cmd(String rest) {
        return "/" + CommandNames.root() + (rest == null || rest.isEmpty() ? "" : " " + rest);
    }

    private static void code(Body body, String command, String descId) {
        String s = body.add(CODE);
        body.commands().set(s + " #Command.Text", command);
        body.commands().set(s + " #Desc.Text", descId == null ? "" : Messages.get(descId));
    }

    private static void good(Body body, String text) {
        body.text(text, GREEN);
    }

    private Act found(UICommandBuilder c, Body body, Faction f, boolean[] done) {
        heading(c, UiText.TUTORIAL_FOUND_TITLE);
        body.text(t(UiText.TUTORIAL_FOUND_TEXT));
        if (f != null) {
            done[0] = true;
            good(body, t(UiText.TUTORIAL_FOUND_DONE, "faction", f.name()));
            code(body, cmd("create <tag> <name>"), UiText.TUTORIAL_CMD_CREATE);
            code(body, cmd("join <tag>"), UiText.TUTORIAL_CMD_JOIN);
            return null;
        }
        Settings s = ctx.settings();
        body.text(t(UiText.TUTORIAL_FOUND_LIMITS, "tagMin", s.integer("faction.tagMin"), "tagMax", s.integer("faction.tagMax"),
                "nameMax", s.integer("faction.nameMax")), Msg.MUTED);
        String form = body.form(new String[] {Messages.get(UiText.TUTORIAL_TAG), Messages.get(UiText.TUTORIAL_NAME)});
        code(body, cmd("create <tag> <name>"), UiText.TUTORIAL_CMD_CREATE);
        code(body, cmd("join <tag>"), UiText.TUTORIAL_CMD_JOIN);

        List<Faction> joinable = new ArrayList<>();
        long now = Time.now();
        for (Faction other : factions().all()) {
            if (!other.isSystem() && (other.setting("open", false) || other.hasValidInvite(uuid(), now))) {
                joinable.add(other);
            }
        }
        if (!joinable.isEmpty()) {
            body.section(Messages.get(UiText.TUTORIAL_FOUND_SECTION));
            body.text(t(UiText.TUTORIAL_FOUND_JOIN), Msg.MUTED);
            joinable.sort(Comparator.comparingInt(Faction::memberCount).reversed());
            for (Faction other : joinable.subList(0, Math.min(JOIN_ROWS, joinable.size()))) {
                String state = other.hasValidInvite(uuid(), now) ? Messages.get(UiText.INVITED) : Messages.get(UiText.STATE_OPEN);
                body.row(other.name(), safeColor(other.color(), null),
                        new String[] {Messages.get(UiText.MEMBERS_COUNT, "count", other.memberCount()), state, "", ""},
                        Btn.of(Messages.get(UiText.JOIN), "join", other.tag()));
            }
        }
        return new Act(Messages.get(UiText.TUTORIAL_FOUND_ACTION), "create", form + " #I1", form + " #I2");
    }

    private Act claim(UICommandBuilder c, Body body, Faction f, boolean[] done) {
        heading(c, UiText.TUTORIAL_CLAIM_TITLE);
        body.text(t(UiText.TUTORIAL_CLAIM_TEXT));
        body.text(t(UiText.TUTORIAL_CLAIM_POWER, "radius", radiusExample()), Msg.MUTED);
        code(body, cmd("claim"), UiText.TUTORIAL_CMD_CLAIM);
        code(body, cmd("claim " + radiusExample()), UiText.TUTORIAL_CMD_CLAIM_RADIUS);
        code(body, cmd("unclaim"), UiText.TUTORIAL_CMD_UNCLAIM);
        if (f == null) {
            body.text(t(UiText.TUTORIAL_NEED_FACTION), Msg.MUTED);
            return null;
        }
        ClaimService claims = ctx.service(ClaimService.class);
        int count = claims.claimCount(f);
        String status = t(UiText.TUTORIAL_CLAIM_STATUS, "claims", count, "max", claims.maxClaims(f));
        if (count > 0) {
            done[0] = true;
            good(body, status);
            return null;
        }
        body.text(status, Msg.MUTED);
        return new Act(Messages.get(UiText.TUTORIAL_CLAIM_ACTION), "claim");
    }

    /** The radius the sentence uses as an example, five as in the classic command, never above land.maxRadiusClaim. */
    private int radiusExample() {
        return Math.max(1, Math.min(5, ctx.settings().integer("land.maxRadiusClaim")));
    }

    private Act map(UICommandBuilder c, Body body) {
        heading(c, UiText.TUTORIAL_MAP_TITLE);
        body.text(t(UiText.TUTORIAL_MAP_TEXT));
        if (ctx.settings().bool("map.overlay") || ctx.settings().bool("map.markers")) {
            body.text(t(UiText.TUTORIAL_MAP_WORLD));
        }
        code(body, cmd("map"), UiText.TUTORIAL_CMD_MAP);
        code(body, cmd("map 3"), UiText.TUTORIAL_CMD_MAP_BIG);
        return new Act(Messages.get(UiText.TUTORIAL_MAP_ACTION), "map");
    }

    private Act home(UICommandBuilder c, Body body, Faction f, boolean[] done) {
        heading(c, UiText.TUTORIAL_HOME_TITLE);
        body.text(t(UiText.TUTORIAL_HOME_TEXT));
        int warmup = ctx.settings().integer("home.warmupSeconds");
        if (warmup > 0) {
            body.text(t(UiText.TUTORIAL_HOME_WAIT, "seconds", warmup), Msg.MUTED);
        }
        code(body, cmd("sethome"), UiText.TUTORIAL_CMD_SETHOME);
        code(body, cmd("home"), UiText.TUTORIAL_CMD_HOME);
        if (f == null) {
            body.text(t(UiText.TUTORIAL_NEED_FACTION), Msg.MUTED);
            return null;
        }
        if (f.home() != null) {
            done[0] = true;
            good(body, t(UiText.TUTORIAL_HOME_DONE));
            return null;
        }
        return new Act(Messages.get(UiText.TUTORIAL_HOME_ACTION), "sethome");
    }

    private Act invite(UICommandBuilder c, Body body, Faction f, boolean[] done) {
        heading(c, UiText.TUTORIAL_INVITE_TITLE);
        body.text(t(UiText.TUTORIAL_INVITE_TEXT, "tag", f == null ? "<tag>" : f.tag()));
        body.text(t(UiText.TUTORIAL_INVITE_ROSTER), Msg.MUTED);
        if (f == null) {
            code(body, cmd("invite <player>"), UiText.TUTORIAL_CMD_INVITE);
            code(body, cmd("who <tag>"), UiText.TUTORIAL_CMD_WHO);
            body.text(t(UiText.TUTORIAL_NEED_FACTION), Msg.MUTED);
            return null;
        }
        if (f.memberCount() > 1) {
            done[0] = true;
            good(body, t(UiText.TUTORIAL_INVITE_DONE, "count", f.memberCount()));
            code(body, cmd("invite <player>"), UiText.TUTORIAL_CMD_INVITE);
            code(body, cmd("who <tag>"), UiText.TUTORIAL_CMD_WHO);
            return null;
        }
        String form = body.form(new String[] {Messages.get(UiText.TUTORIAL_PLAYER)});
        code(body, cmd("invite <player>"), UiText.TUTORIAL_CMD_INVITE);
        code(body, cmd("who <tag>"), UiText.TUTORIAL_CMD_WHO);
        return new Act(Messages.get(UiText.TUTORIAL_INVITE_ACTION), "invite", form + " #I1");
    }

    private Act relations(UICommandBuilder c, Body body) {
        heading(c, UiText.TUTORIAL_RELATIONS_TITLE);
        body.text(t(UiText.TUTORIAL_RELATIONS_TEXT));
        body.text(t(UiText.TUTORIAL_RELATIONS_MORE), Msg.MUTED);
        code(body, cmd("ally <tag>"), UiText.TUTORIAL_CMD_ALLY);
        code(body, cmd("truce <tag>"), UiText.TUTORIAL_CMD_TRUCE);
        code(body, cmd("enemy <tag>"), UiText.TUTORIAL_CMD_ENEMY);
        code(body, cmd("neutral <tag>"), UiText.TUTORIAL_CMD_NEUTRAL);
        code(body, cmd("relations"), UiText.TUTORIAL_CMD_RELATIONS);
        return null;
    }

    private Act raids(UICommandBuilder c, Body body) {
        heading(c, UiText.TUTORIAL_RAIDS_TITLE);
        body.text(t(UiText.TUTORIAL_RAIDS_POWER));
        Settings s = ctx.settings();
        boolean sieges = s.bool("raid.modeSiege") && ctx.serviceOrNull(SiegeService.class) != null && !CommandNames.disabled("siege");
        boolean shields = s.bool("shield.enabled") && ctx.serviceOrNull(ShieldService.class) != null && !CommandNames.disabled("shield");
        if (sieges) {
            body.text(t(UiText.TUTORIAL_RAIDS_SIEGE));
        }
        if (shields) {
            body.text(t(UiText.TUTORIAL_RAIDS_SHIELD, "hours", s.integer("shield.hours")), Msg.MUTED);
        }
        code(body, cmd("power"), UiText.TUTORIAL_CMD_POWER);
        code(body, cmd("raids"), UiText.TUTORIAL_CMD_RAIDS);
        if (sieges) {
            code(body, cmd("siege"), UiText.TUTORIAL_CMD_SIEGE);
        }
        if (shields) {
            code(body, cmd("shield"), UiText.TUTORIAL_CMD_SHIELD);
        }
        return null;
    }

    private Act bank(UICommandBuilder c, Body body, Faction f) {
        heading(c, UiText.TUTORIAL_BANK_TITLE);
        EconomyService eco = economy();
        boolean market = ctx.serviceOrNull(MarketService.class) != null && !CommandNames.disabled("market");
        String form = null;
        if (eco != null) {
            body.text(t(UiText.TUTORIAL_BANK_TEXT));
            if ("coinage".equals(eco.providerName())) {
                body.text(t(UiText.TUTORIAL_BANK_COINAGE), Msg.MUTED);
            }
        }
        if (market) {
            body.text(t(UiText.TUTORIAL_BANK_MARKET));
        }
        if (eco != null) {
            if (f == null) {
                body.text(t(UiText.TUTORIAL_NEED_FACTION), Msg.MUTED);
            } else {
                form = body.form(new String[] {Messages.get(UiText.TUTORIAL_AMOUNT)});
            }
            code(body, cmd("bank"), UiText.TUTORIAL_CMD_BANK);
            code(body, cmd("deposit <amount>"), UiText.TUTORIAL_CMD_DEPOSIT);
            code(body, cmd("withdraw <amount>"), UiText.TUTORIAL_CMD_WITHDRAW);
        }
        if (market) {
            code(body, cmd("market"), UiText.TUTORIAL_CMD_MARKET);
        }
        return form == null ? null : new Act(Messages.get(UiText.TUTORIAL_BANK_ACTION), "deposit", form + " #I1");
    }

    private Act chat(UICommandBuilder c, Body body) {
        heading(c, UiText.TUTORIAL_CHAT_TITLE);
        Settings s = ctx.settings();
        body.text(t(UiText.TUTORIAL_CHAT_TEXT, "faction", s.text("chat.factionPrefix"), "ally", s.text("chat.allyPrefix")));
        body.text(t(UiText.TUTORIAL_CHAT_CHANNEL), Msg.MUTED);
        if (s.bool("chat.showTag")) {
            body.text(t(UiText.TUTORIAL_CHAT_TAG), Msg.MUTED);
        }
        code(body, s.text("chat.factionPrefix") + "hello", null);
        code(body, cmd("chat"), UiText.TUTORIAL_CMD_CHAT);
        code(body, cmd("announce <text>"), UiText.TUTORIAL_CMD_ANNOUNCE);
        return null;
    }

    private Act commands(UICommandBuilder c, Body body) {
        heading(c, UiText.TUTORIAL_COMMANDS_TITLE);
        body.text(t(UiText.TUTORIAL_COMMANDS_TEXT));
        code(body, cmd(""), UiText.TUTORIAL_CMD_PANEL);
        code(body, cmd("claim"), UiText.TUTORIAL_CMD_CLAIM);
        if (available(MAP)) {
            code(body, cmd("map"), UiText.TUTORIAL_CMD_MAP);
        }
        if (available(HOME)) {
            code(body, cmd("home"), UiText.TUTORIAL_CMD_HOME);
        }
        code(body, cmd("invite <player>"), UiText.TUTORIAL_CMD_INVITE);
        code(body, cmd("who <tag>"), UiText.TUTORIAL_CMD_WHO);
        code(body, cmd("power"), UiText.TUTORIAL_CMD_POWER);
        if (available(CHAT)) {
            code(body, cmd("chat"), UiText.TUTORIAL_CMD_CHAT);
        }
        if (economy() != null) {
            code(body, cmd("deposit <amount>"), UiText.TUTORIAL_CMD_DEPOSIT);
        }
        code(body, cmd("help"), UiText.TUTORIAL_CMD_HELP);
        body.text(t(UiText.TUTORIAL_COMMANDS_HELP), Msg.MUTED);
        return null;
    }

    // ---- actions -------------------------------------------------------------

    @Override
    protected boolean onAction(PageEvent e) {
        List<String> steps = steps();
        int index = indexFor(steps, current);
        switch (e.action) {
            case "next" -> {
                current = steps.get(Math.min(steps.size() - 1, index + 1));
                reply = null;
                return true;
            }
            case "back" -> {
                current = steps.get(Math.max(0, index - 1));
                reply = null;
                return true;
            }
            case "skip", "finish" -> {
                TutorialCredit.finished(ctx, ctx.player(uuid()));
                if ("skip".equals(e.action)) {
                    Msg.send(player(), Msg.muted(t(UiText.TUTORIAL_SKIPPED)));
                }
                closePage();
                return false;
            }
            default -> {
                try {
                    act(e);
                } catch (Exception ex) {
                    ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion tutorial action %s failed", e.action);
                    show(Result.fail(Messages.get(UiText.BUTTON_FAILED)));
                }
                return true;
            }
        }
    }

    /** Runs a step button on the player's world thread, where page events arrive, and keeps the reply for the page. */
    private void act(PageEvent e) {
        String feature = DominionPanel.switchOf(e.action);
        boolean mapOff = "map".equals(e.action) && !ctx.settings().bool("map.enabled");
        if (CommandNames.disabled(e.action) || mapOff || (feature != null && !ctx.settings().bool(feature))) {
            show(Result.fail(Messages.get(CommonText.FEATURE_OFF)));
            return;
        }
        String text = e.text();
        switch (e.action) {
            case "create" -> {
                if (text.isEmpty()) {
                    show(Result.fail(Messages.get(UiText.FACTION_TAG_NEEDED)));
                    return;
                }
                String name = e.text2().isEmpty() ? text : e.text2();
                show(factions().create(uuid(), username(), text, name));
            }
            case "join" -> show(factions().join(uuid(), username(), e.value()));
            case "claim" -> {
                String world = Players.worldName(player());
                long chunk = Players.chunkOf(player());
                show(world == null || chunk == Long.MIN_VALUE ? Result.fail(Messages.get(UiText.POSITION_UNKNOWN_RETRY))
                        : ctx.service(ClaimService.class).claim(uuid(), world, chunk));
            }
            case "map" -> {
                List<Message> rows = ctx.service(ClaimService.class).chatMap(uuid(), 21, 9);
                if (rows == null || rows.isEmpty()) {
                    show(Result.fail(Messages.get(UiText.POSITION_UNKNOWN_RETRY)));
                    return;
                }
                for (Message row : rows) {
                    player().sendMessage(row);
                }
                show(Result.pass(Messages.get(UiText.TUTORIAL_MAP_SENT)));
            }
            case "sethome" -> show(ctx.service(HomeService.class).setHome(uuid()));
            case "invite" -> {
                UUID target = resolvePlayer(text);
                if (target == null) {
                    show(Result.fail(text.isEmpty() ? Messages.get(UiText.PLAYER_NAME_NEEDED)
                            : Messages.get(UiText.NO_PLAYER_NAMED, "name", text)));
                    return;
                }
                PlayerData other = ctx.repo().playerIfLoaded(target);
                show(factions().invite(uuid(), target, other == null || other.name().isBlank() ? text : other.name()));
            }
            case "deposit" -> {
                EconomyService eco = economy();
                double amount = DominionPanel.parseAmount(text);
                if (eco == null) {
                    show(Result.fail(Messages.get(UiText.ECONOMY_OFF)));
                } else if (amount <= 0) {
                    show(Result.fail(Messages.get(UiText.POSITIVE_AMOUNT_FIRST)));
                } else {
                    show(eco.bankDeposit(uuid(), amount));
                }
            }
            default -> { }
        }
    }

    private void show(Result r) {
        if (r == null) {
            return;
        }
        replyGood = r.success();
        reply = r.message() == null || r.message().isBlank()
                ? Messages.get(r.success() ? UiText.DONE : UiText.DID_NOT_WORK)
                : r.message();
    }
}
