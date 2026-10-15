package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

class RelationTextTest {
    @Test
    void everyIdIsDefinedOnceWithItsPlaceholders() throws Exception {
        TextClassCheck.check(RelationText.class, "relation", TextClassCheck.expect(
                RelationText.STATE_ALLY, "",
                RelationText.STATE_TRUCE, "",
                RelationText.STATE_ENEMY, "",
                RelationText.STATE_NEUTRAL, "",
                RelationText.AN_ALLY, "",
                RelationText.A_TRUCE, "",
                RelationText.NOUN_ALLY, "",
                RelationText.NOUN_TRUCE, "",
                RelationText.YOUR_FACTION, "",
                RelationText.AUDIT_SET, "faction relation",
                RelationText.AUDIT_SET_BY, "faction relation",
                RelationText.NOW_OURS, "faction state",
                RelationText.NOW_THEIRS, "faction state",
                RelationText.AUDIT_ASKED, "faction request",
                RelationText.AUDIT_ASKED_BY, "faction request",
                RelationText.ASKED_NOTICE, "faction request",
                RelationText.WANTS_NOTICE, "faction request",
                RelationText.AUDIT_DECLINED, "faction noun",
                RelationText.AUDIT_DECLINED_BY, "faction noun",
                RelationText.DECLINED_NOTICE, "faction noun",
                RelationText.DECLINED_YOURS, "faction noun",
                RelationText.AUDIT_EXPIRED_FROM, "faction noun",
                RelationText.AUDIT_EXPIRED_TO, "faction noun",
                RelationText.NOT_SETTABLE, "",
                RelationText.NOT_ASKED, "faction",
                RelationText.REQUEST_EXPIRED, "",
                RelationText.DECLINED, "faction",
                RelationText.ALREADY_ENEMIES, "faction",
                RelationText.NOW_ENEMY, "faction",
                RelationText.ALREADY_NEUTRAL, "faction",
                RelationText.WITHDREW, "faction",
                RelationText.NOW_NEUTRAL, "faction",
                RelationText.ALREADY_ALLIED, "faction",
                RelationText.ALREADY_TRUCE, "faction",
                RelationText.ALREADY_ASKED, "faction",
                RelationText.ASKED_HOUR, "faction hours",
                RelationText.ASKED_HOURS, "faction hours",
                RelationText.NOW_ALLIED, "faction",
                RelationText.NOW_TRUCE, "faction",
                RelationText.MAX_ALLY, "max who",
                RelationText.MAX_ALLIES, "max who",
                RelationText.MAX_TRUCE, "max who",
                RelationText.MAX_TRUCES, "max who",
                RelationText.RANK_DENIED, "",
                RelationText.NO_FACTION_WITH_TAG, "",
                RelationText.OWN_FACTION, "",
                RelationText.ZONES_NO_DIPLOMACY, "",
                RelationText.SAME_NATION, "faction"));
    }
}
