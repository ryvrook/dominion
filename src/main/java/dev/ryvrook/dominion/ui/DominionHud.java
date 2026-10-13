package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
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
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

import java.util.List;

import java.util.logging.Level;

/** Right side faction sidebar. Built once with inline markup, then refreshed by label updates. */
public final class DominionHud extends CustomUIHud {
    public static final String KEY = "DominionHud";

    private static final String MARKUP = """
            Group {
              Anchor: (Right: 16, Top: 200, Width: 280, Height: 302);
              LayoutMode: Top;
              Padding: (Horizontal: 12, Vertical: 10);
              Background: #000000(0.4);

              Label #DomTitle {
                Anchor: (Height: 28);
                Style: (FontSize: 18, TextColor: #7fd6ff, RenderBold: true, VerticalAlignment: Center);
              }

              Label #DomRank {
                Anchor: (Height: 20, Bottom: 6);
                Style: (FontSize: 13, TextColor: #96a9be, RenderUppercase: true, RenderBold: true, VerticalAlignment: Center);
              }

              Label #DomPower {
                Anchor: (Height: 22);
                Style: (FontSize: 14, TextColor: #d3d6db, VerticalAlignment: Center);
              }

              Label #DomFactionPower {
                Anchor: (Height: 22);
                Style: (FontSize: 14, TextColor: #d3d6db, VerticalAlignment: Center);
              }

              Label #DomClaims {
                Anchor: (Height: 22);
                Style: (FontSize: 14, TextColor: #d3d6db, VerticalAlignment: Center);
              }

              Label #DomOnline {
                Anchor: (Height: 22, Bottom: 6);
                Style: (FontSize: 14, TextColor: #d3d6db, VerticalAlignment: Center);
              }

              Label #DomHere {
                Anchor: (Height: 22);
                Style: (FontSize: 14, TextColor: #d3d6db, RenderBold: true, VerticalAlignment: Center);
              }

              Label #DomStatus {
                Anchor: (Height: 22);
                Style: (FontSize: 13, TextColor: #878e9c, VerticalAlignment: Center);
              }

              Label #DomSiege {
                Anchor: (Height: 22);
                Style: (FontSize: 13, TextColor: #ff6b6b, RenderBold: true, VerticalAlignment: Center);
              }

              Label #DomShield {
                Anchor: (Height: 22);
                Style: (FontSize: 13, TextColor: #7fd6ff, VerticalAlignment: Center);
              }

              Label #DomGrace {
                Anchor: (Height: 22);
                Style: (FontSize: 13, TextColor: #7aa7e6, VerticalAlignment: Center);
              }
            }
            """;

    /** Rough character budgets that fit the 256 pixel content width at each font size. */
    private static final int TITLE_CHARS = 24;
    private static final int LINE_CHARS = 34;
    private static final int SMALL_CHARS = 36;

    private final DominionContext ctx;

    public DominionHud(PlayerRef player, DominionContext ctx) {
        super(player, KEY);
        this.ctx = ctx;
    }

    @Override
    protected void build(UICommandBuilder commands) {
        commands.appendInline(null, MARKUP);
        fill(commands);
    }

    /** Pushes fresh values. Must run on the player's world thread. */
    void refresh() {
        UICommandBuilder commands = new UICommandBuilder();
        fill(commands);
        try {
            update(false, commands);
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD update failed");
        }
    }

    private void fill(UICommandBuilder c) {
        try {
            fillUnsafe(c);
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion HUD values unavailable");
        }
    }

