package dev.ryvrook.dominion.shop;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the shops, their pages and commands, editable in messages.json. */
public final class ShopText {
    private ShopText() {}

    // ---- shared pieces
    public static final String LOT = Messages.define("shop.lot", "{count} {item}");
    public static final String SOMEONE = Messages.define("shop.someone", "Someone");
    public static final String DONE = Messages.define("shop.done", "Done.");
    public static final String DID_NOT_WORK = Messages.define("shop.didNotWork", "That did not work.");
    public static final String PAGE_FAILED = Messages.define("shop.pageFailed", "Part of this page could not be shown. Try again in a moment.");
    public static final String BUTTON_FAILED = Messages.define("shop.buttonFailed", "Something went wrong with that button. Try again in a moment.");
    public static final String BALANCE = Messages.define("shop.balance", "Your balance is {balance}");
    public static final String NEED_WORLD = Messages.define("shop.needWorld", "You need to be in a world to do that.");
    public static final String SOMETHING_WRONG = Messages.define("shop.somethingWrong", "Something went wrong and nothing was changed.");

    // ---- page labels
    public static final String LABEL_SHOP = Messages.define("shop.labelShop", "Shop");
    public static final String LABEL_SERVER_SHOP = Messages.define("shop.labelServerShop", "Server shop");
    public static final String LABEL_PRICES_EACH = Messages.define("shop.labelPricesEach", "Prices are for each item.");
    public static final String LABEL_ITEM = Messages.define("shop.labelItem", "Item");
    public static final String LABEL_BUY_PRICE = Messages.define("shop.labelBuyPrice", "Buy price");
    public static final String LABEL_SELL_PRICE = Messages.define("shop.labelSellPrice", "Sell price");
    public static final String LABEL_NOT_SOLD = Messages.define("shop.labelNotSold", "Not sold");
    public static final String LABEL_NOT_BOUGHT = Messages.define("shop.labelNotBought", "Not bought");
    public static final String BUTTON_BUY = Messages.define("shop.buttonBuy", "Buy {count}");
    public static final String BUTTON_SELL = Messages.define("shop.buttonSell", "Sell {count}");
    public static final String BUTTON_SELL_HELD = Messages.define("shop.buttonSellHeld", "Sell held item");
    public static final String BUTTON_CLOSE = Messages.define("shop.buttonClose", "Close");
    public static final String LABEL_QUANTITY = Messages.define("shop.labelQuantity", "Quantity");
    public static final String EMPTY_SHOP = Messages.define("shop.emptyShop", "The shop has nothing for sale yet.");
    public static final String EMPTY_CATEGORY = Messages.define("shop.emptyCategory", "This category is empty.");

