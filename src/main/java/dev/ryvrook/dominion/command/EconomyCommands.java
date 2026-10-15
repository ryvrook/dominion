package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.text.Messages;
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
            c.bad(Messages.get(CommandText.THE_ECONOMY_IS_TURNED_OFF_ON));
            return null;
        }
        return eco;
    }

    @Override
    void register(PlayerCmd root) {
        root.sub(new PlayerCmd("bank", Messages.get(CommandText.DESC_SHOW_YOUR_FACTION_BANK))).handler(c -> {
            EconomyService eco = eco(c);
            Faction f = eco == null ? null : requireFaction(c);
            if (f != null) {
                c.info(Messages.get(CommandText.BANK_BALANCE, "tag", f.tag(), "amount", eco.format(f.bank())));
            }
        });

        PlayerCmd deposit = root.sub(new PlayerCmd("deposit", Messages.get(CommandText.DESC_PUT_MONEY_INTO_THE_FACTION_BANK)));
        RequiredArg<Double> depositAmount = deposit.withRequiredArg("amount", Messages.get(CommandText.ARG_AMOUNT), ArgTypes.DOUBLE);
        deposit.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(depositAmount))) {
                c.reply(economy().bankDeposit(c.uuid(), c.arg(depositAmount)));
            }
        });

        PlayerCmd withdraw = root.sub(new PlayerCmd("withdraw", Messages.get(CommandText.DESC_TAKE_MONEY_FROM_THE_FACTION_BANK)));
        RequiredArg<Double> withdrawAmount = withdraw.withRequiredArg("amount", Messages.get(CommandText.ARG_AMOUNT), ArgTypes.DOUBLE);
        withdraw.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(withdrawAmount))) {
                c.reply(economy().bankWithdraw(c.uuid(), c.arg(withdrawAmount)));
            }
        });

        PlayerCmd pay = root.sub(new PlayerCmd("pay", Messages.get(CommandText.DESC_PAY_ANOTHER_FACTION_FROM_YOUR_BANK)));
        RequiredArg<String> payTag = pay.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        RequiredArg<Double> payAmount = pay.withRequiredArg("amount", Messages.get(CommandText.ARG_AMOUNT), ArgTypes.DOUBLE);
        pay.handler(c -> {
            if (eco(c) != null && positive(c, c.arg(payAmount))) {
                c.reply(economy().bankPay(c.uuid(), c.arg(payTag), c.arg(payAmount)));
            }
        });
    }

    /** Top level commands that live outside /f. */
    List<PlayerCmd> topLevel() {
        PlayerCmd balance = new PlayerCmd("balance", Messages.get(CommandText.DESC_SHOW_YOUR_BALANCE));
        balance.addAliases("bal", "money");
        balance.handler(c -> {
            EconomyService eco = eco(c);
            if (eco != null) {
                c.info(Messages.get(CommandText.YOUR_BALANCE, "amount", eco.format(eco.balance(c.uuid()))));
            }
        });
        PlayerCmd balanceOther = new PlayerCmd(Messages.get(CommandText.DESC_SHOW_ANOTHER_PLAYERS_BALANCE));
        RequiredArg<String> who = balanceOther.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        balanceOther.handler(c -> {
            EconomyService eco = eco(c);
            Targets.Target t = eco == null ? null : target(c, c.arg(who));
            if (t != null) {
                c.info(Messages.get(CommandText.PLAYER_BALANCE, "name", t.name(), "amount", eco.format(eco.balance(t.uuid()))));
            }
        });
        balance.variant(balanceOther);

        PlayerCmd pay = new PlayerCmd("pay", Messages.get(CommandText.DESC_SEND_MONEY_TO_ANOTHER_PLAYER));
        RequiredArg<String> payWho = pay.withRequiredArg("player", Messages.get(CommandText.ARG_PLAYER_NAME), ArgTypes.STRING);
        RequiredArg<Double> payAmount = pay.withRequiredArg("amount", Messages.get(CommandText.ARG_AMOUNT), ArgTypes.DOUBLE);
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
                c.bad(Messages.get(CommandText.YOU_CANNOT_PAY_YOURSELF));
                return;
            }
            c.reply(eco.transfer(c.uuid(), t.uuid(), c.arg(payAmount), "pay"));
        });

        PlayerCmd baltop = new PlayerCmd("baltop", Messages.get(CommandText.DESC_SHOW_THE_RICHEST_PLAYERS));
        baltop.handler(c -> {
            EconomyService eco = eco(c);
            if (eco == null) {
                return;
            }
            List<UUID> top = eco.richestPlayers(10);
            if (top.isEmpty()) {
                c.info(Messages.get(CommandText.NOBODY_HAS_ANY_MONEY_YET));
                return;
            }
            c.info(Messages.get(CommandText.RICHEST_PLAYERS));
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
            c.bad(Messages.get(CommandText.THE_AMOUNT_MUST_BE_MORE_THAN));
            return false;
        }
        return true;
    }
}
