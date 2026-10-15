package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.text.Messages;

/**
 * Every admin facing sentence of the importer, editable in messages.json. Skipped records, notes and issues
 * have no full stop of their own because the preview adds one.
 */
public final class ImporterText {
    private ImporterText() {}

    // ---- counts
    public static final String FACTIONS_ONE = Messages.define("importer.factionsOne", "{count} faction");
    public static final String FACTIONS_MANY = Messages.define("importer.factionsMany", "{count} factions");
    public static final String MEMBERS_ONE = Messages.define("importer.membersOne", "{count} member");
    public static final String MEMBERS_MANY = Messages.define("importer.membersMany", "{count} members");
    public static final String CHUNKS_ONE = Messages.define("importer.chunksOne", "{count} chunk");
    public static final String CHUNKS_MANY = Messages.define("importer.chunksMany", "{count} chunks");
    public static final String RECORDS_ONE = Messages.define("importer.recordsOne", "{count} record");
    public static final String RECORDS_MANY = Messages.define("importer.recordsMany", "{count} records");

    // ---- sources
    public static final String FILE_UNREADABLE = Messages.define("importer.fileUnreadable", "The file {file} could not be read because {reason}");
    public static final String HYF_FILE_INCOMPLETE = Messages.define("importer.hyfFileIncomplete", "The file {file} has no Id, Owner or Name");
    public static final String HYF_NO_CLAIMS = Messages.define("importer.hyfNoClaims", "Claims.json was not found so no land is imported");
    public static final String HYF_ZONES = Messages.define("importer.hyfZones", "Zones in {file} are not imported. Recreate them with Dominion zone commands");
    public static final String HYPER_ZONES = Messages.define("importer.hyperZones", "Safe and war zones in zones.json are not imported. Recreate them with Dominion zone commands");
    public static final String HYPER_FILE_INCOMPLETE = Messages.define("importer.hyperFileIncomplete", "The file {file} has no faction id or name");
    public static final String MEMBER_NO_UUID = Messages.define("importer.memberNoUuid", "A member of {faction} has no valid UUID");
    public static final String CLAIM_DAMAGED_OF = Messages.define("importer.claimDamagedOf", "A claim of {faction} is missing its world or has damaged chunk coordinates");
    public static final String UF_NO_PLAYERS = Messages.define("importer.ufNoPlayers", "players.json could not be read so some member names are unknown");
    public static final String UF_FACTION_INCOMPLETE = Messages.define("importer.ufFactionIncomplete", "A faction in factions.json has no id or name");
    public static final String UF_NO_CLAIMS = Messages.define("importer.ufNoClaims", "claims.json was not found so no land is imported");
    public static final String CLAIM_UNKNOWN_FACTION = Messages.define("importer.claimUnknownFaction", "A claim belongs to an unknown faction {faction}");
    public static final String CLAIM_WORLD_UNLOADED = Messages.define("importer.claimWorldUnloaded", "A claim of {faction} is in world {world} which is not loaded, so its name is unknown");
    public static final String UF_ZONES = Messages.define("importer.ufZones", "{count} safe zone and war zone cells are not imported. Recreate them with Dominion zone commands");
    public static final String UF_WORLD_UUIDS = Messages.define("importer.ufWorldUuids", "Claims are stored by world UUID. Load those worlds and preview again to include them");
    public static final String UF_CELLS = Messages.define("importer.ufCells", "Ultimate Factions claims 16 block cells. Each Dominion chunk is {size} blocks wide, so a chunk is claimed in full when any of its cells was claimed");
    public static final String SC_JOURNAL = Messages.define("importer.scJournal", "SimpleClaims.db{suffix} is not empty, so the newest Simple Claims changes may be missing. Stop the server with Simple Claims installed once, or remove Simple Claims first, then import");
    public static final String SC_PARTY_INCOMPLETE = Messages.define("importer.scPartyIncomplete", "A party has no id or owner");
    public static final String SC_RESERVED = Messages.define("importer.scReserved", "{count} reserved chunks are not imported because Dominion has no reservations");
    public static final String SC_PARTY_FILE_INCOMPLETE = Messages.define("importer.scPartyFileIncomplete", "A party in Parties.json has no id or owner");
    public static final String SC_NO_NAMES = Messages.define("importer.scNoNames", "{file} could not be read so some member names are unknown");
    public static final String CLAIM_DAMAGED = Messages.define("importer.claimDamaged", "A claim is missing its world or has damaged chunk coordinates");
    public static final String SC_CLAIM_UNKNOWN_PARTY = Messages.define("importer.scClaimUnknownParty", "The claim at {x}, {z} in {world} belongs to an unknown party {party}");