    public static final String LABEL_EDITOR = Messages.define("shop.labelEditor", "Shop editor");
    public static final String EDITOR_OPEN = Messages.define("shop.editorOpen", "The admin shop is open");
    public static final String EDITOR_CLOSED = Messages.define("shop.editorClosed", "The admin shop is closed in the settings");
    public static final String EDITOR_FOOTER = Messages.define("shop.editorFooter", "A price of 0 means that direction is not offered.");
    public static final String TAB_NEW = Messages.define("shop.tabNew", "new");
    public static final String SECTION_NEW_CATEGORY = Messages.define("shop.sectionNewCategory", "New category");
    public static final String LABEL_NAME = Messages.define("shop.labelName", "Name");
    public static final String BUTTON_CREATE = Messages.define("shop.buttonCreate", "Create");
    public static final String EDITOR_START = Messages.define("shop.editorStart", "Create a category or press Seed defaults to start with a small catalogue.");
    public static final String SECTION_ADD_ITEM = Messages.define("shop.sectionAddItem", "Add an item to {category}");
    public static final String LABEL_ITEM_ID = Messages.define("shop.labelItemId", "Item id");
    public static final String LABEL_BUY = Messages.define("shop.labelBuy", "Buy");
    public static final String LABEL_SELL = Messages.define("shop.labelSell", "Sell");
    public static final String BUTTON_ADD = Messages.define("shop.buttonAdd", "Add");
    public static final String EDITOR_CATEGORY_UNSAVED = Messages.define("shop.editorCategoryUnsaved", "This category has no items yet. It is saved once you add one.");
    public static final String BUTTON_SAVE = Messages.define("shop.buttonSave", "Save");
    public static final String BUTTON_REMOVE = Messages.define("shop.buttonRemove", "Remove");
    public static final String BUTTON_DELETE_CATEGORY = Messages.define("shop.buttonDeleteCategory", "Delete category");
    public static final String BUTTON_OPEN_SHOP = Messages.define("shop.buttonOpenShop", "Open shop");
    public static final String BUTTON_SEED = Messages.define("shop.buttonSeed", "Seed defaults");
    public static final String EDITOR_NO_PERMISSION = Messages.define("shop.editorNoPermission", "You need the admin permission to edit the shop.");
    public static final String CATEGORY_NAME_RULES = Messages.define("shop.categoryNameRules", "Category names use letters, numbers, spaces, dashes or underscores and up to {max} characters.");
    public static final String CATEGORY_READY = Messages.define("shop.categoryReady", "Category {category} is ready. Add an item to save it.");
    public static final String TYPE_ITEM_ID = Messages.define("shop.typeItemId", "Type the id of the item to add.");
    public static final String PRICES_NUMBERS = Messages.define("shop.pricesNumbers", "Prices must be numbers.");

    public static final String LABEL_FACTION_SHOP = Messages.define("shop.labelFactionShop", "Faction shop");
    public static final String FACTION_GONE = Messages.define("shop.factionGone", "This faction no longer exists.");
    public static final String TITLE_FACTION_SHOP = Messages.define("shop.titleFactionShop", "{faction} shop");
    public static final String FOOTER_PAYMENTS = Messages.define("shop.footerPayments", "Payments go to the bank of {faction}.");
    public static final String FACTION_SHOPS_OFF = Messages.define("shop.factionShopsOff", "Faction shops are turned off on this server.");
    public static final String SECTION_FOR_SALE = Messages.define("shop.sectionForSale", "For sale");
    public static final String NOTHING_FOR_SALE = Messages.define("shop.nothingForSale", "{faction} has nothing for sale right now.");
    public static final String LABEL_IN_STOCK = Messages.define("shop.labelInStock", "In stock");
    public static final String LABEL_PRICE_EACH = Messages.define("shop.labelPriceEach", "Price each");
    public static final String BUTTON_MANAGE = Messages.define("shop.buttonManage", "Manage shop");

