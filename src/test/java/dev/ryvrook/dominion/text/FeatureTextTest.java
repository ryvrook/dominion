package dev.ryvrook.dominion.text;

import dev.ryvrook.dominion.events.EventsText;
import dev.ryvrook.dominion.export.ExportText;
import dev.ryvrook.dominion.importer.ImporterText;
import dev.ryvrook.dominion.map.MapText;
import dev.ryvrook.dominion.market.MarketText;
import dev.ryvrook.dominion.shop.ShopText;
import dev.ryvrook.dominion.siege.SiegeText;
import dev.ryvrook.dominion.vault.VaultText;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text classes of the shop, events, market, siege, vault, importer, export and map packages. */
class FeatureTextTest {
    private static final Map<Class<?>, String> CLASSES = Map.of(
            ShopText.class, "shop.",
            EventsText.class, "events.",
            MarketText.class, "market.",
            SiegeText.class, "siege.",
            VaultText.class, "vault.",
            ImporterText.class, "importer.",
            ExportText.class, "export.",
            MapText.class, "map.");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    /** The placeholders each id is filled with by the code. Ids that are not listed take none. */
    private static final Map<String, String> PLACEHOLDERS = Map.ofEntries(
            Map.entry("events.auditHill", "hill"),
            Map.entry("events.auditOutpostLost", "faction,outpost"),
            Map.entry("events.auditOutpostTook", "outpost"),
            Map.entry("events.boostOther", "kind"),
            Map.entry("events.boostOtherVowel", "kind"),
            Map.entry("events.bountyCollected", "amount,killer,player"),
            Map.entry("events.bountyLineMany", "amount,count,player"),
            Map.entry("events.bountyLineOne", "amount,count,player"),
            Map.entry("events.bountyMin", "amount"),
            Map.entry("events.bountyPlaced", "amount,player"),
            Map.entry("events.bountyPlacedAnnounce", "amount,placer,player,total"),
            Map.entry("events.commandsMany", "count"),
            Map.entry("events.commandsOne", "count"),
            Map.entry("events.coords", "x,y,z"),
            Map.entry("events.daysMany", "count"),
            Map.entry("events.daysOne", "count"),
            Map.entry("events.envoyClaimed", "player,zone"),
            Map.entry("events.envoyDropping", "zone"),
            Map.entry("events.envoyLanded", "time,where,zone"),
            Map.entry("events.envoyLine", "time,where,zone"),
            Map.entry("events.envoyLineNoChest", "time,where,zone"),
            Map.entry("events.envoyNoWorld", "where,zone"),
            Map.entry("events.envoyTimerLine", "minutes,time,zone"),
            Map.entry("events.envoyTimerOff", "zone"),
            Map.entry("events.envoyTimerSet", "minutes,zone"),
            Map.entry("events.envoyVanished", "where,zone"),
            Map.entry("events.factionsMany", "count"),
            Map.entry("events.factionsOne", "count"),
            Map.entry("events.hillAlreadyOpen", "hill"),
            Map.entry("events.hillCaptured", "faction,hill,reward"),
            Map.entry("events.hillClosed", "hill"),
            Map.entry("events.hillClosedByAdmin", "hill"),
            Map.entry("events.hillContested", "hill"),
            Map.entry("events.hillExists", "hill"),
            Map.entry("events.hillHolds", "faction,hill,time"),
            Map.entry("events.hillLastWon", "ago,faction"),
            Map.entry("events.hillNotOpen", "hill"),
            Map.entry("events.hillOpenAnnounce", "hill,reward,time,where"),
            Map.entry("events.hillOpened", "hill"),
            Map.entry("events.hillOpensOneMinute", "hill,where"),
            Map.entry("events.hillOpensTenMinutes", "hill,where"),
            Map.entry("events.hillRadiusMax", "max"),
            Map.entry("events.hillRemoved", "hill"),
            Map.entry("events.hillScheduled", "hill,schedule,time"),
            Map.entry("events.hillSet", "hill,radius,where"),
            Map.entry("events.hillStatusContested", "hill,time,where"),
            Map.entry("events.hillStatusFree", "hill,time,where"),
            Map.entry("events.hillStatusHeld", "cap,faction,held,hill,time,where"),
            Map.entry("events.hillStatusScheduled", "hill,schedule,time,where"),
            Map.entry("events.hillStatusUnscheduled", "hill,where"),
            Map.entry("events.hillTaking", "faction,hill"),
            Map.entry("events.hillTimedOut", "hill"),
            Map.entry("events.hillUnscheduled", "hill"),
            Map.entry("events.listAnd", "last,list"),
            Map.entry("events.memoBounty", "player"),
            Map.entry("events.memoHill", "hill"),
            Map.entry("events.noEnvoyNear", "distance"),
            Map.entry("events.noPlayerNamed", "player"),
            Map.entry("events.noSuchHill", "hill"),
            Map.entry("events.noSuchOutpost", "outpost"),
            Map.entry("events.noSuchZone", "tag"),
            Map.entry("events.notWarZone", "zone"),
            Map.entry("events.outpostExists", "outpost"),
            Map.entry("events.outpostLost", "faction,outpost"),
            Map.entry("events.outpostRadiusMax", "max"),
            Map.entry("events.outpostRemoved", "outpost"),
            Map.entry("events.outpostReset", "outpost"),
            Map.entry("events.outpostResetAlert", "outpost"),
            Map.entry("events.outpostSet", "boost,multiplier,outpost,radius,where"),
            Map.entry("events.outpostStatus", "boost,multiplier,outpost,where"),
            Map.entry("events.outpostStatusChallenger", "control,faction"),
            Map.entry("events.outpostStatusHeld", "control,faction"),
            Map.entry("events.outpostTaken", "faction,outpost"),
            Map.entry("events.outpostTakenFrom", "faction,loser,outpost"),
            Map.entry("events.outpostWon", "boost,multiplier,outpost"),
            Map.entry("events.payoutLine", "command,faction,rank"),
            Map.entry("events.payoutsAlreadyRan", "ago"),
            Map.entry("events.payoutsPermission", "permission"),
            Map.entry("events.payoutsRanFeed", "factions"),
            Map.entry("events.payoutsSent", "commands"),
            Map.entry("events.payoutsSentFailed", "commands,failed"),
            Map.entry("events.poiEnvoy", "zone"),
            Map.entry("events.poiHill", "hill"),
            Map.entry("events.poiHillOpen", "hill"),
            Map.entry("events.poiOutpost", "outpost"),
            Map.entry("events.poiOutpostHeld", "faction,outpost"),
            Map.entry("events.rewardBoth", "coins,points"),
            Map.entry("events.rewardCoinsIts", "amount"),
            Map.entry("events.rewardCoinsYours", "amount"),
            Map.entry("events.rewardPoints", "points"),
            Map.entry("events.rowBoost", "boost,multiplier"),
            Map.entry("events.rowBoostContested", "boost,multiplier"),
            Map.entry("events.rowClosesIn", "time"),
            Map.entry("events.rowDropsIn", "zone"),
            Map.entry("events.rowEvery", "minutes"),
            Map.entry("events.rowHeld", "faction,percent"),
            Map.entry("events.rowNextIn", "time"),
            Map.entry("events.rowOpensIn", "time"),
            Map.entry("events.rowPlacer", "count"),
            Map.entry("events.rowPlacers", "count"),
            Map.entry("events.rowVanishesIn", "time"),
            Map.entry("events.rowWonBy", "faction"),
            Map.entry("events.scheduleDaily", "times"),
            Map.entry("events.scheduleEvery", "minutes"),
            Map.entry("events.seasonAlreadyEnded", "ago"),
            Map.entry("events.seasonBegunEotw", "days"),
            Map.entry("events.seasonBegunGrace", "days"),
            Map.entry("events.seasonTopMany", "factions"),
            Map.entry("events.seasonTopOne", "factions"),
            Map.entry("events.statusEotwIn", "time"),
            Map.entry("events.statusFrozen", "ago"),
            Map.entry("events.statusGraceEnds", "time"),
            Map.entry("events.statusPayoutsRan", "ago"),
            Map.entry("events.statusStarted", "ago"),
            Map.entry("events.where", "world,x,y,z"),
            Map.entry("events.zoneNoLand", "zone"),
            Map.entry("export.allies", "a,b"),
            Map.entry("export.banned", "faction,player"),
            Map.entry("export.claimed", "chunk,faction,world"),
            Map.entry("export.coins", "amount"),
            Map.entry("export.disbanded", "faction"),
            Map.entry("export.enemy", "a,b"),
            Map.entry("export.founded", "faction"),
            Map.entry("export.gaveUp", "chunk,faction,world"),
            Map.entry("export.homeLabel", "faction"),
            Map.entry("export.joined", "faction,player"),
            Map.entry("export.kicked", "faction,player"),
            Map.entry("export.left", "faction,player"),
            Map.entry("export.mission", "faction,mission"),
            Map.entry("export.neutral", "a,b"),
            Map.entry("export.siegeBegan", "attacker,defender"),
            Map.entry("export.siegeBreached", "attacker,defender"),
            Map.entry("export.siegeChanged", "attacker,defender"),
            Map.entry("export.siegeDefended", "attacker,defender"),
            Map.entry("export.siegeExpired", "attacker,defender"),
            Map.entry("export.siegeFailed", "attacker,defender"),
            Map.entry("export.siegeLabel", "attacker,defender"),
            Map.entry("export.tookLand", "chunk,faction,other,world"),
            Map.entry("export.trade", "amount,count,item,player"),
            Map.entry("export.truce", "a,b"),
            Map.entry("export.upgrade", "faction,level,upgrade"),
            Map.entry("importer.alreadyImported", "faction,tag"),
            Map.entry("importer.auditImported", "admin,chunks,members,name,source"),
            Map.entry("importer.cannotRunUnreadable", "problem"),
            Map.entry("importer.chunksLost", "chunks"),
            Map.entry("importer.chunksMany", "count"),
            Map.entry("importer.chunksOne", "count"),
            Map.entry("importer.claimDamagedOf", "faction"),
            Map.entry("importer.claimUnknownFaction", "faction"),
            Map.entry("importer.claimWorldUnloaded", "faction,world"),
            Map.entry("importer.duplicateMany", "count"),
            Map.entry("importer.duplicateOne", "count"),
            Map.entry("importer.experimental", "source"),
            Map.entry("importer.factionsMany", "count"),
            Map.entry("importer.factionsOne", "count"),
            Map.entry("importer.fileUnreadable", "file,reason"),
            Map.entry("importer.hyfFileIncomplete", "file"),
            Map.entry("importer.hyfZones", "file"),
            Map.entry("importer.hyperFileIncomplete", "file"),
            Map.entry("importer.imported", "chunks,factions,members,source"),
            Map.entry("importer.importedAndGone", "faction"),
            Map.entry("importer.ledgerUnreadable", "file"),
            Map.entry("importer.line", "text"),
            Map.entry("importer.lookedFor", "path"),
            Map.entry("importer.memberEarlier", "player"),
            Map.entry("importer.memberElsewhere", "player,tag"),
            Map.entry("importer.memberNoUuid", "faction"),
            Map.entry("importer.membersMany", "count"),
            Map.entry("importer.membersOne", "count"),
            Map.entry("importer.moreSkipped", "count"),
            Map.entry("importer.newLeader", "player"),
            Map.entry("importer.noLeader", "faction"),
            Map.entry("importer.noSuchSource", "source,sources"),
            Map.entry("importer.noTag", "faction"),
            Map.entry("importer.nothingFound", "source"),
            Map.entry("importer.nothingImported", "problem"),
            Map.entry("importer.nothingNew", "records,source"),
            Map.entry("importer.previewFirst", "source"),
            Map.entry("importer.previewHeader", "source"),
            Map.entry("importer.readingFrom", "path,source"),
            Map.entry("importer.recordsMany", "count"),
            Map.entry("importer.recordsOne", "count"),
            Map.entry("importer.runLedgerUnreadable", "file"),
            Map.entry("importer.runNoSuchSource", "source,sources"),
            Map.entry("importer.runNothingFound", "source"),
            Map.entry("importer.runSkipped", "records"),
            Map.entry("importer.scClaimUnknownParty", "party,world,x,z"),
            Map.entry("importer.scJournal", "suffix"),
            Map.entry("importer.scNoNames", "file"),
            Map.entry("importer.scReserved", "count"),
            Map.entry("importer.skippedCount", "records"),
            Map.entry("importer.tagBecomes", "tag"),
            Map.entry("importer.tagBecomesTaken", "tag"),
            Map.entry("importer.takenMany", "count"),
            Map.entry("importer.takenOne", "count"),
            Map.entry("importer.ufCells", "size"),
            Map.entry("importer.ufZones", "count"),
            Map.entry("importer.unreadable", "reason,source"),
            Map.entry("importer.untouched", "source"),
            Map.entry("importer.worldPart", "count,world"),
            Map.entry("importer.worlds", "parts"),
            Map.entry("importer.worldsAnd", "last,list"),
            Map.entry("importer.wouldBecome", "chunks,faction,leader,members,tag,worlds"),
            Map.entry("importer.wouldCreate", "chunks,factions,members"),
            Map.entry("map.allyHome", "faction"),
            Map.entry("map.home", "faction"),
            Map.entry("map.siegeBreached", "attacker,defender"),
            Map.entry("map.siegeCapturing", "attacker,defender"),
            Map.entry("market.auctionNoBids", "lot"),
            Map.entry("market.auctionSold", "amount,buyer,lot"),
            Map.entry("market.auctionStarted", "count,hours,item,start"),
            Map.entry("market.auctionStartedOneHour", "count,hours,item,start"),
            Map.entry("market.auctionWon", "amount,lot"),
            Map.entry("market.auditPinned", "lot,seller"),
            Map.entry("market.auditUnpinned", "lot,seller"),
            Map.entry("market.bidHighest", "amount,lot"),
            Map.entry("market.bidHighestExtended", "amount,lot,minutes"),
            Map.entry("market.bidPlacedSeller", "amount,bidder,lot"),
            Map.entry("market.bidTooLow", "amount"),
            Map.entry("market.bought", "amount,count,item"),
            Map.entry("market.boughtOut", "amount,lot"),
            Map.entry("market.boughtOutSeller", "amount,buyer,lot"),
            Map.entry("market.collectedMany", "count"),
            Map.entry("market.collectedManyAndCoins", "coins,count"),
            Map.entry("market.collectedOne", "count"),
            Map.entry("market.collectedOneAndCoins", "coins,count"),
            Map.entry("market.itemToMailbox", "item"),
            Map.entry("market.listed", "count,item,price"),
            Map.entry("market.listingExpired", "lot"),
            Map.entry("market.listingLimit", "max,open"),
            Map.entry("market.lot", "count,item"),
            Map.entry("market.mailboxCoins", "coins"),
            Map.entry("market.mailboxItems", "stacks"),
            Map.entry("market.mailboxItemsAndCoins", "coins,stacks"),
            Map.entry("market.memoAuction", "lot"),
            Map.entry("market.memoFilledOrder", "count,item"),
            Map.entry("market.memoSale", "count,item"),
            Map.entry("market.memoTax", "player"),
            Map.entry("market.noSuchItem", "item"),
            Map.entry("market.noneToSell", "item"),
            Map.entry("market.onlyCarry", "count,item"),
            Map.entry("market.onlyLeft", "count"),
            Map.entry("market.orderAmountRange", "max"),
            Map.entry("market.orderCancelled", "amount"),
            Map.entry("market.orderExpired", "amount,lot"),
            Map.entry("market.orderFilledBy", "count,item,seller"),
            Map.entry("market.orderNeedsOnly", "count"),
            Map.entry("market.orderPlaced", "count,item,price,total"),
            Map.entry("market.outbid", "amount,lot"),
            Map.entry("market.pinned", "lot"),
            Map.entry("market.soldTo", "amount,buyer,count,item"),
            Map.entry("market.soldToOrder", "amount,count,item"),
            Map.entry("market.storefrontFull", "limit"),
            Map.entry("market.unpinned", "lot"),
            Map.entry("shop.auditStocked", "lot,price"),
            Map.entry("shop.auditStockedVault", "lot,page,price,slot"),
            Map.entry("shop.auditTook", "lot"),
            Map.entry("shop.balance", "balance"),
            Map.entry("shop.bought", "amount,lot"),
            Map.entry("shop.boughtFrom", "amount,faction,lot"),
            Map.entry("shop.buttonBuy", "count"),
            Map.entry("shop.buttonSell", "count"),
            Map.entry("shop.buyRange", "max"),
            Map.entry("shop.buyingLot", "lot"),
            Map.entry("shop.categoryNameRules", "max"),
            Map.entry("shop.categoryReady", "category"),
            Map.entry("shop.categoryRemovedMany", "category,count"),
            Map.entry("shop.categoryRemovedOne", "category,count"),
            Map.entry("shop.doesNotBuy", "item"),
            Map.entry("shop.entriesUsed", "max,used"),
            Map.entry("shop.entryAdded", "category,item,prices"),
            Map.entry("shop.entryRemoved", "category,item"),
            Map.entry("shop.entryUpdated", "category,item,prices"),
            Map.entry("shop.footerPayments", "faction"),
            Map.entry("shop.headingManage", "faction"),
            Map.entry("shop.lot", "count,item"),
            Map.entry("shop.memoAdminPurchase", "lot"),
            Map.entry("shop.memoAdminSale", "count,item"),
            Map.entry("shop.memoBankSale", "buyer,lot"),
            Map.entry("shop.memoBoughtFrom", "faction,lot"),
            Map.entry("shop.memoRefundTo", "buyer"),
            Map.entry("shop.moreSlot", "count"),
            Map.entry("shop.moreSlots", "count"),
            Map.entry("shop.newMemberVault", "time"),
            Map.entry("shop.noFactionCalled", "tag"),
            Map.entry("shop.noSuchCategory", "category"),
            Map.entry("shop.noSuchItem", "item"),
            Map.entry("shop.noSuchShopCategory", "category"),
            Map.entry("shop.noneToSell", "item"),
            Map.entry("shop.notListed", "category,item"),
            Map.entry("shop.nothingForSale", "faction"),
            Map.entry("shop.owed", "amount,faction"),
            Map.entry("shop.pricesBoth", "buy,sell"),
            Map.entry("shop.pricesBuyOnly", "buy"),
            Map.entry("shop.pricesSellOnly", "sell"),
            Map.entry("shop.restRefunded", "count"),
            Map.entry("shop.sectionAddItem", "category"),
            Map.entry("shop.seedAdded", "count"),
            Map.entry("shop.seedSkipped", "count"),
            Map.entry("shop.sellRange", "max"),
            Map.entry("shop.shopFull", "count,max"),
            Map.entry("shop.slotChanged", "page,slot"),
            Map.entry("shop.slotEmpty", "page,slot"),
            Map.entry("shop.sold", "amount,count,item"),
            Map.entry("shop.soldFromShop", "amount,buyer,lot"),
            Map.entry("shop.stockMailed", "faction,item"),
            Map.entry("shop.stocked", "lot,price"),
            Map.entry("shop.stockedVault", "lot,price"),
            Map.entry("shop.titleFactionShop", "faction"),
            Map.entry("shop.took", "lot"),
            Map.entry("shop.vaultPageRange", "pages"),
            Map.entry("siege.attackersMany", "count"),
            Map.entry("siege.attackersOne", "count"),
            Map.entry("siege.auditAdminEndedAgainst", "faction"),
            Map.entry("siege.auditAdminEndedBy", "faction"),
            Map.entry("siege.auditBreached", "chunk,faction"),
            Map.entry("siege.auditCalledOff", "faction"),
            Map.entry("siege.auditHeldOff", "faction"),
            Map.entry("siege.auditLost", "faction"),
            Map.entry("siege.auditStarted", "chunk,faction,world"),
            Map.entry("siege.auditStartedAgainstUs", "chunk,faction,world"),
            Map.entry("siege.auditTheyHeldOff", "faction"),
            Map.entry("siege.auditWeWereBreached", "chunk,faction"),
            Map.entry("siege.auditWon", "blocks,containers,faction"),
            Map.entry("siege.begun", "faction,time"),
            Map.entry("siege.breachedAttacker", "faction,time,where"),
            Map.entry("siege.breachedDefender", "faction,time,where"),
            Map.entry("siege.calledOff", "faction"),
            Map.entry("siege.cooldown", "faction,time"),
            Map.entry("siege.crowd", "attackers,chunk,defenders"),
            Map.entry("siege.defendedAttacker", "faction,where"),
            Map.entry("siege.defendedDefender", "faction,where"),
            Map.entry("siege.defendedDefenderImmune", "faction,hours,where"),
            Map.entry("siege.defendersMany", "count"),
            Map.entry("siege.defendersOne", "count"),
            Map.entry("siege.failedAttacker", "faction,reason,where"),
            Map.entry("siege.failedDefender", "faction,reason,where"),
            Map.entry("siege.hoursMany", "count"),
            Map.entry("siege.hoursOne", "count"),
            Map.entry("siege.immune", "faction,time"),
            Map.entry("siege.membersMany", "count"),
            Map.entry("siege.membersOne", "count"),
            Map.entry("siege.raisedAttacker", "faction,time,where"),
            Map.entry("siege.raisedDefender", "faction,where"),
            Map.entry("siege.shielded", "faction"),
            Map.entry("siege.statusAttackers", "crowd,faction,time"),
            Map.entry("siege.statusAttackersNow", "crowd,faction"),
            Map.entry("siege.statusDefenders", "crowd,faction,time"),
            Map.entry("siege.statusDefendersNow", "crowd,faction"),
            Map.entry("siege.targetInSiege", "faction"),
            Map.entry("siege.tooFewAttackers", "members"),
            Map.entry("siege.tooFewDefenders", "faction,members"),
            Map.entry("siege.where", "chunk,world"),
            Map.entry("siege.wonAttacker", "faction,where"),
            Map.entry("siege.wonDefender", "faction,where"),
            Map.entry("vault.auditOpened", "page"),
            Map.entry("vault.auditTook", "count,item,page,slot"),
            Map.entry("vault.newMemberHold", "time"),
            Map.entry("vault.officerPage", "page"),
            Map.entry("vault.opened", "page"),
            Map.entry("vault.opening", "page"),
            Map.entry("vault.pageRange", "total"),
            Map.entry("vault.rescued", "faction"));