    // ---- planner
    public static final String ALREADY_IMPORTED = Messages.define("importer.alreadyImported", "{faction} was already imported as {tag}");
    public static final String IMPORTED_AND_GONE = Messages.define("importer.importedAndGone", "{faction} was imported before and that faction no longer exists");
    public static final String MEMBER_ELSEWHERE = Messages.define("importer.memberElsewhere", "{player} is already in the Dominion faction {tag} and is left out");
    public static final String MEMBER_EARLIER = Messages.define("importer.memberEarlier", "{player} belongs to an earlier faction in this import and is left out");
    public static final String NEW_LEADER = Messages.define("importer.newLeader", "The original leader cannot lead it, so {player} becomes the leader");
    public static final String NO_LEADER = Messages.define("importer.noLeader", "{faction} is skipped because none of its members is free to lead it");
    public static final String NO_TAG = Messages.define("importer.noTag", "{faction} is skipped because no free tag could be made from its name");
    public static final String TAG_BECOMES = Messages.define("importer.tagBecomes", "Its tag becomes {tag}");
    public static final String TAG_BECOMES_TAKEN = Messages.define("importer.tagBecomesTaken", "Its tag becomes {tag} because that tag is already used in Dominion");
    public static final String TAKEN_ONE = Messages.define("importer.takenOne", "{count} of its chunks is already claimed in Dominion and skipped");
    public static final String TAKEN_MANY = Messages.define("importer.takenMany", "{count} of its chunks are already claimed in Dominion and skipped");
    public static final String DUPLICATE_ONE = Messages.define("importer.duplicateOne", "{count} of its chunks is also claimed by an earlier faction in this import and skipped");
    public static final String DUPLICATE_MANY = Messages.define("importer.duplicateMany", "{count} of its chunks are also claimed by an earlier faction in this import and skipped");

    // ---- preview and run
    public static final String UNREADABLE = Messages.define("importer.unreadable", "The {source} data could not be read because {reason}");
    public static final String NO_SUCH_SOURCE = Messages.define("importer.noSuchSource", "There is no import source called {source}. Choose one of {sources}.");
    public static final String PREVIEW_HEADER = Messages.define("importer.previewHeader", "{source} import preview. Nothing is changed until the import is run.");
    public static final String EXPERIMENTAL = Messages.define("importer.experimental", "This source is experimental because {source} is closed source and its format is not officially documented.");
    public static final String CANNOT_RUN_UNREADABLE = Messages.define("importer.cannotRunUnreadable", "{problem}. The import cannot run until the data can be read in full.");
    public static final String LEDGER_UNREADABLE = Messages.define("importer.ledgerUnreadable", "The import record {file} cannot be read, so the import cannot run until it is fixed or removed.");
    public static final String NOTHING_FOUND = Messages.define("importer.nothingFound", "No {source} data was found.");
    public static final String LOOKED_FOR = Messages.define("importer.lookedFor", "Looked for {path}");
    public static final String READING_FROM = Messages.define("importer.readingFrom", "Reading {source} data from {path}");
    public static final String WOULD_CREATE = Messages.define("importer.wouldCreate", "{factions} with {members} and {chunks} would be created.");
    public static final String WOULD_BECOME = Messages.define("importer.wouldBecome", "{faction} would become {tag} led by {leader} with {members} and {chunks}{worlds}.");
    public static final String LINE = Messages.define("importer.line", "{text}.");
    public static final String SKIPPED_COUNT = Messages.define("importer.skippedCount", "{records} skipped.");
    public static final String MORE_SKIPPED = Messages.define("importer.moreSkipped", "And {count} more skipped records.");
    public static final String NOT_IMPORTED = Messages.define("importer.notImported", "Homes, relations, power, banks and permissions are not imported.");
    public static final String WORLD_PART = Messages.define("importer.worldPart", "{count} in {world}");
    public static final String WORLDS = Messages.define("importer.worlds", ", {parts}");
    public static final String WORLDS_AND = Messages.define("importer.worldsAnd", "{list} and {last}");
    public static final String RUN_NO_SUCH_SOURCE = Messages.define("importer.runNoSuchSource", "There is no import source called {source}. Choose one of {sources}");
    public static final String PREVIEW_FIRST = Messages.define("importer.previewFirst", "Preview the import from {source} first and run it within 10 minutes of the preview");
    public static final String AN_ADMIN = Messages.define("importer.anAdmin", "An admin");
    public static final String NOTHING_IMPORTED = Messages.define("importer.nothingImported", "{problem}. Nothing was imported");
    public static final String RUN_NOTHING_FOUND = Messages.define("importer.runNothingFound", "No {source} data was found. Use the preview to see where Dominion looked");
    public static final String NOTHING_NEW = Messages.define("importer.nothingNew", "There is nothing new to import from {source}. {records} skipped");
    public static final String RUN_LEDGER_UNREADABLE = Messages.define("importer.runLedgerUnreadable", "The import record {file} cannot be read, so Dominion cannot tell what was already imported. Fix or remove it and preview again");
    public static final String AUDIT_IMPORTED = Messages.define("importer.auditImported", "{admin} imported this faction from {source} where it was {name}, with {members} and {chunks}");
    public static final String IMPORTED = Messages.define("importer.imported", "Imported {factions} from {source} with {members} and {chunks}.");
    public static final String CHUNKS_LOST = Messages.define("importer.chunksLost", "{chunks} could not be claimed.");
    public static final String NO_LAND_MODULE = Messages.define("importer.noLandModule", "The land module is not loaded so no land was imported.");
    public static final String RUN_SKIPPED = Messages.define("importer.runSkipped", "{records} skipped. The preview lists them.");
    public static final String UNTOUCHED = Messages.define("importer.untouched", "The {source} data was left untouched.");
}