    public static final String NO_LONGER_MEMBER = Messages.define("shop.noLongerMember", "You are no longer in this faction.");
    public static final String MANAGE_OFFICERS = Messages.define("shop.manageOfficers", "Only officers and the leader can manage the faction shop.");
    public static final String HEADING_MANAGE = Messages.define("shop.headingManage", "Manage the {faction} shop");
    public static final String ENTRIES_USED = Messages.define("shop.entriesUsed", "{used} of {max} entries used");
    public static final String FOOTER_SALES = Messages.define("shop.footerSales", "Sales are paid into the faction bank.");
    public static final String BUTTON_VIEW = Messages.define("shop.buttonView", "View shop");
    public static final String SHOPS_OFF_TAKE_BACK = Messages.define("shop.shopsOffTakeBack", "Faction shops are turned off on this server. You can still take items back.");
    public static final String SECTION_STOCK_HELD = Messages.define("shop.sectionStockHeld", "Stock the held item");
    public static final String STOCK_HELD_HINT = Messages.define("shop.stockHeldHint", "Hold the stack you want to sell in your hand, then enter the price for each item.");
    public static final String BUTTON_STOCK_HELD = Messages.define("shop.buttonStockHeld", "Stock held");
    public static final String SECTION_IN_SHOP = Messages.define("shop.sectionInShop", "In the shop");
    public static final String SHOP_EMPTY = Messages.define("shop.shopEmpty", "The shop is empty.");
    public static final String LABEL_STOCKED_BY = Messages.define("shop.labelStockedBy", "Stocked by");
    public static final String BUTTON_TAKE_BACK = Messages.define("shop.buttonTakeBack", "Take back");
    public static final String SECTION_FROM_VAULT = Messages.define("shop.sectionFromVault", "Stock from the vault");
    public static final String VAULT_UNAVAILABLE = Messages.define("shop.vaultUnavailable", "The faction vault is not available on this server.");
    public static final String VAULT_RANK = Messages.define("shop.vaultRank", "Your rank cannot use the faction vault.");
    public static final String VAULT_HINT = Messages.define("shop.vaultHint", "Enter the price for each item next to a vault slot and press Stock.");
    public static final String LABEL_PAGE = Messages.define("shop.labelPage", "Page");
    public static final String LABEL_SLOT = Messages.define("shop.labelSlot", "Slot");
    public static final String LABEL_AMOUNT = Messages.define("shop.labelAmount", "Amount");
    public static final String BUTTON_STOCK = Messages.define("shop.buttonStock", "Stock");
    public static final String VAULT_EMPTY = Messages.define("shop.vaultEmpty", "The vault is empty.");
    public static final String MORE_SLOT = Messages.define("shop.moreSlot", "{count} more slot is not shown. Stock some of these first.");
    public static final String MORE_SLOTS = Messages.define("shop.moreSlots", "{count} more slots are not shown. Stock some of these first.");
    public static final String ENTER_PRICE = Messages.define("shop.enterPrice", "Enter a price for each item.");
    public static final String BAD_VAULT_SLOT = Messages.define("shop.badVaultSlot", "That vault slot is not valid.");

    // ---- commands
    public static final String CMD_SHOP = Messages.define("shop.cmdShop", "Open the server shop.");
    public static final String CMD_SHOP_CATEGORY = Messages.define("shop.cmdShopCategory", "Open the server shop on a category.");
    public static final String ARG_CATEGORY = Messages.define("shop.argCategory", "Shop category");
    public static final String CMD_SELL = Messages.define("shop.cmdSell", "Sell the item in your hand to the server shop.");
    public static final String CMD_FASHOP = Messages.define("shop.cmdFashop", "Edit the server shop.");
    public static final String CMD_EDIT = Messages.define("shop.cmdEdit", "Open the shop editor.");
    public static final String CMD_SET = Messages.define("shop.cmdSet", "Add or change a shop entry.");
    public static final String ARG_ITEM_ID = Messages.define("shop.argItemId", "Item id");
    public static final String ARG_BUY = Messages.define("shop.argBuy", "Buy price, 0 for not sold");
    public static final String ARG_SELL = Messages.define("shop.argSell", "Sell price, 0 for not bought");
    public static final String CMD_REMOVE = Messages.define("shop.cmdRemove", "Remove a shop entry.");
    public static final String ADMIN_HELP = Messages.define("shop.adminHelp", "Shop admin commands");
    public static final String CMD_FSHOP = Messages.define("shop.cmdFshop", "Open your faction shop or another faction's shop.");
    public static final String FSHOP_NO_FACTION = Messages.define("shop.fshopNoFaction", "You are not in a faction. Use /fshop with a faction tag to visit another shop.");
    public static final String CMD_FSHOP_TAG = Messages.define("shop.cmdFshopTag", "Open a faction's shop.");
    public static final String ARG_TAG = Messages.define("shop.argTag", "Faction tag");
    public static final String NO_FACTION_CALLED = Messages.define("shop.noFactionCalled", "There is no faction called {tag}.");
    public static final String CMD_MANAGE = Messages.define("shop.cmdManage", "Manage your faction shop.");