    private static Map<String, String> constants() throws IllegalAccessException {
        Map<String, String> byId = new HashMap<>();
        for (Map.Entry<Class<?>, String> e : CLASSES.entrySet()) {
            for (Field f : e.getKey().getDeclaredFields()) {
                int mod = f.getModifiers();
                if (f.getType() != String.class || !Modifier.isStatic(mod)) {
                    continue;
                }
                assertTrue(Modifier.isPublic(mod) && Modifier.isFinal(mod), f + " is a public constant");
                String id = (String) f.get(null);
                String where = e.getKey().getSimpleName() + "." + f.getName();
                assertTrue(id.startsWith(e.getValue()), where + " starts with " + e.getValue());
                assertTrue(Messages.has(id), where + " is a defined id");
                String twin = byId.put(id, where);
                assertNull(twin, id + " is shared by " + twin + " and " + where);
            }
        }
        return byId;
    }

    @Test
    void everyConstantIsADefinedIdWithItsPackagePrefixAndNoTwoShareAnId() throws Exception {
        Map<String, String> byId = constants();
        assertEquals(725, byId.size(), "every sentence of these packages has an id");
        for (String className : MessageSets.ALL) {
            assertNotNull(Class.forName(className), className);
        }
        for (Class<?> c : CLASSES.keySet()) {
            assertTrue(Arrays.asList(MessageSets.ALL).contains(c.getName()), c.getName() + " is listed in MessageSets");
        }
    }

