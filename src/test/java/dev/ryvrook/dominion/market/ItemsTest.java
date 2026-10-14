package dev.ryvrook.dominion.market;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs without item assets, which is exactly the case where stacks cannot be built. */
class ItemsTest {
    @Test
    void splitDocumentsNeverDropsAPart() {
        Document template = new Document("Id", "Mod_Gone_Sword").append("Quantity", 1).append("Durability", 12.5);
        for (int qty : new int[] {1, 99, 100, 101, 250, 1000}) {
            List<Document> docs = Items.splitDocuments(template, "Mod_Gone_Sword", qty);
            int total = docs.stream().mapToInt(d -> d.getInteger("Quantity")).sum();
            assertEquals(qty, total);
            for (Document d : docs) {
                assertEquals("Mod_Gone_Sword", d.getString("Id"));
                assertEquals(12.5, d.getDouble("Durability"));
            }
        }
        assertEquals(1, template.getInteger("Quantity"), "the template is never changed");
    }

    @Test
    void aMissingTemplateStillKeepsIdAndQuantity() {
        List<Document> docs = Items.splitDocuments(null, "Ore_Iron", 5);
        assertEquals(1, docs.size());
        assertEquals("Ore_Iron", docs.get(0).getString("Id"));
        assertEquals(5, docs.get(0).getInteger("Quantity"));
        assertEquals(0, Items.splitDocuments(null, "Ore_Iron", 0).size());
    }

    @Test
    void readableNamesComeFromTheId() {
        assertEquals("Ingredient Bar Gold", Items.name("Ingredient_Bar_Gold"));
        assertEquals("item", Items.name(null));
        assertEquals("item", Items.name("  "));
    }
}
