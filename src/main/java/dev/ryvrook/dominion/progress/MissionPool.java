package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.model.Mission;
import dev.ryvrook.dominion.text.Messages;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The mission templates and the deterministic daily and weekly pick. Days and weeks are counted
 * in UTC so every server running the same pool agrees on the active missions.
 */
public final class MissionPool {
    /**
     * A template. Rewards are a multiple of the configured daily reward so they follow the settings. The name and
     * description are message ids, read each time a mission is built so messages.json wording applies.
     */
    public record Template(String id, String nameId, String descriptionId, String kind, long target, boolean weekly,
                           double rewardScale, String icon) {
        public String name() {
            return Messages.get(nameId);
        }

        public String description() {
            return Messages.get(descriptionId);
        }

        public Mission toMission(double baseCoins, int basePoints) {
            return new Mission(id, name(), description(), kind, target, weekly,
                    (int) Math.round(basePoints * rewardScale), Math.round(baseCoins * rewardScale * 100.0) / 100.0, icon);
        }
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("prospectors", ProgressText.PROSPECTORS_NAME, ProgressText.PROSPECTORS_DESC, "mine", 48, false, 1.0, "Ore_Copper"),
            new Template("ore_rush", ProgressText.ORE_RUSH_NAME, ProgressText.ORE_RUSH_DESC, "mine", 128, false, 2.0, "Ore_Iron"),
            new Template("timber", ProgressText.TIMBER_NAME, ProgressText.TIMBER_DESC, "chop", 64, false, 1.0, "Wood_Oak_Trunk"),
            new Template("clear_cut", ProgressText.CLEAR_CUT_NAME, ProgressText.CLEAR_CUT_DESC, "chop", 160, false, 2.0, "Tool_Hatchet_Iron"),
            new Template("harvest_day", ProgressText.HARVEST_DAY_NAME, ProgressText.HARVEST_DAY_DESC, "farm", 64, false, 1.0, "Plant_Crop_Wheat_Item"),
            new Template("full_granary", ProgressText.FULL_GRANARY_NAME, ProgressText.FULL_GRANARY_DESC, "farm", 200, false, 2.0, "Plant_Crop_Carrot_Item"),
            new Template("catch_of_the_day", ProgressText.CATCH_OF_THE_DAY_NAME, ProgressText.CATCH_OF_THE_DAY_DESC, "fish", 12, false, 1.0, "Fish_Salmon_Item"),
            new Template("skirmish", ProgressText.SKIRMISH_NAME, ProgressText.SKIRMISH_DESC, "kill", 3, false, 1.5, "Weapon_Sword_Iron"),
            new Template("market_day", ProgressText.MARKET_DAY_NAME, ProgressText.MARKET_DAY_DESC, "trade", 5, false, 1.0, "Ingredient_Bar_Gold"),
            new Template("pathfinders", ProgressText.PATHFINDERS_NAME, ProgressText.PATHFINDERS_DESC, "claim", 4, false, 1.0, "Furniture_Flag_Small_Red"),
            new Template("mother_lode", ProgressText.MOTHER_LODE_NAME, ProgressText.MOTHER_LODE_DESC, "mine", 1200, true, 6.0, "Ore_Mithril"),
            new Template("lumber_baron", ProgressText.LUMBER_BARON_NAME, ProgressText.LUMBER_BARON_DESC, "chop", 1500, true, 6.0, "Tool_Hatchet_Mithril"),
            new Template("breadbasket", ProgressText.BREADBASKET_NAME, ProgressText.BREADBASKET_DESC, "farm", 2000, true, 6.0, "Plant_Crop_Pumpkin_Item"),
            new Template("deep_waters", ProgressText.DEEP_WATERS_NAME, ProgressText.DEEP_WATERS_DESC, "fish", 120, true, 6.0, "Fish_Shark_Hammerhead_Item"),
            new Template("war_path", ProgressText.WAR_PATH_NAME, ProgressText.WAR_PATH_DESC, "kill", 25, true, 8.0, "Weapon_Longsword_Iron"),
            new Template("merchant_guild", ProgressText.MERCHANT_GUILD_NAME, ProgressText.MERCHANT_GUILD_DESC, "trade", 40, true, 6.0, "Rock_Gem_Emerald"),
            new Template("expansion", ProgressText.EXPANSION_NAME, ProgressText.EXPANSION_DESC, "claim", 24, true, 5.0, "Furniture_Flag_Orange"),
            new Template("conquest", ProgressText.CONQUEST_NAME, ProgressText.CONQUEST_DESC, "siege", 1, true, 8.0, "Weapon_Bomb"),
            new Template("siege_masters", ProgressText.SIEGE_MASTERS_NAME, ProgressText.SIEGE_MASTERS_DESC, "siege", 3, true, 12.0, "Weapon_Bomb_Large_Fire"));

    private MissionPool() {}

    public static Template byId(String id) {
        for (Template t : TEMPLATES) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Week number counted from the Monday before the epoch, so weeks roll over on Monday 00:00 UTC. */
    public static long thisWeek() {
        return Math.floorDiv(today() + 3, 7);
    }

    public static long period(boolean weekly) {
        return weekly ? thisWeek() : today();
    }

    /**
     * Picks count templates for the period. The shuffle is seeded by the period number, and the pick
     * prefers different kinds before repeating one.
     */
    public static List<Template> pick(boolean weekly, long period, int count) {
        List<Template> pool = new ArrayList<>();
        for (Template t : TEMPLATES) {
            if (t.weekly() == weekly) {
                pool.add(t);
            }
        }
        Collections.shuffle(pool, new Random(period * 1_000_003L + (weekly ? 7919L : 104_729L)));
        int n = Math.max(0, Math.min(count, pool.size()));
        List<Template> out = new ArrayList<>(n);
        Set<String> kinds = new HashSet<>();
        for (Template t : pool) {
            if (out.size() >= n) {
                break;
            }
            if (kinds.add(t.kind())) {
                out.add(t);
            }
        }
        for (Template t : pool) {
            if (out.size() >= n) {
                break;
            }
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
