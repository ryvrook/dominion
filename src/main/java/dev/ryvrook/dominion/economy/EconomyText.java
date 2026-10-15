package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.text.Messages;

/** Player facing sentences of the economy, editable in messages.json. */
public final class EconomyText {
    private EconomyText() {}

    public static final String DISABLED = Messages.define("economy.disabled", "The economy is disabled");
    public static final String FACTION_GONE = Messages.define("economy.factionGone", "That faction no longer exists");
    public static final String NEED_HAVE = Messages.define("economy.needHave", "You need {amount} but only have {have}");
    public static final String NEED_CARRY = Messages.define("economy.needCarry", "You need {amount} but only carry {have}");
    public static final String PAY_YOURSELF = Messages.define("economy.payYourself", "You cannot pay yourself");
    public static final String BANK_ONLY_HOLDS = Messages.define("economy.bankOnlyHolds", "The bank only holds {amount}");
    public static final String WITHDRAW_LEFT_TODAY = Messages.define("economy.withdrawLeftToday", "You can withdraw {amount} more today");
    public static final String BANK_OF_ONLY_HOLDS = Messages.define("economy.bankOfOnlyHolds", "The bank of {faction} only holds {amount}");
    public static final String CHARGE_BANK_SHORT = Messages.define("economy.chargeBankShort", "This costs {cost} but the bank of {faction} only holds {amount}");
    public static final String INVALID_AMOUNT = Messages.define("economy.invalidAmount", "That is not a valid amount");
    public static final String AMOUNT_NOT_POSITIVE = Messages.define("economy.amountNotPositive", "The amount must be more than zero");
    public static final String AMOUNT_TWO_DECIMALS = Messages.define("economy.amountTwoDecimals", "Amounts can have at most two decimals");
    public static final String AMOUNT_TOO_LARGE = Messages.define("economy.amountTooLarge", "That amount is too large");
    public static final String ITEMS_ONLINE = Messages.define("economy.itemsOnline", "You need to be online to pay with items");
    public static final String INVENTORY_UNREADABLE = Messages.define("economy.inventoryUnreadable", "Your inventory could not be read right now. Try again in a moment");
    public static final String INVENTORY_FULL = Messages.define("economy.inventoryFull", "Your inventory is full. {amount} more will arrive when you log in again or receive another payment");
    public static final String WHOLE_AMOUNTS = Messages.define("economy.wholeAmounts", "Only whole amounts can be moved because the currency is an item");
    public static final String PAYMENT_REFUSED = Messages.define("economy.paymentRefused", "The payment was refused by the economy");
    public static final String PAYMENT_NOT_ACCEPTED = Messages.define("economy.paymentNotAccepted", "The economy did not accept the payment");
    public static final String NOT_RESPONDING = Messages.define("economy.notResponding", "The economy is not responding right now");
    public static final String RECEIVED = Messages.define("economy.received", "Received {amount}");
    public static final String PAID = Messages.define("economy.paid", "Paid {amount}");
    public static final String SENT = Messages.define("economy.sent", "Sent {amount}");
    public static final String RANK_NO_DEPOSIT = Messages.define("economy.rankNoDeposit", "Your rank cannot deposit to the bank");
    public static final String RANK_NO_WITHDRAW = Messages.define("economy.rankNoWithdraw", "Your rank cannot withdraw from the bank");
    public static final String NEW_MEMBER_HOLD = Messages.define("economy.newMemberHold", "New members cannot withdraw until their hold ends in {time}");
    public static final String DEPOSITED_LOG = Messages.define("economy.depositedLog", "Deposited {amount}");
    public static final String DEPOSITED = Messages.define("economy.deposited", "Deposited {amount} to the bank");
    public static final String WITHDREW_LOG = Messages.define("economy.withdrewLog", "Withdrew {amount}");
    public static final String WITHDREW = Messages.define("economy.withdrew", "Withdrew {amount} from the bank");
    public static final String NO_FACTION_WITH_TAG = Messages.define("economy.noFactionWithTag", "No faction has that tag");
    public static final String PAY_OWN_FACTION = Messages.define("economy.payOwnFaction", "You cannot pay your own faction");
    public static final String PAID_TO = Messages.define("economy.paidTo", "Paid {amount} to {faction}");
    public static final String RECEIVED_FROM = Messages.define("economy.receivedFrom", "Received {amount} from {faction}");
    public static final String NO_FACTION_TO_CHARGE = Messages.define("economy.noFactionToCharge", "There is no faction to charge");
    public static final String PAID_FOR = Messages.define("economy.paidFor", "Paid {amount} for {reason}");
    public static final String PAID_FROM_BANK = Messages.define("economy.paidFromBank", "Paid {amount} from the bank of {faction}");
    public static final String NO_FACTION_TO_PAY = Messages.define("economy.noFactionToPay", "There is no faction to pay");
    public static final String RECEIVED_FOR = Messages.define("economy.receivedFor", "Received {amount} for {reason}");
    public static final String ADDED_TO_BANK = Messages.define("economy.addedToBank", "Added {amount} to the bank of {faction}");
    public static final String EMPTIED_FOR = Messages.define("economy.emptiedFor", "Emptied the bank of {amount} for {reason}");
    public static final String BOTH_MUST_EXIST = Messages.define("economy.bothMustExist", "Both factions must exist");
    public static final String PAY_ITSELF = Messages.define("economy.payItself", "A faction cannot pay itself");
    public static final String PAID_TO_FOR = Messages.define("economy.paidToFor", "Paid {amount} to {faction} for {reason}");
    public static final String RECEIVED_FROM_FOR = Messages.define("economy.receivedFromFor", "Received {amount} from {faction} for {reason}");
    public static final String MOVED = Messages.define("economy.moved", "Moved {amount} from {from} to {to}");
    public static final String UNNAMED_REASON = Messages.define("economy.unnamedReason", "an unnamed reason");
    public static final String DUES_COLLECTED = Messages.define("economy.duesCollected", "Collected {amount} in daily dues");
    public static final String DUES_COLLECTED_ONE_MISSED = Messages.define("economy.duesCollectedOneMissed", "Collected {amount} in daily dues and 1 member could not pay in full");
    public static final String DUES_COLLECTED_MISSED = Messages.define("economy.duesCollectedMissed", "Collected {amount} in daily dues and {count} members could not pay in full");
    public static final String UPKEEP_PAID_ONE = Messages.define("economy.upkeepPaidOne", "Paid {amount} upkeep for 1 claim");
    public static final String UPKEEP_PAID = Messages.define("economy.upkeepPaid", "Paid {amount} upkeep for {claims} claims");
    public static final String UPKEEP_PARTIAL_ONE_DAY = Messages.define("economy.upkeepPartialOneDay", "Upkeep of {amount} was not paid in full. The bank gave the {taken} it held and is now 1 day behind");
    public static final String UPKEEP_PARTIAL = Messages.define("economy.upkeepPartial", "Upkeep of {amount} was not paid in full. The bank gave the {taken} it held and is now {days} days behind");
    public static final String UPKEEP_UNPAID_ONE_DAY = Messages.define("economy.upkeepUnpaidOneDay", "Upkeep of {amount} was not paid. The bank is now 1 day behind");
    public static final String UPKEEP_UNPAID = Messages.define("economy.upkeepUnpaid", "Upkeep of {amount} was not paid. The bank is now {days} days behind");
    public static final String INTEREST_EARNED = Messages.define("economy.interestEarned", "Earned {amount} in interest");

