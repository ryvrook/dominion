package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.text.Palette;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.List;
import java.util.Locale;

import java.util.logging.Level;

/**
 * Faction sidebar in the suite HUD style. A thin title bar sits above rows that each carry their
 * own background, so a hidden row leaves no empty box. Built once with inline markup, then
 * refreshed by label, visibility and colour updates.
 */
public final class DominionHud extends CustomUIHud {
    public static final String KEY = "DominionHud";

    static final int WIDTH = 250;
    static final int HEIGHT = 250;

    private static final String ROW_LABEL = "Style: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center);";
    private static final String ROW_VALUE = "Style: (FontSize: 13, TextColor: #d3d6db, RenderBold: true, "
            + "HorizontalAlignment: End, VerticalAlignment: Center);";

    /** The anchor is filled in from hud.side and hud.top when the HUD is built. */
    private static final String MARKUP = """
            Group #DomHud {
              Anchor: (%s: 20, Top: %d, Width: %d, Height: %d);
              LayoutMode: Top;

              Group #DomTitleBar {
                Anchor: (Height: 22);
                Padding: (Horizontal: 8);
                Background: #000000(0.5);
                LayoutMode: Left;

                Label #DomTag {
                  Visible: false;
                  Anchor: (Right: 5);
                  Style: (FontSize: 12, TextColor: #7fd6ff, RenderBold: true, LetterSpacing: 1, VerticalAlignment: Center);
                }

                Label #DomTitle {
                  FlexWeight: 1;
                  Style: (FontSize: 12, TextColor: #d3d6db, RenderBold: true, LetterSpacing: 1, VerticalAlignment: Center);
                }

                Label #DomRank {
                  Anchor: (Width: 84);
                  Style: (FontSize: 10, TextColor: #96a9be, RenderUppercase: true, RenderBold: true, LetterSpacing: 0.5, HorizontalAlignment: End, VerticalAlignment: Center);
                }
              }

              Group #DomHintRow { Visible: false; Anchor: (Height: 20); Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;
                Label #DomHint { FlexWeight: 1; Style: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center); }
              }

            %s
              Group #DomHereRow { Anchor: (Height: 22); Padding: (Right: 8); Background: #000000(0.42); LayoutMode: Left;
                Group #DomHereBar { Anchor: (Width: 4, Right: 6); Background: (Color: #6b7280); }
                Label #DomHere { FlexWeight: 1; Style: (FontSize: 13, TextColor: #8a93a3, RenderBold: true, VerticalAlignment: Center); }
                Label #DomHereFlags { Anchor: (Width: 96); Style: (FontSize: 11, TextColor: #ff6b6b, RenderBold: true, HorizontalAlignment: End, VerticalAlignment: Center); }
              }

              Group #DomStatusRow { Visible: false; Anchor: (Height: 20); Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;
                Label #DomStatus { FlexWeight: 1; Style: (FontSize: 12, TextColor: #878e9c, VerticalAlignment: Center); }
              }

              Group #DomSiegeRow { Visible: false; Anchor: (Height: 20); Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;
                Label #DomSiege { FlexWeight: 1; Style: (FontSize: 12, TextColor: #ff6b6b, RenderBold: true, VerticalAlignment: Center); }
              }

              Group #DomShieldRow { Visible: false; Anchor: (Height: 20); Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;
                Label #DomShield { FlexWeight: 1; Style: (FontSize: 12, TextColor: #7fd6ff, VerticalAlignment: Center); }
              }

              Group #DomGraceRow { Visible: false; Anchor: (Height: 20); Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;
                Label #DomGrace { FlexWeight: 1; Style: (FontSize: 12, TextColor: #7aa7e6, VerticalAlignment: Center); }
              }
            }
            """;

    /** Label and value rows, as id suffix and label text. */
    private static final String[][] STAT_ROWS = {
            {"Power", UiText.HUD_POWER},
            {"FactionPower", UiText.HUD_FACTION_POWER},
            {"Land", UiText.HUD_LAND},
            {"Online", UiText.HUD_ONLINE},
            {"Nation", UiText.HUD_NATION},
    };

