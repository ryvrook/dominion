package dev.ryvrook.dominion.siege;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveOrderTest {
    @Test
    void anOlderSnapshotNeverLandsAfterANewerOne() {
        SaveOrder order = new SaveOrder();
        List<Long> disk = new ArrayList<>();
        long older = order.next();
        long newer = order.next();
        assertEquals(SaveOrder.Outcome.WRITTEN, order.write(newer, () -> disk.add(newer)));
        assertEquals(SaveOrder.Outcome.STALE, order.write(older, () -> disk.add(older)));
        assertEquals(List.of(newer), disk);
    }

    @Test
    void aFailedWriteLetsTheSameOrAnOlderSnapshotTryAgain() {
        SaveOrder order = new SaveOrder();
        long first = order.next();
        long second = order.next();
        assertEquals(SaveOrder.Outcome.FAILED, order.write(second, () -> false));
        assertEquals(SaveOrder.Outcome.WRITTEN, order.write(first, () -> true));
        long third = order.next();
        assertEquals(SaveOrder.Outcome.WRITTEN, order.write(third, () -> true));
        assertEquals(SaveOrder.Outcome.STALE, order.write(second, () -> true));
    }

    @Test
    void writesInOrderAllLand() {
        SaveOrder order = new SaveOrder();
        for (int i = 0; i < 5; i++) {
            long g = order.next();
            assertEquals(SaveOrder.Outcome.WRITTEN, order.write(g, () -> true));
        }
    }
}
