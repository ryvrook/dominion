package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.UUID;

/** One line of a faction's audit log. */
public record AuditEntry(long at, UUID who, String whoName, String category, String text) {

    public Document toDocument() {
        return new Document("At", at).append("Who", who == null ? null : who.toString())
                .append("WhoName", whoName).append("Category", category).append("Text", text);
    }

    public static AuditEntry fromDocument(Document d) {
        String who = d.getString("Who");
        return new AuditEntry(d.get("At", Number.class).longValue(), who == null ? null : UUID.fromString(who),
                d.getString("WhoName"), d.getString("Category"), d.getString("Text"));
    }
}
