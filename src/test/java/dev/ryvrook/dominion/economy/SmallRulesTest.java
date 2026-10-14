package dev.ryvrook.dominion.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallRulesTest {
    @Test
    void stipendPaysWholeCentsAndKeepsTheRest() {
        assertEquals(0, DailyEconomy.payable(0.004, 0.01));
        assertEquals(0.01, DailyEconomy.payable(0.0166, 0.01));
        assertEquals(0.16, DailyEconomy.payable(0.1666, 0.01));
        assertEquals(2, DailyEconomy.payable(2.9, 1));
        assertEquals(0, DailyEconomy.payable(0.99, 1));
    }

    @Test
    void stipendCarryAddsUpToTheHourlyAmount() {
        double perMinute = 1.0 / 60.0;
        double carry = 0;
        double paid = 0;
        for (int minute = 0; minute < 60; minute++) {
            carry += perMinute;
            double pay = DailyEconomy.payable(carry, 0.01);
            paid += pay;
            carry -= pay;
        }
        assertEquals(1.0, Money.round(paid + carry), 1e-9);
        assertEquals(true, paid >= 0.99);
    }

    @Test
    void itemPaymentsRoundUpAndOwedItemsRoundDown() {
        assertEquals(0, ItemCurrency.itemsFor(0));
        assertEquals(0, ItemCurrency.itemsFor(-3));
        assertEquals(0, ItemCurrency.itemsFor(Double.NaN));
        assertEquals(3, ItemCurrency.itemsFor(3));
        assertEquals(3, ItemCurrency.itemsFor(2.5));
        assertEquals(3, ItemCurrency.itemsFor(3.0000001));
        assertEquals(0, ItemCurrency.wholeOwed(0.99));
        assertEquals(1, ItemCurrency.wholeOwed(0.9999999));
        assertEquals(2, ItemCurrency.wholeOwed(2.5));
        assertEquals(0, ItemCurrency.wholeOwed(-1));
    }
}