    @Test
    void everyPlaceholderInADefaultIsOneTheCodeFills() throws Exception {
        Map<String, String> byId = constants();
        for (String id : PLACEHOLDERS.keySet()) {
            assertTrue(byId.containsKey(id), id + " is a constant");
        }
        for (String id : byId.keySet()) {
            Set<String> found = new TreeSet<>();
            Matcher m = PLACEHOLDER.matcher(Messages.defaultOf(id));
            while (m.find()) {
                found.add(m.group(1));
            }
            String listed = PLACEHOLDERS.get(id);
            Set<String> expected = listed == null ? Set.of() : new TreeSet<>(List.of(listed.split(",")));
            assertEquals(expected, found, id);
        }
    }

    @Test
    void defaultsFollowTheProseRules() throws Exception {
        for (String id : constants().keySet()) {
            String text = Messages.defaultOf(id);
            assertFalse(text.contains("—"), id + " has no em dash");
            assertFalse(text.contains(";"), id + " has no semicolon");
            assertFalse(text.isBlank(), id + " has text");
        }
    }

    @Test
    void sentencesReadAsBefore() {
        assertEquals("Opened vault page 2.", Messages.get(VaultText.OPENED, "page", 2));
        assertEquals("Listed 3 Iron Bar for 5 coins each.",
                Messages.get(MarketText.LISTED, "count", 3, "item", "Iron Bar", "price", "5 coins"));
        assertEquals("Hill alpha at 1, 2, 3 in world is open and nobody holds it. It closes in 5 minutes.",
                Messages.get(EventsText.HILL_STATUS_FREE, "hill", "alpha", "where", "1, 2, 3 in world", "time", "5 minutes"));
        assertEquals("WOLF sieging BEAR, breached", Messages.get(MapText.SIEGE_BREACHED, "attacker", "WOLF", "defender", "BEAR"));
    }
}
