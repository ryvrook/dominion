package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.text.Messages;

import java.util.UUID;

/**
 * The PvP decision table as a pure function so it can be tested without a server. The rules
 * service looks up the zones, factions and relation, then asks this table.
 */
final class PvpRules {
    private PvpRules() {}

    /** The server toggles that shape the table, read live by the caller. */
    record Toggles(boolean pvpFactionless, boolean pvpNeutral, boolean allyFire, boolean truceFire) {}

    /**
     * Decides one hit.
     *
     * @param attackerZone the zone type of the chunk the attacker stands in, or null when unknown or unclaimed
     * @param victimZone the zone type of the chunk the victim stands in, or null when unclaimed
     * @param attackerFaction the attacker's faction id, or null when factionless
     * @param victimFaction the victim's faction id, or null when factionless
     * @param relation the relation between the two factions, only read when both have one and they differ
     * @param friendlyFire whether the attacker's faction allows friendly fire
     */
    static Result decide(boolean self, FactionType attackerZone, FactionType victimZone, UUID attackerFaction,
                         UUID victimFaction, Relation relation, boolean friendlyFire, Toggles t) {
        if (self) {
            return Result.pass();
        }
        if (attackerZone == FactionType.SAFEZONE) {
            return Result.fail(Messages.get(CombatText.PVP_FROM_SAFEZONE));
        }
        if (victimZone == FactionType.SAFEZONE) {
            return Result.fail(Messages.get(CombatText.PVP_IN_SAFEZONE));
        }
        boolean sameFaction = attackerFaction != null && attackerFaction.equals(victimFaction);
        if (victimZone == FactionType.WARZONE) {
            // WarZones always allow fighting, except between members of the same faction.
            return sameFaction ? Result.fail(Messages.get(CombatText.PVP_OWN_IN_WARZONE)) : Result.pass();
        }
        if (attackerFaction == null || victimFaction == null) {
            return t.pvpFactionless() ? Result.pass() : Result.fail(Messages.get(CombatText.PVP_FACTIONLESS_OFF));
        }
        if (sameFaction) {
            return friendlyFire ? Result.pass() : Result.fail(Messages.get(CombatText.FRIENDLY_FIRE_OFF));
        }
        Relation r = relation == null ? Relation.NEUTRAL : relation;
        return switch (r) {
            case ALLY -> t.allyFire() ? Result.pass() : Result.fail(Messages.get(CombatText.PVP_ALLY));
            case TRUCE -> t.truceFire() ? Result.pass() : Result.fail(Messages.get(CombatText.PVP_TRUCE));
            case NEUTRAL -> t.pvpNeutral() ? Result.pass() : Result.fail(Messages.get(CombatText.PVP_NEUTRAL_OFF));
            case ENEMY, OWN -> Result.pass();
        };
    }
}
