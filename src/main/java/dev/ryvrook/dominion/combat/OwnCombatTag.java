package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.util.Time;

import java.util.UUID;

/** Dominion's own combat tag, set on allowed player hits by {@link PvpFilterSystem}. */
final class OwnCombatTag implements CombatTagSource {
    private final DominionContext ctx;

    OwnCombatTag(DominionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean tagged(UUID player) {
        PlayerData pd = ctx.repo().playerIfLoaded(player);
        return pd != null && pd.combatTagged(Time.now());
    }

    @Override
    public long remainingMillis(UUID player) {
        PlayerData pd = ctx.repo().playerIfLoaded(player);
        return pd == null ? 0L : Math.max(0L, pd.combatTagUntil() - Time.now());
    }
}
