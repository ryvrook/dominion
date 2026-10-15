package dev.ryvrook.dominion.api;

import java.util.UUID;

/** Events published on the Dominion bus. Other mods and the web map subscribe to these. */
public sealed interface DominionEvent {
    record FactionCreated(UUID factionId) implements DominionEvent {}
    record FactionDisbanded(UUID factionId, String tag) implements DominionEvent {}
    record FactionChanged(UUID factionId) implements DominionEvent {}
    record MemberJoined(UUID factionId, UUID player) implements DominionEvent {}
    record MemberLeft(UUID factionId, UUID player, String reason) implements DominionEvent {}
    record ClaimChanged(String world, long chunkIndex, UUID oldFaction, UUID newFaction) implements DominionEvent {}
    record RelationChanged(UUID a, UUID b, String relation) implements DominionEvent {}
    record PowerChanged(UUID player, double power) implements DominionEvent {}
    record PlayerEnteredTerritory(UUID player, String world, UUID fromFaction, UUID toFaction) implements DominionEvent {}
    record SiegePhase(UUID siegeId, String phase, UUID attacker, UUID defender, String world, long chunkIndex) implements DominionEvent {}
    record MarketTrade(UUID listingId, String itemId, int quantity, double price, UUID buyer, UUID seller) implements DominionEvent {}
    record UpgradeBought(UUID factionId, String key, int level) implements DominionEvent {}
    record MissionCompleted(UUID factionId, String missionId) implements DominionEvent {}
    record GraceChanged(boolean active, long endsAt) implements DominionEvent {}
    record EotwChanged(boolean active) implements DominionEvent {}
    record BreachChanged(String world, long chunkIndex, UUID attacker, UUID defender, boolean breached) implements DominionEvent {}
    record NationChanged(UUID nationId, String tag) implements DominionEvent {}
    record FactionRenamed(UUID factionId, String oldTag, String newTag, String oldName, String newName) implements DominionEvent {}
    record GameEvent(String kind, String name, String phase, String world, UUID faction, String text) implements DominionEvent {}
}
