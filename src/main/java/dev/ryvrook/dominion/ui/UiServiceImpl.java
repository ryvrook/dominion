package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.chat.ChatServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Opens Dominion pages, drives the sidebar HUD and shows territory banners. */
public final class UiServiceImpl implements UiService {
    /** Item whose icon decorates territory banners. Exists in the vanilla item list. The default of ui.bannerIcon. */
    static final String BANNER_ICON = "Furniture_Flag_Small_Red";

    private String bannerIcon() {
        String id = ctx.settings().text("ui.bannerIcon").trim();
        return id.isEmpty() ? BANNER_ICON : id;
    }

    private final DominionContext ctx;
    private final Map<UUID, DominionHud> huds = new ConcurrentHashMap<>();
    /** The last panel opened per player, kept so live labels can refresh while it stays open. */
    private final Map<UUID, DominionPanel> panels = new ConcurrentHashMap<>();
    private int hudTicks;
    /** hud.side and hud.top as the open sidebars were built with them. */
    private String hudPlacement;

    public UiServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    // ---- pages -----------------------------------------------------------

    @Override
    public void openPanel(PlayerRef player, String tab) {
        open(player, new DominionPanel(player, ctx, this, tab));
    }

    @Override
    public void openPermissions(PlayerRef player) {
        open(player, new PermissionsPage(player, ctx, this));
    }

    /** Opens the permission grid of one zone of the player's faction. */
    public void openZonePerms(PlayerRef player, String zoneId) {
        open(player, new ZonePermsPage(player, ctx, this, zoneId));
    }

    @Override
    public void openFactionInfo(PlayerRef player, String tag) {
        open(player, new FactionInfoPage(player, ctx, this, tag));
    }

    @Override
    public void openTop(PlayerRef player) {
        if (!ctx.settings().bool("top.enabled")) {
            Msg.send(player, Msg.bad(Messages.get(CommonText.FEATURE_OFF)));
            return;
        }
        open(player, new TopPage(player, ctx, this));
    }

    @Override
    public void openTutorial(PlayerRef player) {
        open(player, new TutorialPage(player, ctx, this));
    }

    @Override
    public void openAdminSettings(PlayerRef player, String group) {
        open(player, new AdminSettingsPage(player, ctx, this, group));
    }

    @Override
    public void confirm(PlayerRef player, String title, String consequence, Consumer<Boolean> onDecision) {
        open(player, new ConfirmPage(player, ctx, this, title, consequence, onDecision));
    }

    /** Opens a page on the player's world thread. */
    void open(PlayerRef player, CustomUIPage page) {
        onWorldThread(player, (p, ref, store) -> {
            p.getPageManager().openCustomPage(ref, store, page);
            if (page instanceof DominionPanel panel) {
                panels.put(player.getUuid(), panel);
            } else {
                panels.remove(player.getUuid());
            }
        });
    }

    /** Called every second. Refreshes live labels, such as event countdowns, on open panels. */
    void tickPanels() {
        for (Map.Entry<UUID, DominionPanel> e : panels.entrySet()) {
            DominionPanel panel = e.getValue();
            if (!panel.hasLive()) {
                continue;
            }
            PlayerRef player = Players.online(e.getKey());
            if (player == null) {
                panels.remove(e.getKey(), panel);
                continue;
            }
            World world = Players.world(player);
            if (world == null) {
                continue;
            }
            world.execute(() -> {
                try {
                    if (isShowing(player, panel)) {
                        panel.pushLive();
                    } else {
                        panels.remove(player.getUuid(), panel);
                    }
                } catch (Exception ex) {
                    ctx.logger().at(Level.FINE).withCause(ex).log("Dominion live panel update failed");
                }
            });
        }
    }

