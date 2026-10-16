package dev.ryvrook.dominion.chat;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Experimental faction tag in front of the name above a player's head, behind chat.nameplateTag.
 * The engine keeps that text in the Nameplate component and fills it from DisplayNameComponent
 * only when the component is missing, so Dominion sets it on the world thread when a player enters
 * a world, joins or leaves a faction, or the faction is renamed. The component is saved with the
 * player, so the plain name is put back in onEntityRemove, which the store runs before it copies
 * the components for saving. Plugin stop puts the plain name back for everyone still online.
 */
final class Nameplates extends RefSystem<EntityStore> {
    private final DominionContext ctx;
    /** Players whose nameplate Dominion changed, with the plain name to put back. */
    private final Map<UUID, String> tagged = new ConcurrentHashMap<>();

    Nameplates(DominionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        PlayerRef player = cb.getComponent(ref, PlayerRef.getComponentType());
        EntityStore external = store.getExternalData();
        World world = external == null ? null : external.getWorld();
        if (player != null && world != null) {
            // The engine adds the Nameplate through a command buffer, so wait for the next turn of the world thread.
            world.execute(() -> apply(player, false));
        }
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        PlayerRef player = cb.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        String plain = tagged.remove(player.getUuid());
        Nameplate plate = cb.getComponent(ref, Nameplate.getComponentType());
        if (plain != null && plate != null) {
            plate.setText(plain);
        }
    }

    void onEvent(DominionEvent event) {
        switch (event) {
            case DominionEvent.MemberJoined e -> refresh(e.player());
            case DominionEvent.MemberLeft e -> refresh(e.player());
            case DominionEvent.FactionRenamed e -> {
                FactionService factions = ctx.serviceOrNull(FactionService.class);
                Faction f = factions == null ? null : factions.byId(e.factionId());
                if (f != null) {
                    for (Member m : f.members()) {
                        refresh(m.uuid());
                    }
                }
            }
            default -> { }
        }
    }

    void onPlayerReady(PlayerRef player) {
        World world = Players.world(player);
        if (world != null) {
            world.execute(() -> apply(player, false));
        }
    }

    /** Puts the plain name back for every player Dominion tagged. Called when the plugin stops. */
    void restoreAll() {
        for (UUID id : new ArrayList<>(tagged.keySet())) {
            PlayerRef player = Players.online(id);
            World world = Players.world(player);
            if (world == null) {
                tagged.remove(id);
                continue;
            }
            try {
                world.execute(() -> apply(player, true));
            } catch (RuntimeException e) {
                ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not restore a nameplate");
            }
        }
    }

    private void refresh(UUID id) {
        PlayerRef player = Players.online(id);
        World world = Players.world(player);
        if (world != null) {
            world.execute(() -> apply(player, false));
        }
    }

    /** Runs on the player's world thread. Sets or clears the tag on the nameplate. */
    private void apply(PlayerRef player, boolean restore) {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Nameplate plate = store.getComponent(ref, Nameplate.getComponentType());
            if (plate == null) {
                return;
            }
            UUID id = player.getUuid();
            String plain = plainName(store, ref, player);
            FactionService factions = ctx.serviceOrNull(FactionService.class);
            Faction f = factions == null ? null : factions.factionOf(id);
            boolean on = !restore && ctx.settings().bool("chat.nameplateTag") && f != null && !f.isSystem();
            if (on) {
                String text = text(f.tag(), plain);
                plate.setText(text);
                tagged.put(id, plain);
                return;
            }
            boolean ours = tagged.remove(id) != null;
            // A tag saved by an earlier run, for example after the setting was turned off, the player
            // left the faction or the faction was renamed while they were away, is taken off too.
            boolean stale = taggedText(plate.getText(), plain);
            if (ours || stale) {
                plate.setText(plain);
            }
        } catch (RuntimeException e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not update a nameplate");
        }
    }

    /** The name the engine itself would show, from DisplayNameComponent, else the username. */
    private static String plainName(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
        DisplayNameComponent display = store.getComponent(ref, DisplayNameComponent.getComponentType());
        if (display != null && display.getDisplayName() != null) {
            String s = display.getDisplayName().getAnsiMessage();
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return player.getUsername();
    }

    /** True when text is plain with a bracketed tag of any faction in front, as {@link #text} writes it. */
    static boolean taggedText(String text, String plain) {
        if (text == null || plain == null || !text.startsWith("[") || !text.endsWith("] " + plain)) {
            return false;
        }
        String tag = text.substring(1, text.length() - plain.length() - 2);
        return !tag.isEmpty() && tag.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c));
    }

    /** Nameplate text with the bracketed tag in front. */
    static String text(String tag, String name) {
        return Faction.bracketed(tag) + " " + name;
    }
}
