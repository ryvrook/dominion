package dev.ryvrook.dominion.admin;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

class AdminTextTest {
    @Test
    void everySentenceIsADefinedAdminIdWithTheExpectedPlaceholders() throws Exception {
        TextClassCheck.check(AdminText.class, "admin", Map.ofEntries(
                entry(AdminText.SET_FILE_ONLY, Set.of("key")),
                entry(AdminText.SET_DONE, Set.of("key", "value")),
                entry(AdminText.NO_SUCH_SETTING, Set.of("key")),
                entry(AdminText.GET_VALUE, Set.of("key", "value")),
                entry(AdminText.GET_DETAILS, Set.of("default", "group", "label")),
                entry(AdminText.GET_RANGE, Set.of("max", "min")),
                entry(AdminText.GET_RANGE_OPEN, Set.of("min")),
                entry(AdminText.GET_CHOICES, Set.of("values")),
                entry(AdminText.PRESET_LIST, Set.of("presets")),
                entry(AdminText.PRESET_APPLIED, Set.of("name")),
                entry(AdminText.NO_SUCH_TAG, Set.of("tag")),
                entry(AdminText.CLAIMED_SOME, Set.of("count", "tag", "total")),
                entry(AdminText.RELEASED, Set.of("count")),
                entry(AdminText.INSPECT_TITLE, Set.of("chunk", "world")),
                entry(AdminText.INSPECT_CLAIMED, Set.of("ago", "by")),
                entry(AdminText.INSPECT_CLAIMED_IN_ZONE, Set.of("ago", "by", "zone")),
                entry(AdminText.INSPECT_POWER, Set.of("boost", "claims", "maxClaims", "maxPower", "power")),
                entry(AdminText.FORCE_JOINED, Set.of("player", "tag")),
                entry(AdminText.FORCE_KICKED, Set.of("player")),
                entry(AdminText.FORCE_LEADER, Set.of("player", "tag")),
                entry(AdminText.FORCE_DISBANDED, Set.of("tag")),
                entry(AdminText.FORCE_RENAMED, Set.of("tag")),
                entry(AdminText.NO_PLAYER_NAMED, Set.of("name")),
                entry(AdminText.NO_FACTION_TAG, Set.of("tag")),
                entry(AdminText.POWER_NOW, Set.of("player", "power")),
                entry(AdminText.AUDIT_BOOST, Set.of("boost")),
                entry(AdminText.BOOST_NOW, Set.of("boost", "tag")),
                entry(AdminText.GRACE_ENDS_IN, Set.of("time")),
                entry(AdminText.GRACE_UNTIL, Set.of("date")),
                entry(AdminText.ECO_PROVIDER, Set.of("provider")),
                entry(AdminText.ECO_PROVIDER_OFF, Set.of("provider")),
                entry(AdminText.BALANCE_NOW, Set.of("balance", "player")),
                entry(AdminText.BACKUP_WRITTEN, Set.of("path")),
                entry(AdminText.SETTINGS_RELOADED_PROBLEM, Set.of("count")),
                entry(AdminText.SETTINGS_RELOADED_PROBLEMS, Set.of("count")),
                entry(AdminText.EXPORT_WRITTEN, Set.of("path")),
                entry(AdminText.STRIKE_GIVEN, Set.of("points", "tag")),
                entry(AdminText.STRIKES_NONE, Set.of("tag")),
                entry(AdminText.STRIKES_TITLE, Set.of("tag")),
                entry(AdminText.STRIKE_LINE_ONE, Set.of("points", "reason")),
                entry(AdminText.STRIKE_LINE_MANY, Set.of("points", "reason")),
                entry(AdminText.STRIKE_GIVEN_BY, Set.of("ago", "by")),
                entry(AdminText.STRIKE_REMOVED, Set.of("index", "tag")),
                entry(AdminText.IMPORT_SOURCES, Set.of("sources")),
                entry(AdminText.IMPORT_NO_SUCH_SOURCE, Set.of("source")),
                entry(AdminText.IMPORT_PREVIEW_FIRST, Set.of("source")),
                entry(AdminText.IMPORTING, Set.of("source")),
                entry(AdminText.IMPORT_CHECK_PREVIEW, Set.of("source")),
                entry(AdminText.IMPORT_NOTHING, Set.of("source")),
                entry(AdminText.IMPORT_PREVIEW_TITLE, Set.of("source")),
                entry(AdminText.IMPORT_APPLY_HINT, Set.of("source")),
                entry(AdminText.SIEGE_LABEL, Set.of("attacker", "chunk", "defender", "phase", "time", "world")),
                entry(AdminText.DIAG_COUNTS, Set.of("factions", "players")),
                entry(AdminText.DIAG_CLAIMS, Set.of("claims", "world")),
                entry(AdminText.DIAG_MODULES, Set.of("ok", "total")),
                entry(AdminText.DIAG_SNAPSHOT_ON, Set.of("path", "seconds")),
                entry(AdminText.DIAG_SNAPSHOT_OFF, Set.of("path")),
                entry(AdminText.DIAG_PRESET, Set.of("preset", "seconds"))));
    }
}