    private void fillUnsafe(UICommandBuilder c) {
        PlayerRef player = getPlayerRef();
        var uuid = player.getUuid();
        FactionService fs = ctx.service(FactionService.class);
        PowerService ps = ctx.service(PowerService.class);
        ClaimService cs = ctx.service(ClaimService.class);
        Faction f = fs.factionOf(uuid);

        c.set("#DomPower.Text", "Your power " + Msg.number(ps.powerOf(uuid)) + " / " + Msg.number(ps.maxPowerOf(uuid)));
        if (f == null) {
            c.set("#DomTitle.Text", "No faction");
            c.set("#DomTitle.Style.TextColor", Msg.MUTED);
            c.set("#DomRank.Text", "Use the panel to join or create");
            c.set("#DomFactionPower.Text", "");
            c.set("#DomClaims.Text", "");
            c.set("#DomOnline.Text", "");
        } else {
            c.set("#DomTitle.Text", fit(f.tag() + "  " + f.name(), TITLE_CHARS));
            c.set("#DomTitle.Style.TextColor", DominionPage.safeColor(f.color(), "#7fd6ff"));
            Rank rank = f.rankOf(uuid);
            String nation = nationTag(f);
            String rankText = rank == null ? "" : f.rankName(rank);
            if (nation != null) {
                rankText = rankText.isEmpty() ? "Nation " + nation : rankText + "  of nation " + nation;
            }
            c.set("#DomRank.Text", fit(rankText, SMALL_CHARS));
            c.set("#DomFactionPower.Text", "Faction power " + Msg.number(ps.factionPower(f)) + " / "
                    + Msg.number(ps.factionMaxPower(f)));
            c.set("#DomClaims.Text", "Land " + cs.claimCount(f) + " / " + cs.maxClaims(f) + " chunks");
            int online = 0;
            for (Member m : f.members()) {
                if (Players.isOnline(m.uuid())) {
                    online++;
                }
            }
            c.set("#DomOnline.Text", online + " of " + f.memberCount() + " members online");
        }

        String world = Players.worldName(player);
        long chunk = Players.chunkOf(player);
        Faction here = world == null || chunk == Long.MIN_VALUE ? null : cs.ownerAt(world, chunk);
        if (here == null) {
            c.set("#DomHere.Text", "Wilderness");
            c.set("#DomHere.Style.TextColor", "#5fd37a");
        } else {
            String color;
            String flags = "";
            if (here.type() == FactionType.SAFEZONE) {
                color = "#ffd166";
                flags = "  safe";
            } else if (here.type() == FactionType.WARZONE) {
                color = "#ff6b6b";
                flags = "  war";
            } else {
                Relation rel = ctx.service(RelationService.class).playerToFaction(uuid, here);
                color = rel == null ? Msg.TEXT : rel.color();
                if (cs.raidable(here)) {
                    flags = "  raidable";
                }
                ClaimEntry entry = cs.claimAt(world, chunk);
                if (entry != null && f != null && cs.breached(world, chunk, f.id())) {
                    flags += "  breached";
                }
            }
            c.set("#DomHere.Text", fit(here.tag() + "  " + here.name() + flags, LINE_CHARS));
            c.set("#DomHere.Style.TextColor", color);
        }

        RulesService rules = ctx.serviceOrNull(RulesService.class);
        ShieldService shieldService = ctx.serviceOrNull(ShieldService.class);
        if (rules != null && rules.graceActive()) {
            // One grace line only. The countdown joins the status line and the grace line stays empty.
            long left = shieldService != null && shieldService.graceActive() ? shieldService.graceEndsAt() - Time.now() : -1;
            c.set("#DomStatus.Text", fit(left > 0 ? "Grace ends in " + Time.remaining(left) + ", no raiding" : "Grace period, no raiding", SMALL_CHARS));
            c.set("#DomStatus.Style.TextColor", "#7aa7e6");
        } else if (rules != null && rules.eotwActive()) {
            c.set("#DomStatus.Text", "End of the world, all land raidable");
            c.set("#DomStatus.Style.TextColor", "#ff6b6b");
        } else if (f != null && !f.isSystem() && cs.raidable(f)) {
            c.set("#DomStatus.Text", "Your faction is raidable");
            c.set("#DomStatus.Style.TextColor", "#ff6b6b");
        } else if (f != null) {
            c.set("#DomStatus.Text", "Your land is protected");
            c.set("#DomStatus.Style.TextColor", "#5fd37a");
        } else {
            c.set("#DomStatus.Text", "");
        }
        fillSiegeLines(c, f, rules != null && rules.graceActive());
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

    private String nationTag(Faction f) {
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null || f == null || f.isSystem()) {
            return null;
        }
        try {
            Nation n = nations.nationOf(f);
            return n == null ? null : n.tag();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Siege, shield and grace lines. Reads the active sieges once per refresh. */
    private void fillSiegeLines(UICommandBuilder c, Faction f, boolean graceOnStatus) {
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
                String foeTag = foe == null ? "a former faction" : foe.tag();
                String stage = s.phase() == Siege.Phase.BREACHED ? "breach" : "capture";
                String more = active.size() > 1 ? " +" + (active.size() - 1) + " more" : "";
                String tail = ", " + stage + " " + Time.remaining(s.phaseEndsAt() - now) + more;
                String head = (attacking ? "Sieging " : "Sieged by ") + foeTag;
                // The countdown and count matter most, so the foe tag is shortened first.
                if (head.length() + tail.length() > SMALL_CHARS) {
                    head = fit(head, Math.max(12, SMALL_CHARS - tail.length()));
                }
                siegeText = fit(head + tail, SMALL_CHARS);
            }
        }
        c.set("#DomSiege.Text", siegeText);

        ShieldService shields = ctx.serviceOrNull(ShieldService.class);
        // The shield only covers the core around the home and drops while the faction is raidable.
        boolean shieldUp = shields != null && f != null && !f.isSystem() && f.home() != null && shields.windowActive(f)
                && !ctx.service(ClaimService.class).raidable(f);
        c.set("#DomShield.Text", shieldUp ? "Your core shield is up" : "");
        boolean grace = shields != null && shields.graceActive() && !graceOnStatus;
        c.set("#DomGrace.Text", grace ? fit("Grace ends in " + Time.remaining(shields.graceEndsAt() - now), SMALL_CHARS) : "");
    }
}
