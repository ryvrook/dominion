package dev.ryvrook.dominion.service;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.PlayerData;

import java.util.UUID;

/** Channels, tags and faction wide messages. */
public interface ChatService {
    Result setChannel(UUID player, PlayerData.Channel channel);

    PlayerData.Channel cycleChannel(UUID player);

    /** Sends to every online member, optionally only officers. */
    void sendToFaction(Faction faction, Message message, boolean officersOnly);

    void sendToAllies(Faction faction, Message message);

    /** Tag coloured for the viewer's relation to the sender. */
    Message tagFor(UUID viewer, UUID sender);

    Result announce(UUID actor, String text);

    /** Alert respecting each member's alert toggle. */
    void alert(Faction faction, Message message);

    /**
     * A faction chat line from outside the game, such as a Discord relay, sent to the online members under the
     * given name and published as a ChatLine with a null sender.
     */
    Result sendExternal(Faction faction, String name, String text);
}