    // Reasons sent with a payment to the server economy, shown in its own history.
    public static final String REASON_STIPEND = Messages.define("economy.reasonStipend", "Playtime stipend");
    public static final String REASON_REFUND_FAILED = Messages.define("economy.reasonRefundFailed", "Refund of a failed payment");
    public static final String REASON_BANK_DEPOSIT = Messages.define("economy.reasonBankDeposit", "Faction bank deposit");
    public static final String REASON_REFUND_DISBANDED = Messages.define("economy.reasonRefundDisbanded", "Refund of a deposit to a disbanded faction");
    public static final String REASON_BANK_WITHDRAWAL = Messages.define("economy.reasonBankWithdrawal", "Faction bank withdrawal");
    public static final String REASON_DUES = Messages.define("economy.reasonDues", "Faction dues");
    public static final String REASON_ADMIN_SET = Messages.define("economy.reasonAdminSet", "Admin set");
    public static final String REASON_MIGRATION = Messages.define("economy.reasonMigration", "Dominion balance migration");

    // Coinage brief. Wallets and faction banks held in Coinage.
    public static final String COINAGE_WHOLE = Messages.define("economy.coinageWhole", "Only whole amounts can be moved in this currency");
    public static final String COINAGE_DECIMALS = Messages.define("economy.coinageDecimals", "Amounts can have at most {decimals} decimal in this currency");
    public static final String COINAGE_LOCKED = Messages.define("economy.coinageLocked", "That account is locked and can only receive money");
    public static final String COINAGE_FROZEN = Messages.define("economy.coinageFrozen", "That account is frozen");
    public static final String COINAGE_MAX_BALANCE = Messages.define("economy.coinageMaxBalance", "That would take the account over the most it may hold");
    public static final String COINAGE_NO_CURRENCY = Messages.define("economy.coinageNoCurrency", "The economy has no currency named {currency}");
    public static final String COINAGE_NO_ACCOUNT = Messages.define("economy.coinageNoAccount", "The economy account could not be found");
    public static final String COINAGE_NOT_MEMBER = Messages.define("economy.coinageNotMember", "Only members of the faction can take money from its bank");
    public static final String COINAGE_IN_LOG = Messages.define("economy.coinageInLog", "{amount} came in through Coinage from {from} for {reason}");
    public static final String COINAGE_OUT_LOG = Messages.define("economy.coinageOutLog", "{amount} went out through Coinage to {to} for {reason}");
    public static final String COINAGE_SET_LOG = Messages.define("economy.coinageSetLog", "The bank was set to {amount} through Coinage");
    public static final String COINAGE_SOMEONE = Messages.define("economy.coinageSomeone", "the economy");
    public static final String REASON_BANK_TO_COINAGE = Messages.define("economy.reasonBankToCoinage", "Faction bank moved to Coinage");
    public static final String REASON_BANK_FROM_COINAGE = Messages.define("economy.reasonBankFromCoinage", "Faction bank moved back to Dominion");
    public static final String REASON_BANK_PAYMENT = Messages.define("economy.reasonBankPayment", "Faction bank payment");
    public static final String REASON_UPKEEP = Messages.define("economy.reasonUpkeep", "Faction upkeep");
    public static final String REASON_INTEREST = Messages.define("economy.reasonInterest", "Faction bank interest");
}