    // ---- prices
    public static final String PRICE_NEGATIVE = Messages.define("shop.priceNegative", "Prices cannot be negative.");
    public static final String PRICE_NOT_POSITIVE = Messages.define("shop.priceNotPositive", "The price must be more than zero.");
    public static final String PRICE_TOO_HIGH = Messages.define("shop.priceTooHigh", "That price is too high.");
    public static final String PRICE_TOO_SMALL = Messages.define("shop.priceTooSmall", "The price must be at least 0.01.");
    public static final String PRICE_WHOLE = Messages.define("shop.priceWhole", "Prices must be whole numbers because the currency is an item.");
    public static final String PRICES_BOTH = Messages.define("shop.pricesBoth", "Players buy it for {buy} and sell it for {sell}.");
    public static final String PRICES_BUY_ONLY = Messages.define("shop.pricesBuyOnly", "Players buy it for {buy} and cannot sell it.");
    public static final String PRICES_SELL_ONLY = Messages.define("shop.pricesSellOnly", "Players sell it for {sell} and cannot buy it.");

    // ---- admin shop service
    public static final String STOCK_MAILED = Messages.define("shop.stockMailed", "The {item} you stocked in the shop of {faction} went to your market mailbox.");
    public static final String GIVE_ITEM_ID = Messages.define("shop.giveItemId", "Give the id of the item to list.");
    public static final String NO_SUCH_ITEM = Messages.define("shop.noSuchItem", "There is no item called {item}.");
    public static final String GIVE_A_PRICE = Messages.define("shop.giveAPrice", "Give the item a buy price, a sell price or both.");
    public static final String SELL_ABOVE_BUY = Messages.define("shop.sellAboveBuy", "The sell price cannot be higher than the buy price, or players could buy and sell for profit.");
    public static final String ENTRY_ADDED = Messages.define("shop.entryAdded", "Added {item} in {category}. {prices}");
    public static final String ENTRY_UPDATED = Messages.define("shop.entryUpdated", "Updated {item} in {category}. {prices}");
    public static final String GIVE_CATEGORY_AND_ITEM = Messages.define("shop.giveCategoryAndItem", "Give a category and an item id.");
    public static final String NO_SUCH_CATEGORY = Messages.define("shop.noSuchCategory", "There is no category called {category}.");
    public static final String NOT_LISTED = Messages.define("shop.notListed", "{item} is not listed in {category}.");
    public static final String ENTRY_REMOVED = Messages.define("shop.entryRemoved", "Removed {item} from {category}.");
    public static final String CATEGORY_DROPPED = Messages.define("shop.categoryDropped", "The category was empty so it was removed too.");
    public static final String CATEGORY_REMOVED_ONE = Messages.define("shop.categoryRemovedOne", "Removed the category {category} and its {count} entry.");
    public static final String CATEGORY_REMOVED_MANY = Messages.define("shop.categoryRemovedMany", "Removed the category {category} and its {count} entries.");
    public static final String SEED_NONE_EXIST = Messages.define("shop.seedNoneExist", "None of the starter items exist on this server.");
    public static final String SEED_IN_PLACE = Messages.define("shop.seedInPlace", "The starter catalogue is already in place.");
    public static final String SEED_ADDED = Messages.define("shop.seedAdded", "Added {count} starter entries.");
    public static final String SEED_SKIPPED = Messages.define("shop.seedSkipped", "{count} items do not exist on this server and were skipped.");
    public static final String ADMIN_CLOSED = Messages.define("shop.adminClosed", "The admin shop is closed.");
    public static final String BUY_RANGE = Messages.define("shop.buyRange", "Buy between 1 and {max} at a time.");
    public static final String NOT_FOR_SALE = Messages.define("shop.notForSale", "That item is not for sale.");
    public static final String ITEM_GONE = Messages.define("shop.itemGone", "That item no longer exists on this server.");
    public static final String BUY_OFFLINE = Messages.define("shop.buyOffline", "You need to be online to buy from the shop.");
    public static final String CANNOT_MAKE = Messages.define("shop.cannotMake", "That item could not be made right now.");
    public static final String MEMO_ADMIN_PURCHASE = Messages.define("shop.memoAdminPurchase", "Admin shop purchase of {lot}");
    public static final String REST_MAILED = Messages.define("shop.restMailed", "Your inventory was full so the rest went to your market mailbox.");
    public static final String MEMO_ADMIN_REFUND = Messages.define("shop.memoAdminRefund", "Admin shop refund");
    public static final String REST_REFUNDED = Messages.define("shop.restRefunded", "Your inventory was full so {count} were refunded.");
    public static final String BOUGHT = Messages.define("shop.bought", "You bought {lot} for {amount}.");
    public static final String BUYING_LOT = Messages.define("shop.buyingLot", "Buying {lot}.");
    public static final String SELL_RANGE = Messages.define("shop.sellRange", "Sell between 1 and {max} at a time.");
    public static final String DOES_NOT_BUY_THAT = Messages.define("shop.doesNotBuyThat", "The shop does not buy that item.");
    public static final String SELL_OFFLINE = Messages.define("shop.sellOffline", "You need to be online to sell to the shop.");
    public static final String NONE_TO_SELL = Messages.define("shop.noneToSell", "You have no {item} to sell.");
    public static final String SELLING = Messages.define("shop.selling", "Selling.");
    public static final String HOLD_TO_SELL = Messages.define("shop.holdToSell", "Hold the item you want to sell in your hand.");
    public static final String NO_BROKEN = Messages.define("shop.noBroken", "The shop does not buy broken items.");
    public static final String DOES_NOT_BUY = Messages.define("shop.doesNotBuy", "The shop does not buy {item}.");
    public static final String HAND_TAKE_FAILED = Messages.define("shop.handTakeFailed", "That item could not be taken from your hand.");
    public static final String INVENTORY_TAKE_FAILED = Messages.define("shop.inventoryTakeFailed", "Those items could not be taken from your inventory.");
    public static final String MEMO_ADMIN_SALE = Messages.define("shop.memoAdminSale", "Admin shop sale of {count} {item}");
    public static final String NOT_PAID = Messages.define("shop.notPaid", "You could not be paid so your items were returned.");
    public static final String SOLD = Messages.define("shop.sold", "You sold {count} {item} for {amount}.");

