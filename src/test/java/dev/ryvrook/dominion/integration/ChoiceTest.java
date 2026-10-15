package dev.ryvrook.dominion.integration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The auto, always and never switch against a sibling that is present, missing, old or stopped. */
class ChoiceTest {
    static final Presence.Found MISSING = Presence.Found.MISSING;

    static Presence.Found present(String version) {
        return new Presence.Found(new Object(), version, true);
    }

    @Test
    void modeParsing() {
        assertEquals(Mode.AUTO, Mode.parse(null));
        assertEquals(Mode.AUTO, Mode.parse(""));
        assertEquals(Mode.AUTO, Mode.parse("whatever"));
        assertEquals(Mode.ALWAYS, Mode.parse(" Always "));
        assertEquals(Mode.NEVER, Mode.parse("never"));
        assertEquals(Mode.NEVER, Mode.parse("off"));
    }

    @Test
    void autoBindsWhenPresentAndNewEnough() {
        Choice c = Choice.decide(Sibling.TRACKER, Mode.AUTO, present("1.0.3"));
        assertEquals(Choice.Outcome.BIND, c.outcome());
        assertTrue(c.bind());
        assertFalse(c.warn());
        assertEquals("Dominion found TrackerAPI 1.0.3 and links to it", c.startLine());
    }

    @Test
    void autoRunsAloneQuietlyWhenMissing() {
        Choice c = Choice.decide(Sibling.FEATS, Mode.AUTO, MISSING);
        assertEquals(Choice.Outcome.MISSING, c.outcome());
        assertFalse(c.bind());
        assertFalse(c.warn());
        assertEquals("Dominion runs without Feats because it is not installed", c.startLine());
    }

    @Test
    void autoLeavesAnOldVersionAlone() {
        Choice c = Choice.decide(Sibling.ATLAS, Mode.AUTO, present("1.5.0"));
        assertEquals(Choice.Outcome.TOO_OLD, c.outcome());
        assertFalse(c.bind());
        assertTrue(c.startLine().contains("1.5.0 is older than 1.6.0"));
    }

    @Test
    void alwaysBindsAnyVersionAndWarnsWhenMissing() {
        assertEquals(Choice.Outcome.BIND, Choice.decide(Sibling.ATLAS, Mode.ALWAYS, present("1.5.0")).outcome());
        assertEquals(Choice.Outcome.BIND, Choice.decide(Sibling.ATLAS, Mode.ALWAYS, present("1.6.0")).outcome());
        Choice missing = Choice.decide(Sibling.TRACKER, Mode.ALWAYS, MISSING);
        assertEquals(Choice.Outcome.MISSING, missing.outcome());
        assertTrue(missing.warn());
        assertTrue(missing.startLine().contains("integrations.tracker always"));
    }

    @Test
    void neverSkipsWhateverIsInstalled() {
        for (Presence.Found f : new Presence.Found[]{present("9.9.9"), MISSING}) {
            Choice c = Choice.decide(Sibling.TRACKER, Mode.NEVER, f);
            assertEquals(Choice.Outcome.OFF, c.outcome());
            assertFalse(c.bind());
            assertFalse(c.warn());
        }
    }

    @Test
    void aStoppedPluginCountsAsMissing() {
        Choice c = Choice.decide(Sibling.FEATS, Mode.AUTO, new Presence.Found(new Object(), "1.5.1", false));
        assertEquals(Choice.Outcome.MISSING, c.outcome());
        assertTrue(c.startLine().contains("not running"));
        assertEquals(Choice.Outcome.MISSING, Choice.decide(Sibling.FEATS, Mode.AUTO, null).outcome());
    }

    @Test
    void versionsCompareByNumber() {
        assertTrue(VersionCompare.below("1.5.9", "1.6.0"));
        assertFalse(VersionCompare.below("1.10.0", "1.6.0"));
        assertFalse(VersionCompare.below("v1.6.0-pre", "1.6.0"));
        assertTrue(VersionCompare.below("junk", "1.0.0"));
    }

    /** A link that records stop calls. */
    static final class FakeLink implements Link {
        boolean bound = true;
        int stops;
        int polls;

        @Override public boolean bound() { return bound; }
        @Override public String version() { return "1.0.3"; }
        @Override public void poll() { polls++; }
        @Override public void stop() { stops++; bound = false; }
    }

    @Test
    void bindingStatesAndLines() {
        Object plugin = new Object();
        Presence.Found found = new Presence.Found(plugin, "1.0.3", true);
        AtomicReference<Presence.Found> now = new AtomicReference<>(found);
        Binding b = new Binding(Choice.decide(Sibling.TRACKER, Mode.AUTO, found), s -> now.get());
        assertEquals("waiting", b.state());
        FakeLink link = new FakeLink();
        b.attach(link);
        assertEquals("bound", b.state());
        assertEquals("TrackerAPI 1.0.3 is bound, integrations.tracker is auto", b.statusLine());
        assertNull(b.poll());
        assertEquals(1, link.polls);

        now.set(new Presence.Found(new Object(), "1.0.4", true));
        String line = b.poll();
        assertNotNull(line);
        assertTrue(line.contains("was reloaded") && line.contains("/plugin reload Ryvrook:Dominion"), line);
        assertNull(b.poll(), "the change is logged once");
        assertEquals("changed", b.state());

        b.stop();
        b.stop();
        assertEquals(1, link.stops);
        assertNull(b.poll());
    }

    @Test
    void bindingNoticesARemovalAndALateInstall() {
        Presence.Found found = new Presence.Found(new Object(), "1.6.0", true);
        AtomicReference<Presence.Found> now = new AtomicReference<>(found);
        Binding bound = new Binding(Choice.decide(Sibling.ATLAS, Mode.AUTO, found), s -> now.get());
        bound.attach(new FakeLink());
        now.set(MISSING);
        assertTrue(bound.poll().contains("was unloaded"));

        AtomicReference<Presence.Found> later = new AtomicReference<>(MISSING);
        Binding missing = new Binding(Choice.decide(Sibling.FEATS, Mode.AUTO, MISSING), s -> later.get());
        assertEquals("missing", missing.state());
        assertEquals("Feats is missing, integrations.feats is auto", missing.statusLine());
        assertNull(missing.poll());
        later.set(present("1.5.1"));
        assertTrue(missing.poll().contains("was installed after Dominion started"));

        Binding off = new Binding(Choice.decide(Sibling.TRACKER, Mode.NEVER, present("1.0.3")), s -> MISSING);
        assertEquals("off", off.state());
        assertNull(off.poll(), "never does not watch");
    }

    @Test
    void aBridgeThatFailsToLoadLeavesDominionRunning() {
        Presence.Found found = present("1.6.0");
        Binding b = new Binding(Choice.decide(Sibling.ATLAS, Mode.AUTO, found), s -> found);
        try (dev.ryvrook.dominion.model.Fixtures fx = dev.ryvrook.dominion.model.Fixtures.create()) {
            Host host = new Host(fx.ctx, new StatFanout(null), b.choice());
            b.start(name -> {
                throw new NoClassDefFoundError("dev/ryvrook/atlas/api/AtlasApi");
            }, host);
        }
        assertEquals("failed", b.state());
        assertFalse(b.bound());
        assertTrue(b.statusLine().contains("failed to link"));
    }
}
