package dev.ryvrook.dominion.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Classes shaped like the CombatLog API, and some that are not, for the bridge tests. */
final class FakeCombatLogApis {
    private FakeCombatLogApis() {}

    /** The same static shape as org.combatlog.plugin.api.CombatLogApi 1.7.0. */
    public static final class Good {
        static final Map<UUID, Long> TAGS = new HashMap<>();
        static boolean available = true;

        private Good() {}

        public static boolean isAvailable() {
            return available;
        }

        public static boolean isTagged(UUID player) {
            return available && TAGS.getOrDefault(player, 0L) > 0;
        }

        public static long getRemainingMillis(UUID player) {
            return available ? TAGS.getOrDefault(player, 0L) : 0L;
        }
    }

    /** A future version where the methods became instance methods. */
    public static final class InstanceMethods {
        public boolean isAvailable() {
            return true;
        }

        public boolean isTagged(UUID player) {
            return true;
        }

        public long getRemainingMillis(UUID player) {
            return 1000L;
        }
    }

    /** A future version where the remaining time became an int. */
    public static final class WrongReturnType {
        private WrongReturnType() {}

        public static boolean isAvailable() {
            return true;
        }

        public static boolean isTagged(UUID player) {
            return true;
        }

        public static int getRemainingMillis(UUID player) {
            return 1000;
        }
    }

    /** A version whose calls throw, as a broken or shutting down mod might. */
    public static final class Throwing {
        private Throwing() {}

        public static boolean isAvailable() {
            throw new IllegalStateException("not ready");
        }

        public static boolean isTagged(UUID player) {
            throw new IllegalStateException("not ready");
        }

        public static long getRemainingMillis(UUID player) {
            throw new NoClassDefFoundError("org/combatlog/plugin/Missing");
        }
    }
}