    // ---- faction shop service
    public static final String STOCK_OFFICERS = Messages.define("shop.stockOfficers", "Only officers and the leader can stock the faction shop.");
    public static final String NEW_MEMBER_VAULT = Messages.define("shop.newMemberVault", "New members cannot use the vault yet. Try again in {time}.");
    public static final String VAULT_ONE_PAGE = Messages.define("shop.vaultOnePage", "Your vault has one page.");
    public static final String VAULT_PAGE_RANGE = Messages.define("shop.vaultPageRange", "Your vault has pages 1 to {pages}.");
    public static final String PICK_SLOT = Messages.define("shop.pickSlot", "Pick a vault slot from 1 upwards.");
    public static final String SHOP_FULL = Messages.define("shop.shopFull", "Your faction shop already holds {count} entries. The limit is {max}.");
    public static final String SLOT_EMPTY = Messages.define("shop.slotEmpty", "Vault page {page} slot {slot} is empty.");
    public static final String SLOT_CHANGED = Messages.define("shop.slotChanged", "Vault page {page} slot {slot} changed. Check the list and try again.");
    public static final String NO_BROKEN_STOCK = Messages.define("shop.noBrokenStock", "Broken items cannot be stocked.");
    public static final String SLOT_TAKE_FAILED = Messages.define("shop.slotTakeFailed", "That vault slot could not be taken. The vault may be sealed by a breach.");
    public static final String AUDIT_STOCKED_VAULT = Messages.define("shop.auditStockedVault", "Stocked {lot} from vault page {page} slot {slot} at {price} each");
    public static final String STOCKED_VAULT = Messages.define("shop.stockedVault", "Stocked {lot} from the vault at {price} each.");
    public static final String STOCK_OFFLINE = Messages.define("shop.stockOffline", "You need to be online to stock the shop.");
    public static final String HOLD_TO_STOCK = Messages.define("shop.holdToStock", "Hold the item you want to stock in your hand.");
    public static final String AUDIT_STOCKED = Messages.define("shop.auditStocked", "Stocked {lot} at {price} each");
    public static final String STOCKED = Messages.define("shop.stocked", "Stocked {lot} in the faction shop at {price} each.");
    public static final String STOCKING = Messages.define("shop.stocking", "Stocking the shop.");
    public static final String TAKE_OFFICERS = Messages.define("shop.takeOfficers", "Only officers and the leader can take items out of the faction shop.");
    public static final String TAKE_OFFLINE = Messages.define("shop.takeOffline", "You need to be online to take items out of the shop.");
    public static final String ENTRY_GONE = Messages.define("shop.entryGone", "That entry is no longer in the shop.");
    public static final String TAKE_OWN_ONLY_RANK = Messages.define("shop.takeOwnOnlyRank", "Your rank cannot use the faction vault, so you can only take back what you stocked.");
    public static final String TAKE_OWN_ONLY_NEW = Messages.define("shop.takeOwnOnlyNew", "New members can only take back what they stocked until their hold ends.");
    public static final String INVENTORY_FULL = Messages.define("shop.inventoryFull", "Your inventory is full. Make some room and try again.");
    public static final String AUDIT_TOOK = Messages.define("shop.auditTook", "Took {lot} out of the faction shop");
    public static final String TOOK = Messages.define("shop.took", "Took {lot} out of the faction shop.");
    public static final String TOOK_MAILED = Messages.define("shop.tookMailed", "Some did not fit and went to your market mailbox.");
    public static final String TAKING = Messages.define("shop.taking", "Taking the items back.");
    public static final String BUY_OFFLINE_FACTION = Messages.define("shop.buyOfflineFaction", "You need to be online to buy from a shop.");
    public static final String OUT_OF_STOCK = Messages.define("shop.outOfStock", "That item is no longer in stock.");
    public static final String MEMO_BOUGHT_FROM = Messages.define("shop.memoBoughtFrom", "Bought {lot} from the shop of {faction}");
    public static final String MEMO_BANK_SALE = Messages.define("shop.memoBankSale", "{buyer} bought {lot} from the faction shop");
    public static final String BANK_REFUSED = Messages.define("shop.bankRefused", "The faction bank could not take the payment so nothing was bought.");
    public static final String MEMO_REFUND_TO = Messages.define("shop.memoRefundTo", "Faction shop refund to {buyer}");
    public static final String OWED = Messages.define("shop.owed", "{amount} for the items that did not fit could not be paid back. Ask {faction} to refund you.");
    public static final String SOLD_FROM_SHOP = Messages.define("shop.soldFromShop", "{buyer} bought {lot} from your faction shop for {amount}.");
    public static final String BOUGHT_FROM = Messages.define("shop.boughtFrom", "You bought {lot} from {faction} for {amount}.");
    public static final String BUYING = Messages.define("shop.buying", "Buying.");
    public static final String MEMO_FACTION_REFUND = Messages.define("shop.memoFactionRefund", "Faction shop refund");
    public static final String NO_SUCH_SHOP_CATEGORY = Messages.define("shop.noSuchShopCategory", "There is no shop category called {category}.");
    public static final String OPEN_FAILED = Messages.define("shop.openFailed", "The shop could not be opened right now.");
}
