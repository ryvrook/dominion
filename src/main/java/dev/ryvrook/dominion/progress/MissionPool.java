package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.model.Mission;

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
    /** A template. Rewards are a multiple of the configured daily reward so they follow the settings. */
    public record Template(String id, String name, String description, String kind, long target, boolean weekly,
                           double rewardScale, String icon) {
        public Mission toMission(double baseCoins, int basePoints) {
            return new Mission(id, name, description, kind, target, weekly,
                    (int) Math.round(basePoints * rewardScale), Math.round(baseCoins * rewardScale * 100.0) / 100.0, icon);
        }
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("prospectors", "Prospectors", "Mine 48 ore blocks.", "mine", 48, false, 1.0, "Ore_Copper"),
            new Template("ore_rush", "Ore Rush", "Mine 128 ore blocks.", "mine", 128, false, 2.0, "Ore_Iron"),
            new Template("timber", "Timber", "Chop 64 tree trunks.", "chop", 64, false, 1.0, "Wood_Oak_Trunk"),
            new Template("clear_cut", "Clear Cut", "Chop 160 tree trunks.", "chop", 160, false, 2.0, "Tool_Hatchet_Iron"),
            new Template("harvest_day", "Harvest Day", "Gather 64 crops.", "farm", 64, false, 1.0, "Plant_Crop_Wheat_Item"),
            new Template("full_granary", "Full Granary", "Gather 200 crops.", "farm", 200, false, 2.0, "Plant_Crop_Carrot_Item"),
            new Template("catch_of_the_day", "Catch of the Day", "Catch 12 fish.", "fish", 12, false, 1.0, "Fish_Salmon_Item"),
            new Template("skirmish", "Skirmish", "Defeat 3 players from other factions.", "kill", 3, false, 1.5, "Weapon_Sword_Iron"),
            new Template("market_day", "Market Day", "Complete 5 trades on the market.", "trade", 5, false, 1.0, "Ingredient_Bar_Gold"),
            new Template("pathfinders", "Pathfinders", "Claim 4 chunks of land.", "claim", 4, false, 1.0, "Furniture_Flag_Small_Red"),
            new Template("mother_lode", "Mother Lode", "Mine 1,200 ore blocks.", "mine", 1200, true, 6.0, "Ore_Mithril"),
            new Template("lumber_baron", "Lumber Baron", "Chop 1,500 tree trunks.", "chop", 1500, true, 6.0, "Tool_Hatchet_Mithril"),
            new Template("breadbasket", "Breadbasket", "Gather 2,000 crops.", "farm", 2000, true, 6.0, "Plant_Crop_Pumpkin_Item"),
            new Template("deep_waters", "Deep Waters", "Catch 120 fish.", "fish", 120, true, 6.0, "Fish_Shark_Hammerhead_Item"),
            new Template("war_path", "War Path", "Defeat 25 players from other factions.", "kill", 25, true, 8.0, "Weapon_Longsword_Iron"),
            new Template("merchant_guild", "Merchant Guild", "Complete 40 trades on the market.", "trade", 40, true, 6.0, "Rock_Gem_Emerald"),
            new Template("expansion", "Expansion", "Claim 24 chunks of land.", "claim", 24, true, 5.0, "Furniture_Flag_Orange"),
            new Template("conquest", "Conquest", "Win a siege against an enemy faction.", "siege", 1, true, 8.0, "Weapon_Bomb"),
            new Template("siege_masters", "Siege Masters", "Win 3 sieges against enemy factions.", "siege", 3, true, 12.0, "Weapon_Bomb_Large_Fire"));

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
