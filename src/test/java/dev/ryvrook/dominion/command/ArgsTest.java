package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.Tokenizer;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Command parsing. The first tests pin down how the 0.7.1 engine tokenizer and argument types
 * treat quotes, apostrophes, commas and minus signs, since Dominion's own parsing builds on that.
 */
class ArgsTest {
    private static List<String> tokens(String input) {
        ParseResult r = new ParseResult();
        List<String> t = Tokenizer.parseArguments(input, r);
        return r.failed() ? null : t;
    }

    private static String string(String token) {
        return ArgTypes.STRING.parse(token, new ParseResult());
    }

    @Test
    void engineKeepsQuotesAroundAQuotedWord() {
        List<String> t = tokens("fa zone safe \"Spawn Town\"");
        assertEquals(List.of("fa", "zone", "safe", "\"Spawn Town\""), t);
        assertEquals("\"Spawn Town\"", string(t.get(3)), "the engine hands the quotes to the command");
        assertEquals("Spawn Town", Args.unquote(string(t.get(3))));
    }

    @Test
    void apostropheInsideAWordIsPlainText() {
        assertEquals(List.of("f", "name", "O'Neil", "Clan"), tokens("f name O'Neil Clan"));
        assertEquals("O'Neil", Args.unquote("O'Neil"));
        assertEquals("Bob's", Args.unquote("Bob's"));
    }

    @Test
    void wordStartingWithAnApostropheIsRefusedByTheEngine() {
        assertNull(tokens("f desc 'tis our land"), "unbalanced quote, the player has to quote the whole text");
        assertEquals(List.of("f", "desc", "\"'tis our land\""), tokens("f desc \"'tis our land\""));
    }

    @Test
    void negativeNumbersParseAsIntegers() {
        List<String> t = tokens("f claim radius -3");
        assertEquals("-3", t.get(3));
        assertEquals(-3, ArgTypes.INTEGER.parse(t.get(3), new ParseResult()));
        assertEquals(-3, Args.integer("-3"));
        assertEquals(12, Args.integer("+12"));
        assertNull(Args.integer("3.5"));
        assertNull(Args.integer(""));
    }

    @Test
    void unquoteHandlesEdgeCases() {
        assertNull(Args.unquote(null));
        assertEquals("", Args.unquote("\"\""));
        assertEquals("\"", Args.unquote("\""));
        assertEquals("a \"b\" c", Args.unquote("\"a \\\"b\\\" c\""));
        assertEquals("single", Args.unquote("'single'"));
        assertEquals("\"mixed'", Args.unquote("\"mixed'"));
        assertEquals("trim me", Args.unquote("  \" trim me \"  "));
        assertEquals("North Farm", Args.unquote("North Farm"));
    }

    @Test
    void amountsAcceptSeparatorsAndSuffixes() {
        assertEquals(1500, Args.amount("1,500"));
        assertEquals(1500, Args.amount("1_500"));
        assertEquals(2500, Args.amount("2.5k"));
        assertEquals(3_000_000, Args.amount("3M"));
        assertEquals(0.5, Args.amount(".5"));
        assertEquals(-4, Args.amount("-4"));
        assertTrue(Double.isNaN(Args.amount("NaN")));
        assertTrue(Double.isNaN(Args.amount("Infinity")));
        assertTrue(Double.isNaN(Args.amount("1e400")));
        assertTrue(Double.isNaN(Args.amount("ten")));
        assertTrue(Double.isNaN(Args.amount("")));
    }

    @Test
    void channelNamesAndShortcuts() {
        assertEquals(PlayerData.Channel.PUBLIC, SocialCommands.parseChannel("G"));
        assertEquals(PlayerData.Channel.FACTION, SocialCommands.parseChannel("fac"));
        assertEquals(PlayerData.Channel.ALLY, SocialCommands.parseChannel("allies"));
        assertEquals(PlayerData.Channel.OFFICER, SocialCommands.parseChannel("Officers"));
        assertEquals(PlayerData.Channel.NATION, SocialCommands.parseChannel("n"));
        assertNull(SocialCommands.parseChannel("shout"));
    }

    private static Faction faction() {
        UUID leader = UUID.randomUUID();
        Faction f = new Faction(UUID.randomUUID(), "Wolf", "Wolves", leader, 1L);
        f.putMember(new Member(leader, "Lead", Rank.LEADER, 1L));
        return f;
    }

    @Test
    void ranksByBuiltInOrCustomName() {
        Faction f = faction();
        assertEquals(Rank.OFFICER, ZoneCommands.parseRank(f, "officer"));
        assertEquals(Rank.MEMBER, ZoneCommands.parseRank(null, " MEMBER "));
        f.setRankName(Rank.RECRUIT, "Member");
        assertEquals(Rank.RECRUIT, ZoneCommands.parseRank(f, "member"), "a custom name wins over the built in one");
        assertNull(ZoneCommands.parseRank(f, "emperor"));
        assertNull(ZoneCommands.parseRank(f, null));
    }

    @Test
    void zonesByMultiWordNameOrId() {
        Faction f = faction();
        f.putZone(new Zone("z1", "North Farm"));
        assertEquals("z1", ZoneCommands.find(f, "north farm").id());
        assertEquals("z1", ZoneCommands.find(f, "  z1 ").id());
        assertEquals("z1", ZoneCommands.find(f, Args.unquote("\"North Farm\"")).id());
        assertNull(ZoneCommands.find(f, "South Farm"));
        assertEquals("South Farm", ZoneCommands.zoneId(f, " South Farm "));
    }

    @Test
    void idPrefixShortForm() {
        UUID id = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");
        assertEquals("abcdef12", IdPrefix.shortId(id));
    }

    @Test
    void confirmationsRunOnceAndExpire() {
        Confirmations c = new Confirmations();
        UUID p = UUID.randomUUID();
        int[] runs = {0};
        c.put(p, "x", () -> {
            runs[0]++;
            return dev.ryvrook.dominion.api.Result.pass("done");
        });
        assertTrue(c.confirm(p).success());
        assertFalse(c.confirm(p).success(), "a confirmation is used up");
        assertEquals(1, runs[0]);
    }
}
