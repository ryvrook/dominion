package dev.ryvrook.dominion.events;

import dev.ryvrook.dominion.text.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The pure rules for season payouts and bounties, kept apart from the service so they can be
 * tested. Payout commands come from events.payoutCommands, which can only be set in config.json.
 */
final class Payouts {
    private Payouts() {}

    /** Splits the pipe separated payout setting into console commands without a leading slash. */
    static List<String> templates(String setting) {
        List<String> out = new ArrayList<>();
        if (setting == null) {
            return out;
        }
        for (String t : setting.split("\\|")) {
            String c = t.trim();
            if (c.startsWith("/")) {
                c = c.substring(1).trim();
            }
            if (!c.isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    /** Fills {tag} and {rank} into one template. */
    static String fill(String template, String tag, String rank) {
        return template.replace("{tag}", tag).replace("{rank}", rank);
    }

    /**
     * True for a tag that is safe to put into a console command. Faction tags are plain letters and
     * digits, so anything else, such as a space that would add arguments, is refused.
     */
    static boolean safeTag(String tag) {
        return tag != null && tag.matches("[A-Za-z0-9_]{1,32}");
    }

    /** Why a bounty amount is refused. */
    enum AmountProblem { NONE, TOO_SMALL, NOT_WHOLE }

    static AmountProblem checkAmount(double amount, double min, boolean wholeUnits) {
        if (!Double.isFinite(amount) || !(amount > 0) || amount < min) {
            return AmountProblem.TOO_SMALL;
        }
        if (wholeUnits && amount != Math.floor(amount)) {
            return AmountProblem.NOT_WHOLE;
        }
        return AmountProblem.NONE;
    }

    /**
     * True when the killer may collect a bounty. Factions cannot farm a bounty on their own
     * member, and a player who put money on the target cannot collect it back.
     */
    static boolean collectable(UUID killer, UUID killerFaction, UUID victimFaction, Set<UUID> placers) {
        if (killerFaction != null && killerFaction.equals(victimFaction)) {
            return false;
        }
        return !placers.contains(killer);
    }

    /** Joins the top tags as A, B and C for the season end line. */
    static String topList(List<String> tags, int max) {
        int n = Math.min(Math.max(0, max), tags.size());
        if (n == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(tags.get(i));
        }
        return n == 1 ? tags.get(0) : Messages.get(EventsText.LIST_AND, "list", out, "last", tags.get(n - 1));
    }
}
