package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.text.Messages;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Player wallets held by Coinage, the {@code coinage} provider. Free of Coinage types, it talks to Coinage through
 * the {@link CoinagePort} that {@code integration.coinage.CoinageLink} attaches once Coinage is up. Dominion
 * amounts are converted to minor units of the configured currency through its decimals, and an amount that does
 * not fit is refused. Every move names Dominion as the plugin and the plugin as the initiator.
 */
final class CoinageBridge implements BalanceProvider {
    private final Supplier<CoinagePort> port;
    private final Supplier<String> currency;
    private final DoubleFunction<String> format;

    /**
     * @param port     the attached port, or null while Coinage is not linked
     * @param currency the eco.coinageCurrency setting, blank for the primary currency
     * @param format   money formatting for messages
     */
    CoinageBridge(Supplier<CoinagePort> port, Supplier<String> currency, DoubleFunction<String> format) {
        this.port = port;
        this.currency = currency;
        this.format = format;
    }

    @Override
    public String name() {
        return "coinage";
    }

    /** The port while it is linked, else null. */
    CoinagePort live() {
        CoinagePort p = port.get();
        return p != null && p.bound() ? p : null;
    }

    /** The configured currency id, null for the primary. */
    String currency() {
        String c = currency.get();
        return c == null || c.isBlank() ? null : c.strip().toLowerCase(java.util.Locale.ROOT);
    }

    /** Digits after the point of the currency, or -1 when Coinage is not linked or has no such currency. */
    int decimals() {
        CoinagePort p = live();
        return p == null ? -1 : p.decimals(currency());
    }

    @Override
    public double balance(UUID player) {
        CoinagePort p = live();
        if (p == null || player == null) {
            return 0;
        }
        int d = p.decimals(currency());
        return d < 0 ? 0 : CoinageAmounts.toAmount(p.balance(player, currency()), d);
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        CoinagePort p = live();
        if (p == null) {
            return Result.fail(Messages.get(EconomyText.NOT_RESPONDING));
        }
        Converted c = convert(p, amount);
        if (c.failure != null) {
            return Result.fail(c.failure);
        }
        p.ensurePlayer(player);
        CoinagePort.Move m = p.withdraw(player, currency(), c.minor, reason);
        return m.ok() ? Result.pass() : Result.fail(reason(m, amount, () -> balance(player), null, currency(), format));
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        CoinagePort p = live();
        if (p == null) {
            return Result.fail(Messages.get(EconomyText.NOT_RESPONDING));
        }
        Converted c = convert(p, amount);
        if (c.failure != null) {
            return Result.fail(c.failure);
        }
        p.ensurePlayer(player);
        CoinagePort.Move m = p.deposit(player, currency(), c.minor, reason);
        return m.ok() ? Result.pass() : Result.fail(reason(m, amount, () -> balance(player), null, currency(), format));
    }

    @Override
    public void set(UUID player, double amount) {
        CoinagePort p = live();
        if (p == null) {
            return;
        }
        int d = p.decimals(currency());
        if (d >= 0) {
            p.ensurePlayer(player);
            p.set(player, currency(), CoinageAmounts.floorMinor(Math.max(0, amount), d), Messages.get(EconomyText.REASON_ADMIN_SET));
        }
    }

    @Override
    public boolean wholeUnits() {
        return decimals() == 0;
    }

    @Override
    public double step() {
        int d = decimals();
        return d < 0 ? 0.01 : CoinageAmounts.step(d);
    }

    /** Minor units or the reason the amount cannot move. */
    record Converted(long minor, String failure) {}

    Converted convert(CoinagePort p, double amount) {
        int d = p.decimals(currency());
        if (d < 0) {
            return new Converted(0, Messages.get(EconomyText.COINAGE_NO_CURRENCY, "currency", String.valueOf(currency())));
        }
        OptionalLong minor = CoinageAmounts.toMinor(amount, d);
        if (minor.isEmpty()) {
            return new Converted(0, tooFine(amount, d));
        }
        return new Converted(minor.getAsLong(), null);
    }

    /** Why an amount does not fit a currency with these decimals. */
    static String tooFine(double amount, int decimals) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            return Messages.get(EconomyText.INVALID_AMOUNT);
        }
        if (decimals >= 2) {
            return Messages.get(EconomyText.AMOUNT_TOO_LARGE);
        }
        return decimals == 0 ? Messages.get(EconomyText.COINAGE_WHOLE)
                : Messages.get(EconomyText.COINAGE_DECIMALS, "decimals", decimals);
    }

    /**
     * The sentence a player reads for a failed move. A short wallet reads as need and have, a short bank as what
     * the bank holds, which {@code bank} gives when the paying side is a faction bank.
     *
     * @param amount the Dominion amount that was asked for
     * @param have   the paying side's balance, read only for a short wallet
     * @param bank   the sentence for a short bank, or null when the paying side is a wallet
     * @param currency the configured currency id for an unknown currency, null for the primary
     */
    static String reason(CoinagePort.Move m, double amount, java.util.function.DoubleSupplier have, String bank,
                         String currency, DoubleFunction<String> format) {
        return switch (m.failure()) {
            case INSUFFICIENT_FUNDS -> bank != null ? bank
                    : Messages.get(EconomyText.NEED_HAVE, "amount", format.apply(amount), "have", format.apply(have.getAsDouble()));
            case ACCOUNT_LOCKED -> Messages.get(EconomyText.COINAGE_LOCKED);
            case ACCOUNT_FROZEN -> Messages.get(EconomyText.COINAGE_FROZEN);
            case MAX_BALANCE -> Messages.get(EconomyText.COINAGE_MAX_BALANCE);
            case UNKNOWN_CURRENCY -> Messages.get(EconomyText.COINAGE_NO_CURRENCY, "currency", currency == null ? "" : currency);
            case ACCOUNT_NOT_FOUND -> Messages.get(EconomyText.COINAGE_NO_ACCOUNT);
            case INVALID_AMOUNT -> Messages.get(EconomyText.INVALID_AMOUNT);
            case STOPPING -> Messages.get(EconomyText.NOT_RESPONDING);
            case CANCELLED -> m.message() == null || m.message().isBlank() ? Messages.get(EconomyText.PAYMENT_REFUSED) : m.message();
            case NONE, NOT_PERMITTED, SAME_ACCOUNT, ALREADY_REVERSED -> Messages.get(EconomyText.PAYMENT_REFUSED);
        };
    }
}