    /** True when this page is still the player's open custom page. Call on the world thread. */
    boolean isShowing(PlayerRef player, CustomUIPage page) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Player p = ref.getStore().getComponent(ref, Player.getComponentType());
        return p != null && p.getPageManager().getCustomPage() == page;
    }

    interface PlayerTask {
        void run(Player player, Ref<EntityStore> ref, Store<EntityStore> store);
    }

    void onWorldThread(PlayerRef player, PlayerTask task) {
        World world = Players.world(player);
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    task.run(p, ref, store);
                }
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion UI task failed for %s", player.getUsername());
            }
        });
    }

    // ---- HUD -------------------------------------------------------------

    /**
     * Shows the sidebar to players who have it on. New players start with hud.enabledDefault, so
     * the server default only decides the first value and each player's own choice wins after that.
     */
    void onPlayerReady(PlayerRef player) {
        if (ctx.settings().bool("hud.enabled") && ctx.player(player.getUuid()).hud()) {
            showHud(player);
        }
    }

    void onPlayerQuit(UUID uuid) {
        huds.remove(uuid);
        panels.remove(uuid);
    }

    /** Called every second. Refreshes visible sidebars every hud.refreshSeconds. */
    void tickHud() {
        // With hud.enabled off every open sidebar is taken down and nothing refreshes. Each player's own
        // choice is kept, so the sidebars come back on their next join once the switch is on again.
        if (!ctx.settings().bool("hud.enabled")) {
            for (UUID id : new ArrayList<>(huds.keySet())) {
                PlayerRef player = Players.online(id);
                if (player != null) {
                    hideHud(player);
                } else {
                    huds.remove(id);
                }
            }
            return;
        }
        // The position is part of the markup, so a changed hud.side or hud.top builds the open sidebars again.
        String placement = ctx.settings().text("hud.side") + "/" + ctx.settings().integer("hud.top");
        if (hudPlacement != null && !hudPlacement.equals(placement)) {
            for (UUID id : new ArrayList<>(huds.keySet())) {
                PlayerRef player = Players.online(id);
                if (player != null) {
                    showHud(player);
                }
            }
        }
        hudPlacement = placement;
        int every = Math.max(1, ctx.settings().integer("hud.refreshSeconds"));
        if (++hudTicks % every != 0) {
            return;
        }
        for (Map.Entry<UUID, DominionHud> e : huds.entrySet()) {
            PlayerRef player = Players.online(e.getKey());
            if (player == null) {
                huds.remove(e.getKey());
                continue;
            }
            PlayerData data = ctx.repo().playerIfLoaded(e.getKey());
            if (data == null || !data.hud()) {
                continue;
            }
            refreshOnWorld(player, e.getValue());
        }
    }

    /** Refreshes on the player's world thread, skipping a sidebar that was hidden or replaced meanwhile. */
    private void refreshOnWorld(PlayerRef player, DominionHud hud) {
        World world = Players.world(player);
        if (world == null || hud == null) {
            return;
        }
        UUID id = player.getUuid();
        world.execute(() -> {
            if (huds.get(id) == hud) {
                hud.refresh();
            }
        });
    }

    private void showHud(PlayerRef player) {
        onWorldThread(player, (p, ref, store) -> {
            DominionHud old = huds.remove(player.getUuid());
            if (old != null) {
                p.getHudManager().removeCustomHud(player, DominionHud.KEY);
            }
            DominionHud hud = new DominionHud(player, ctx);
            p.getHudManager().addCustomHud(player, hud);
            huds.put(player.getUuid(), hud);
        });
    }

    private void hideHud(PlayerRef player) {
        onWorldThread(player, (p, ref, store) -> {
            huds.remove(player.getUuid());
            p.getHudManager().removeCustomHud(player, DominionHud.KEY);
        });
    }

    @Override
    public void refreshHud(PlayerRef player) {
        if (player == null) {
            return;
        }
        refreshOnWorld(player, huds.get(player.getUuid()));
    }

    @Override
    public void setHudVisible(PlayerRef player, boolean visible) {
        if (player == null) {
            return;
        }
        ctx.player(player.getUuid()).setHud(visible);
        if (visible && !ctx.settings().bool("hud.enabled")) {
            return;
        }
        if (visible) {
            showHud(player);
        } else {
            hideHud(player);
        }
    }

    // ---- banners ---------------------------------------------------------

    @Override
    public void territoryBanner(PlayerRef player, String title, String subtitle, String color) {
        if (player == null) {
            return;
        }
        String c = DominionPage.safeColor(color, Msg.TEXT);
        try {
            NotificationUtil.sendNotification(player.getPacketHandler(),
                    Message.raw(title == null ? "" : title).color(c).bold(true),
                    bannerSubtitle(player, title, subtitle),
                    new ItemStack(bannerIcon(), 1).toPacket(),
                    NotificationStyle.Default);
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion territory banner failed");
        }
    }

    /**
     * Prefixes the subtitle with the owner's tag in the viewer's relation colour and a short
     * relation phrase. The owner is the faction at the player's chunk, used only when its name
     * matches the banner title so a stale banner never shows the wrong tag.
     */
    private Message bannerSubtitle(PlayerRef player, String title, String subtitle) {
        Message plain = Message.raw(subtitle == null ? "" : subtitle).color(Msg.MUTED);
        try {
            String world = Players.worldName(player);
            long chunk = Players.chunkOf(player);
            ClaimService claims = ctx.serviceOrNull(ClaimService.class);
            if (claims == null || world == null || chunk == Long.MIN_VALUE) {
                return plain;
            }
            Faction owner = claims.ownerAt(world, chunk);
            if (owner == null || owner.isSystem() || title == null || !title.equals(owner.name())) {
                return plain;
            }
            UUID viewer = player.getUuid();
            Relation rel = ctx.service(RelationService.class).playerToFaction(viewer, owner);
            if (rel == null) {
                rel = Relation.NEUTRAL;
            }
            ChatService chat = ctx.serviceOrNull(ChatService.class);
            Message tag = chat instanceof ChatServiceImpl impl ? impl.factionTag(viewer, owner)
                    : Message.raw(owner.tag() + " ").color(rel.color());
            String phrase = switch (rel) {
                case OWN -> Messages.get(UiText.BANNER_OWN);
                case ALLY -> Messages.get(UiText.BANNER_ALLY);
                case TRUCE -> Messages.get(UiText.BANNER_TRUCE);
                case ENEMY -> Messages.get(UiText.BANNER_ENEMY);
                default -> Messages.get(UiText.BANNER_NEUTRAL);
            };
            String rest = subtitle == null || subtitle.isBlank() ? "" : "  " + subtitle;
            return Message.join(tag, Message.raw(phrase).color(rel.color()), Message.raw(rest).color(Msg.MUTED));
        } catch (RuntimeException e) {
            return plain;
        }
    }
}
