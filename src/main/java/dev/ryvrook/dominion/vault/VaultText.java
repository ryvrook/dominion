package dev.ryvrook.dominion.vault;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the faction vault, editable in messages.json. */
public final class VaultText {
    private VaultText() {}

    public static final String NEW_MEMBER_HOLD = Messages.define("vault.newMemberHold", "New members cannot open the vault yet. Try again in {time}.");
    public static final String RANK_CANNOT_OPEN = Messages.define("vault.rankCannotOpen", "Your rank cannot open the faction vault.");
    public static final String ONE_PAGE = Messages.define("vault.onePage", "Your vault has one page.");
    public static final String PAGE_RANGE = Messages.define("vault.pageRange", "Your vault has pages 1 to {total}.");
    public static final String OFFICER_PAGE = Messages.define("vault.officerPage", "Only officers and the leader can open vault page {page}.");
    public static final String SEALED = Messages.define("vault.sealed", "The vault is sealed while your land is breached.");
    public static final String MUST_BE_ONLINE = Messages.define("vault.mustBeOnline", "You need to be online to open the vault.");
    public static final String OPENED = Messages.define("vault.opened", "Opened vault page {page}.");
    public static final String OPEN_FAILED = Messages.define("vault.openFailed", "The vault could not be opened right now.");
    public static final String OPENING = Messages.define("vault.opening", "Opening vault page {page}.");
    public static final String AUDIT_TOOK = Messages.define("vault.auditTook", "Took {count} {item} from vault page {page} slot {slot}");
    public static final String AUDIT_OPENED = Messages.define("vault.auditOpened", "Opened vault page {page}");
    public static final String RESCUED = Messages.define("vault.rescued", "The items left in the vault of {faction} went to your market mailbox.");
    public static final String EMPTIED_TO_MAILBOX = Messages.define("vault.emptiedToMailbox", "The faction vault was emptied into your market mailbox.");
    public static final String CLOSED_BY_BREACH = Messages.define("vault.closedByBreach", "Your land was breached, so the faction vault was closed. It stays sealed until the breach ends.");
}