    /** Rough character budgets that fit the 250 pixel sidebar at each font size. */
    private static final int TITLE_CHARS = 18;
    private static final int RANK_CHARS = 12;
    private static final int VALUE_CHARS = 16;
    private static final int HERE_CHARS = 17;
    private static final int FLAG_CHARS = 16;
    private static final int SMALL_CHARS = 36;

    static final String WILD_TEXT = "#8a93a3";
    // Refreshed from the colors settings by Palette, so they are not final.
    static volatile String WILD_BAR = Palette.wilderness();
    static volatile String SAFE = Palette.safeZone();
    static volatile String WAR = Palette.warZone();

    static {
        Palette.onChange(() -> {
            WILD_BAR = Palette.wilderness();
            SAFE = Palette.safeZone();
            WAR = Palette.warZone();
        });
    }

    /** The status row colours follow the palette. The literals below are the shipped info, bad and good colours. */
    static String statusColor(String color) {
        if (color == null) {
            return Msg.MUTED;
        }
        return switch (color) {
            case "#7aa7e6" -> Msg.INFO;
            case "#ff6b6b" -> Msg.BAD;
            case "#5fd37a" -> Msg.GOOD;
            default -> color;
        };
    }

    private final DominionContext ctx;

    public DominionHud(PlayerRef player, DominionContext ctx) {
        super(player, KEY);
        this.ctx = ctx;
    }

    /** The sidebar markup for a side and a top offset. Any side but left means right. */
    static String markup(String side, int top) {
        StringBuilder rows = new StringBuilder();
        for (String[] r : STAT_ROWS) {
            rows.append("  Group #Dom").append(r[0]).append("Row { Visible: false; Anchor: (Height: 20); ")
                    .append("Padding: (Horizontal: 8); Background: #000000(0.35); LayoutMode: Left;\n")
                    .append("    Label { FlexWeight: 1; Text: \"").append(Messages.get(r[1]).replace('"', '\'')).append("\"; ").append(ROW_LABEL).append(" }\n")
                    .append("    Label #Dom").append(r[0]).append(" { Anchor: (Width: 140); ").append(ROW_VALUE).append(" }\n")
                    .append("  }\n\n");
        }
        String edge = "left".equalsIgnoreCase(side == null ? "" : side.trim()) ? "Left" : "Right";
        return String.format(Locale.ROOT, MARKUP, edge, Math.max(0, top), WIDTH, HEIGHT, rows);
    }

    /**
     * The values of one fill, by selector. A refresh compares them with what the client was last sent and sends only
     * the changes, so a sidebar where nothing moved costs no packet at all.
     */
    static final class Values {
        /** A bar colour, kept as its hex so two fills compare equal. */
        record Patch(String hex) {}

        final java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();

        void set(String selector, String value) {
            map.put(selector, value == null ? "" : value);
        }

        void set(String selector, boolean value) {
            map.put(selector, value);
        }

        void patch(String selector, String hex) {
            map.put(selector, new Patch(hex));
        }

