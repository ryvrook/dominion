package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.text.Msg;

import java.util.List;
import java.util.UUID;

/** Faction bank commands under /f plus the personal /balance, /pay and /baltop. */
final class EconomyCommands extends Section {

    EconomyCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    /** Returns the economy or null after telling the caller it is off. */
    private EconomyService eco(Call c) {
        EconomyService eco = economy();
        if (!eco.enabled()) {
            c.bad("The economy is turned off on this server.");
            return null;
        }
        return eco;
    }

    @Override
    void register(PlayerCmd root) {
        root.sub(new PlayerCmd("bank", "Show your faction bank.")).handler(c -> {
            EconomyService eco = eco(c);
            Faction f = eco == null ? null : requireFaction(c);
            if (f != null) {
                c.info(f.tag() + " has " + eco.format(f.bank()) + " in the bank.");
            }
        });

        PlayerCmd deposit = root.sub(new PlayerCmd("deposit", "Put money into the faction bank."));
        RequiredArg<Double> depositAmount = deposit.withRequiredArg("amount", "Amount", ArgTypes.DOUBLE);
        deposit.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(depositAmount))) {
                c.reply(economy().bankDeposit(c.uuid(), c.arg(depositAmount)));
            }
        });

        PlayerCmd withdraw = root.sub(new PlayerCmd("withdraw", "Take money from the faction bank."));
        RequiredArg<Double> withdrawAmount = withdraw.withRequiredArg("amount", "Amount", ArgTypes.DOUBLE);
        withdraw.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(withdrawAmount))) {
                c.reply(economy().bankWithdraw(c.uuid(), c.arg(withdrawAmount)));
            }
        });

        PlayerCmd pay = root.sub(new PlayerCmd("pay", "Pay another faction from your bank."));
        RequiredArg<String> payTag = pay.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        RequiredArg<Double> payAmount = pay.withRequiredArg("amount", "Amount", ArgTypes.DOUBLE);
        pay.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(payAmount))) {
                c.reply(economy().bankPay(c.uuid(), c.arg(payTag), c.arg(payAmount)));
            }
        });
    }

    /** Top level commands that live outside /f. */
    List<PlayerCmd> topLevel() {
        PlayerCmd balance = new PlayerCmd("balance", "Show your balance.");
        balance.addAliases("bal", "money");
        balance.handler(c -> {
            EconomyService eco = eco(c);
            if (eco != null) {
                c.info("You have " + eco.format(eco.balance(c.uuid())) + ".");
            }
        });
        PlayerCmd balanceOther = new PlayerCmd("Show another player's balance.");
        RequiredArg<String> who = balanceOther.withRequiredArg("player", "Player name", ArgTypes.STRING);
        balanceOther.handler(c -> {
            EconomyService eco = eco(c);
            Targets.Target t = eco == null ? null : target(c, c.arg(who));
            if (t != null) {
                c.info(t.name() + " has " + eco.format(eco.balance(t.uuid())) + ".");
            }
        });
        balance.variant(balanceOther);

        PlayerCmd pay = new PlayerCmd("pay", "Send money to another player.");
        RequiredArg<String> payWho = pay.withRequiredArg("player", "Player name", ArgTypes.STRING);
        RequiredArg<Double> payAmount = pay.withRequiredArg("amount", "Amount", ArgTypes.DOUBLE);
        pay.handler(c -> {
            EconomyService eco = eco(c);
            if (eco == null || !positive(c, c.arg(payAmount))) {
                return;
            }
            Targets.Target t = target(c, c.arg(payWho));
            if (t == null) {
                return;
            }
            if (t.uuid().equals(c.uuid())) {
                c.bad("You cannot pay yourself.");
                return;
            }
            c.reply(eco.transfer(c.uuid(), t.uuid(), c.arg(payAmount), "pay"));
        });

        PlayerCmd baltop = new PlayerCmd("baltop", "Show the richest players.");
        baltop.handler(c -> {
            EconomyService eco = eco(c);
            if (eco == null) {
                return;
            }
            List<UUID> top = eco.richestPlayers(10);
            if (top.isEmpty()) {
                c.info("Nobody has any money yet.");
                return;
            }
            c.info("Richest players");
            int rank = 1;
            for (UUID uuid : top) {
                boolean self = uuid.equals(c.uuid());
                c.send(Message.join(Msg.muted(" " + rank + ". "),
                        Msg.colored(targets.nameOf(uuid), self ? Msg.GOLD : Msg.TEXT).bold(self),
                        Msg.muted("  " + eco.format(eco.balance(uuid)))));
                rank++;
            }
        });
        return List.of(balance, pay, baltop);
    }

    private static boolean positive(Call c, Double amount) {
        if (amount == null || amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            c.bad("The amount must be more than zero.");
            return false;
        }
        return true;
    }
}
