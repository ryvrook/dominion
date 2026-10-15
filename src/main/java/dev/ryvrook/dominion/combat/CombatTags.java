package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.service.CombatTagService;
import dev.ryvrook.dominion.text.Messages;

import java.util.Locale;
import java.util.UUID;

/**
 * The shared combat tag. A player is tagged while Dominion's own tag runs or, when
 * combat.useCombatLog is auto and the CombatLog mod is installed, while CombatLog has them tagged.
 * The setting is read on every call, so a change through /fa set applies at once.
 */
final class CombatTags implements CombatTagService {
    static final String KEY = "combat.useCombatLog";

    private final Settings settings;
    private final CombatTagSource own;
    private final CombatTagSource external;

    CombatTags(Settings settings, CombatTagSource own, CombatTagSource external) {
        this.settings = settings;
        this.own = own;
        this.external = external;
    }

    /** True when the external source is installed and the setting allows it. */
    boolean usingExternal() {
        return external != null && external.present() && !off();
    }

    /** True when the owner turned the external source off. */
    boolean off() {
        String mode = settings == null ? null : settings.text(KEY);
        return mode != null && "off".equals(mode.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean tagged(UUID player) {
        if (player == null) {
            return false;
        }
        if (safeTagged(own, player)) {
            return true;
        }
        return usingExternal() && safeTagged(external, player);
    }

    @Override
    public long remainingMillis(UUID player) {
        if (player == null) {
            return 0L;
        }
        long mine = safeRemaining(own, player);
        long theirs = usingExternal() ? safeRemaining(external, player) : 0L;
        return Math.max(mine, theirs);
    }

    @Override
    public String status() {
        if (external == null || !external.present()) {
            if (external instanceof CombatLogBridge b && b.pluginLoaded()) {
                return Messages.get(CombatText.STATUS_UNREADABLE, "version", versionText(b.version()));
            }
            return Messages.get(CombatText.STATUS_ABSENT);
        }
        String version = versionText(external.version());
        if (off()) {
            return Messages.get(CombatText.STATUS_OFF, "version", version, "key", KEY);
        }
        return Messages.get(CombatText.STATUS_USED, "version", version);
    }

    private static String versionText(String version) {
        return version == null || version.isBlank() ? Messages.get(CombatText.UNKNOWN_VERSION) : version;
    }

    private static boolean safeTagged(CombatTagSource source, UUID player) {
        if (source == null) {
            return false;
        }
        try {
            return source.tagged(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long safeRemaining(CombatTagSource source, UUID player) {
        if (source == null) {
            return 0L;
        }
        try {
            return Math.max(0L, source.remainingMillis(player));
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
