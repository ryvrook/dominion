package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sends a denial reason at most once per player every two seconds. */
final class Denials {
    private static final long GAP_MILLIS = 2000;

    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    void tell(PlayerRef player, String reason) {
        if (player == null || reason == null || reason.isEmpty()) {
            return;
        }
        long now = Time.now();
        Long last = lastSent.get(player.getUuid());
        if (last != null && now - last < GAP_MILLIS) {
            return;
        }
        lastSent.put(player.getUuid(), now);
        player.sendMessage(Msg.bad(reason));
    }
}