        /** Writes the values that differ from {@code last} and answers how many it wrote. */
        int writeChanges(UICommandBuilder c, java.util.Map<String, Object> last) {
            int n = 0;
            for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
                if (last != null && e.getValue().equals(last.get(e.getKey()))) {
                    continue;
                }
                n++;
                switch (e.getValue()) {
                    case String text -> c.set(e.getKey(), text);
                    case Boolean b -> c.set(e.getKey(), b);
                    case Patch pt -> c.setObject(e.getKey(), DominionHud.patch(pt.hex()));
                    default -> { }
                }
            }
            return n;
        }
    }

    /** Every value is sent again this often, in case the client dropped an update. */
    static final long FULL_EVERY_MILLIS = 30_000L;

    /** What the client was last sent and when everything was. Read and written on the player's world thread only. */
    private java.util.Map<String, Object> sent;
    private long fullAt;

    @Override
    protected void build(UICommandBuilder commands) {
        String side = "right";
        int top = 400;
        try {
            side = ctx.settings().text("hud.side");
            top = ctx.settings().integer("hud.top");
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD position unavailable");
        }
        commands.appendInline(null, markup(side, top));
        Values v = new Values();
        fill(v);
        v.writeChanges(commands, null);
        sent = v.map;
        fullAt = System.currentTimeMillis();
    }

    /** Pushes the values that changed since the last push, nothing when none did. Must run on the player's world thread. */
    void refresh() {
        Values v = new Values();
        fill(v);
        UICommandBuilder commands = new UICommandBuilder();
        long now = System.currentTimeMillis();
        boolean full = now - fullAt >= FULL_EVERY_MILLIS;
        if (v.writeChanges(commands, full ? null : sent) == 0) {
            return;
        }
        try {
            update(false, commands);
            sent = v.map;
            if (full) {
                fullAt = now;
            }
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD update failed");
        }
    }

    /** Each part fills on its own, so one missing service blanks only its own rows. */
    private void fill(Values c) {
        Faction f;
        try {
            f = ctx.service(FactionService.class).factionOf(getPlayerRef().getUuid());
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD values unavailable");
            return;
        }
        safely(() -> fillTitle(c, f), "title");
        safely(() -> fillStats(c, f), "stats");
        safely(() -> fillHere(c, f), "location");
        boolean[] graceOnStatus = {false};
        safely(() -> graceOnStatus[0] = fillStatus(c, f), "status");
        safely(() -> fillSiegeLines(c, f, graceOnStatus[0]), "siege");
    }

    private void safely(Runnable part, String what) {
        try {
            part.run();
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD %s unavailable", what);
        }
    }

    private void fillTitle(Values c, Faction f) {
        if (f == null) {
            c.set("#DomTag.Visible", false);
            c.set("#DomTitle.Text", Messages.get(UiText.HUD_NO_FACTION));
            c.set("#DomTitle.Style.TextColor", Msg.MUTED);
            c.set("#DomRank.Text", "");
            c.set("#DomHint.Text", Messages.get(UiText.HUD_JOIN_HINT));
            c.set("#DomHintRow.Visible", true);
            return;
        }
        c.set("#DomHintRow.Visible", false);
        // The tag reads like a clan tag in the faction colour, then the name in the text colour.
        boolean both = f.tagDiffers();
        String[] title = titleParts(both ? f.tag() : null, both ? f.name() : f.label());
        c.set("#DomTag.Visible", !title[0].isEmpty());
        c.set("#DomTag.Text", title[0]);
        c.set("#DomTag.Style.TextColor", DominionPage.safeColor(f.color(), "#7fd6ff"));
        c.set("#DomTitle.Text", title[1]);
        c.set("#DomTitle.Style.TextColor", title[0].isEmpty() ? DominionPage.safeColor(f.color(), "#7fd6ff") : Msg.TEXT);
        Rank rank = f.rankOf(getPlayerRef().getUuid());
        c.set("#DomRank.Text", rank == null ? "" : fit(f.rankName(rank), RANK_CHARS));
    }

    private void fillStats(Values c, Faction f) {
        var uuid = getPlayerRef().getUuid();
        PowerService ps = ctx.service(PowerService.class);
        c.set("#DomPower.Text", Msg.number(ps.powerOf(uuid)) + " / " + Msg.number(ps.maxPowerOf(uuid)));
        c.set("#DomPowerRow.Visible", true);
        boolean member = f != null;
        c.set("#DomFactionPowerRow.Visible", member);
        c.set("#DomLandRow.Visible", member);
        c.set("#DomOnlineRow.Visible", member);
        String nation = member ? nationLabel(f) : null;
        c.set("#DomNationRow.Visible", nation != null);
        c.set("#DomNation.Text", nation == null ? "" : fit(nation, VALUE_CHARS));
        if (!member) {
            return;
        }
        c.set("#DomFactionPower.Text", Msg.number(ps.factionPower(f)) + " / " + Msg.number(ps.factionMaxPower(f)));
        ClaimService cs = ctx.service(ClaimService.class);
        c.set("#DomLand.Text", Messages.get(UiText.HUD_LAND_VALUE, "claims", cs.claimCount(f), "max", cs.maxClaims(f)));
        int online = 0;
        for (Member m : f.members()) {
            if (Players.isOnline(m.uuid())) {
                online++;
            }
        }
        c.set("#DomOnline.Text", online + " / " + f.memberCount());
    }

    private void fillHere(Values c, Faction f) {
        PlayerRef player = getPlayerRef();
        ClaimService cs = ctx.service(ClaimService.class);
        String world = Players.worldName(player);
        long chunk = Players.chunkOf(player);
        Faction here = world == null || chunk == Long.MIN_VALUE ? null : cs.ownerAt(world, chunk);
        String color;
        String name;
        String flags = "";
        if (here == null) {
            name = Messages.get(UiText.HUD_WILDERNESS);
            color = WILD_TEXT;
            c.patch("#DomHereBar.Background", WILD_BAR);
        } else {
            name = here.label();
            if (here.type() == FactionType.SAFEZONE) {
                color = SAFE;
                flags = Messages.get(UiText.HUD_FLAG_SAFE);
            } else if (here.type() == FactionType.WARZONE) {
                color = WAR;
                flags = Messages.get(UiText.HUD_FLAG_WAR);
            } else {
                Relation rel = ctx.service(RelationService.class).playerToFaction(player.getUuid(), here);
                color = rel == null ? Msg.TEXT : rel.color();
                if (cs.raidable(here)) {
                    flags = Messages.get(UiText.HUD_FLAG_RAIDABLE);
                }
                ClaimEntry entry = cs.claimAt(world, chunk);
                if (entry != null && f != null && cs.breached(world, chunk, f.id())) {
                    String breached = Messages.get(UiText.HUD_FLAG_BREACHED);
                    flags = flags.isEmpty() ? breached : flags + "  " + breached;
                }
            }
            c.patch("#DomHereBar.Background", color);
        }
        c.set("#DomHere.Text", fit(name, flags.isEmpty() ? HERE_CHARS + FLAG_CHARS - 2 : HERE_CHARS));
        c.set("#DomHere.Style.TextColor", color);
        c.set("#DomHereFlags.Text", fit(flags, FLAG_CHARS));
        c.set("#DomHereFlags.Style.TextColor", here != null && here.type() == FactionType.SAFEZONE ? SAFE : WAR);
    }

    /** A plain colour fill, the same form the vanilla world event rows use for their status bar. */
    static PatchStyle patch(String hex) {
        return new PatchStyle().setColor(Value.of(hex));
    }

    /** Fills the status row and answers whether the grace countdown went there. */
    private boolean fillStatus(Values c, Faction f) {
        RulesService rules = ctx.serviceOrNull(RulesService.class);
        ShieldService shieldService = ctx.serviceOrNull(ShieldService.class);
        ClaimService cs = ctx.service(ClaimService.class);
        String text;
        String color;
        boolean grace = rules != null && rules.graceActive();
        if (grace) {
            // One grace line only. The countdown joins the status row and the grace row stays hidden.
            long left = shieldService != null && shieldService.graceActive() ? shieldService.graceEndsAt() - Time.now() : -1;
            text = left > 0 ? Messages.get(UiText.HUD_GRACE_ENDS, "time", Time.remaining(left)) : Messages.get(UiText.HUD_GRACE);
            color = "#7aa7e6";
        } else if (rules != null && rules.eotwActive()) {
            text = Messages.get(UiText.HUD_EOTW);
            color = "#ff6b6b";
        } else if (f != null && !f.isSystem() && cs.raidable(f)) {
            text = Messages.get(UiText.HUD_RAIDABLE);
            color = "#ff6b6b";
        } else if (f != null) {
            text = Messages.get(UiText.HUD_PROTECTED);
            color = "#5fd37a";
        } else {
            text = "";
            color = Msg.MUTED;
        }
        c.set("#DomStatus.Text", fit(text, SMALL_CHARS));
        c.set("#DomStatus.Style.TextColor", statusColor(color));
        c.set("#DomStatusRow.Visible", !text.isEmpty());
        return grace;
    }

    /**
     * The sidebar title as a bracketed tag and a name that share the title budget. A null tag gives an
     * empty tag piece and the whole budget to the name. A long tag keeps room for a few name characters.
     */
    static String[] titleParts(String tag, String name) {
        if (tag == null || tag.isBlank()) {
            return new String[] {"", fit(name, TITLE_CHARS)};
        }
        String t = tag.trim();
        int nameRoom = 6;
        if (t.length() + 2 > TITLE_CHARS - nameRoom) {
            t = fit(t, TITLE_CHARS - nameRoom - 2);
        }
        String piece = Faction.bracketed(t);
        return new String[] {piece, fit(name, TITLE_CHARS - piece.length() - 1)};
    }

    /** Shortens text to a character budget so it never runs past the sidebar edge. */
    static String fit(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, Math.max(1, max - 3)).stripTrailing();
        if (cut.endsWith(",")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private String nationLabel(Faction f) {
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null || f == null || f.isSystem()) {
            return null;
        }
        try {
            Nation n = nations.nationOf(f);
            return n == null ? null : n.label();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Siege, shield and grace lines. Reads the active sieges once per refresh. */
    private void fillSiegeLines(Values c, Faction f, boolean graceOnStatus) {
        long now = Time.now();
        String siegeText = "";
        SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
        if (sieges != null && f != null && !f.isSystem()) {
            List<Siege> active = sieges.activeFor(f);
            if (!active.isEmpty()) {
                Siege s = active.get(0);
                for (Siege other : active) {
                    if (other.phaseEndsAt() < s.phaseEndsAt()) {
                        s = other;
                    }
                }
                boolean attacking = f.id().equals(s.attacker());
                Faction foe = ctx.service(FactionService.class).byId(attacking ? s.defender() : s.attacker());
                String foeTag = foe == null ? Messages.get(UiText.HUD_FORMER_FACTION) : foe.tag();
                String stage = Messages.get(s.phase() == Siege.Phase.BREACHED ? UiText.HUD_STAGE_BREACH : UiText.HUD_STAGE_CAPTURE,
                        "time", Time.remaining(s.phaseEndsAt() - now));
                String more = active.size() > 1 ? " " + Messages.get(UiText.HUD_MORE_SIEGES, "count", active.size() - 1) : "";
                String tail = ", " + stage + more;
                String head = Messages.get(attacking ? UiText.HUD_SIEGING : UiText.HUD_SIEGED_BY, "tag", foeTag);
                // The countdown and count matter most, so the foe tag is shortened first.
                if (head.length() + tail.length() > SMALL_CHARS) {
                    head = fit(head, Math.max(12, SMALL_CHARS - tail.length()));
                }
                siegeText = fit(head + tail, SMALL_CHARS);
            }
        }
        c.set("#DomSiege.Text", siegeText);
        c.set("#DomSiegeRow.Visible", !siegeText.isEmpty());

        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        // The shield only covers the core around the home and drops while the faction is raidable.
        boolean shieldUp = shields != null && f != null && !f.isSystem() && f.home() != null && shields.windowActive(f)
                && !ctx.service(ClaimService.class).raidable(f);
        c.set("#DomShield.Text", shieldUp ? Messages.get(UiText.HUD_SHIELD_UP) : "");
        c.set("#DomShieldRow.Visible", shieldUp);
        boolean grace = shields != null && shields.graceActive() && !graceOnStatus;
        c.set("#DomGrace.Text", grace ? fit(Messages.get(UiText.HUD_GRACE_ENDS_SHORT, "time", Time.remaining(shields.graceEndsAt() - now)), SMALL_CHARS) : "");
        c.set("#DomGraceRow.Visible", grace);
    }
}
