package dev.ryvrook.dominion.chat;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.ExportService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Routes chat into channels and colours every tag by the viewer's relation to the sender. */
public final class ChatServiceImpl implements ChatService {
    private static final String SEPARATOR = " » ";
    private static final String CONTENT_COLOR = "#ffffff";
    private static final String NATION_COLOR = "#d99cff";

    private final DominionContext ctx;

    public ChatServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    private RelationService relations() {
        return ctx.service(RelationService.class);
    }

    // ---- chat event --------------------------------------------------------

    /**
     * First pass on the async chat thread. Picks the channel and routes private channels, which
     * cancels the event. Public lines are left alone here and finished in handlePublic.
     */
    void handle(PlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PlayerRef sender = event.getSender();
        if (sender == null) {
            return;
        }
        // Fail closed. A line from a player who talks in a private channel is cancelled before any
        // work that can throw, and any failure while routing cancels the line too, so a private
        // message can never fall through to the public pass.
        PlayerData known = ctx.repo().playerIfLoaded(sender.getUuid());
        if (known != null && known.channel() != PlayerData.Channel.PUBLIC) {
            event.setCancelled(true);
        }
        try {
            route(event, sender);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            Msg.send(sender, Msg.bad(Messages.get(ChatText.NOT_DELIVERED)));
            throw e;
        }
    }

    private void route(PlayerChatEvent event, PlayerRef sender) {
        UUID uuid = sender.getUuid();
        String content = event.getContent() == null ? "" : event.getContent();
        PlayerData.Channel channel = ctx.player(uuid).channel();

        Faction faction = factions().factionOf(uuid);
        String factionPrefix = ctx.settings().text("chat.factionPrefix");
        String allyPrefix = ctx.settings().text("chat.allyPrefix");
        String nationPrefix = ctx.settings().text("chat.nationPrefix");
        if (faction != null) {
            if (startsWithPrefix(content, factionPrefix)) {
                channel = PlayerData.Channel.FACTION;
                content = content.substring(factionPrefix.length()).trim();
            } else if (startsWithPrefix(content, allyPrefix)) {
                channel = PlayerData.Channel.ALLY;
                content = content.substring(allyPrefix.length()).trim();
            } else if (startsWithPrefix(content, nationPrefix) && nationOf(faction) != null) {
                channel = PlayerData.Channel.NATION;
                content = content.substring(nationPrefix.length()).trim();
            }
            if (channel == PlayerData.Channel.NATION && nationOf(faction) == null) {
                // The nation is gone or the service was removed, so ally chat takes over.
                channel = PlayerData.Channel.ALLY;
            }
            if (channel == PlayerData.Channel.OFFICER) {
                Rank rank = faction.rankOf(uuid);
                if (rank == null || !rank.atLeast(Rank.OFFICER)) {
                    // Demoted since choosing the channel, so fall back to faction chat.
                    channel = PlayerData.Channel.FACTION;
                }
            }
        } else {
            channel = PlayerData.Channel.PUBLIC;
        }
        if (content.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        if (channel == PlayerData.Channel.PUBLIC) {
            // Finished by handlePublic once every other chat plugin had its turn. Undo the early
            // cancel, the event was not cancelled when Dominion first saw it.
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        if (channel == PlayerData.Channel.NATION) {
            sendNation(sender, faction, content);
        } else {
            sendChannel(sender, faction, channel, content);
        }
    }

    /**
     * Second pass, registered last. Anything still alive here is public chat. In relation style the
     * line is sent to each target with the tag in that viewer's relation colour, which needs the
     * event cancelled. In passthrough style the tag is added to the formatter and the event goes on,
     * so relays such as a Discord bridge still see it.
     */
    void handlePublic(PlayerChatEvent event) {
        if (event.isCancelled() || event.getSender() == null) {
            return;
        }
        PlayerRef sender = event.getSender();
        String content = event.getContent() == null ? "" : event.getContent();
        if (content.isBlank()) {
            return;
        }
        Faction faction = factions().factionOf(sender.getUuid());
        PlayerChatEvent.Formatter base = event.getFormatter();
        boolean ownFormat = base == null || base == PlayerChatEvent.DEFAULT_FORMATTER;
        boolean showTag = ctx.settings().bool("chat.showTag");
        if ("passthrough".equalsIgnoreCase(ctx.settings().text("chat.publicFormat"))) {
            // The engine calls the formatter only for a line it delivers, so the line is logged and
            // recorded for the map there. A plugin that cancels after Dominion keeps it off both.
            Message tag = faction == null || !showTag ? null
                    : Msg.colored(plainTag(faction.tag()), safeColor(faction.color(), Relation.NEUTRAL.color()));
            PlayerChatEvent.Formatter inner = base == null ? PlayerChatEvent.DEFAULT_FORMATTER : base;
            String tagText = faction == null ? "" : faction.tag();
            java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
            event.setFormatter((s, c) -> {
                if (once.compareAndSet(false, true)) {
                    log((faction == null ? "" : plainTag(tagText)) + s.getUsername() + SEPARATOR + c, false);
                    recordPublic(s.getUsername(), tagText, c);
                }
                Message line = inner.format(s, c);
                return tag == null ? line : Message.join(tag, line);
            });
            return;
        }
        event.setCancelled(true);
        sendPublic(sender, event.getTargets(), content, faction, showTag, ownFormat ? null : base);
    }

    private static String safeColor(String color, String fallback) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : fallback;
    }

    private static boolean startsWithPrefix(String content, String prefix) {
        return prefix != null && !prefix.isEmpty() && content.startsWith(prefix) && content.length() > prefix.length();
    }

    /**
     * Sends a public line to each target with the tag coloured for that viewer, or without a tag when
     * chat.showTag is off. With a formatter
     * from another plugin, such as a title or rank prefix, its output follows the tag in place of
     * Dominion's own name and separator.
     */
    private void sendPublic(PlayerRef sender, List<PlayerRef> targets, String content, Faction faction, boolean showTag,
                            PlayerChatEvent.Formatter other) {
        UUID uuid = sender.getUuid();
        Message line;
        if (other != null) {
            line = other.format(sender, content);
        } else {
            line = Message.join(Msg.colored(sender.getUsername(), Msg.TEXT), Msg.muted(SEPARATOR), Msg.colored(content, CONTENT_COLOR));
        }
        if (targets != null) {
            for (PlayerRef target : targets) {
                if (target == null) {
                    continue;
                }
                target.sendMessage(faction == null || !showTag ? line : Message.join(tagFor(target.getUuid(), uuid), line));
            }
        }
        String tag = faction == null ? "" : plainTag(faction.tag());
        log(tag + sender.getUsername() + SEPARATOR + content, false);
        recordPublic(sender.getUsername(), faction == null ? "" : faction.tag(), content);
    }

    /** Hands a delivered public line to the export module so the web map snapshot carries it. */
    private void recordPublic(String name, String tag, String content) {
        ExportService export = ctx.serviceOrNull(ExportService.class);
        if (export == null) {
            return;
        }
        try {
            export.recordChat(name, tag, content);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not record chat for the map");
        }
    }

    private void sendChannel(PlayerRef sender, Faction faction, PlayerData.Channel channel, String content) {
        String label;
        String color;
        List<UUID> recipients;
        switch (channel) {
            case ALLY -> {
                label = Messages.get(ChatText.LABEL_ALLY);
                color = Relation.ALLY.color();
                recipients = allyAudience(faction);
            }
            case OFFICER -> {
                label = Messages.get(ChatText.LABEL_OFFICER);
                color = Msg.GOLD;
                recipients = officers(faction);
            }
            default -> {
                label = Messages.get(ChatText.LABEL_FACTION);
                color = Relation.OWN.color();
                recipients = memberIds(faction);
            }
        }
        Rank rank = faction.rankOf(sender.getUuid());
        String rankPrefix = rank == null ? "" : rank.prefix();
        for (UUID id : recipients) {
            PlayerRef target = Players.online(id);
            if (target == null) {
                continue;
            }
            Message msg = Message.join(Msg.colored(label + " ", color).bold(true),
                    channel == PlayerData.Channel.ALLY ? tagFor(id, sender.getUuid()) : Message.raw(""),
                    Msg.colored(rankPrefix + sender.getUsername(), color),
                    Msg.muted(SEPARATOR), Msg.colored(content, color));
            target.sendMessage(msg);
        }
        log(label + " " + plainTag(faction.tag()) + sender.getUsername() + SEPARATOR + content, true);
    }

    /** Sends to every online member of every faction in the sender's nation. */
    private void sendNation(PlayerRef sender, Faction faction, String content) {
        Nation n = nationOf(faction);
        if (n == null) {
            sendChannel(sender, faction, PlayerData.Channel.ALLY, content);
            return;
        }
        Rank rank = faction.rankOf(sender.getUuid());
        String rankPrefix = rank == null ? "" : rank.prefix();
        String label = Messages.get(ChatText.LABEL_NATION, "tag", n.tag()) + " ";
        for (UUID id : nationAudience(n)) {
            PlayerRef target = Players.online(id);
            if (target == null) {
                continue;
            }
            target.sendMessage(Message.join(Msg.colored(label, NATION_COLOR).bold(true), tagFor(id, sender.getUuid()),
                    Msg.colored(rankPrefix + sender.getUsername(), NATION_COLOR),
                    Msg.muted(SEPARATOR), Msg.colored(content, NATION_COLOR)));
        }
        log(label + plainTag(faction.tag()) + sender.getUsername() + SEPARATOR + content, true);
    }

    private List<UUID> nationAudience(Nation n) {
        List<UUID> out = new ArrayList<>();
        for (UUID fid : n.members()) {
            Faction member = factions().byId(fid);
            if (member != null) {
                out.addAll(memberIds(member));
            }
        }
        return out;
    }

    /** The faction's nation when the nation service is installed, otherwise null. */
    private Nation nationOf(Faction faction) {
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null || faction == null) {
            return null;
        }
        try {
            return nations.nationOf(faction);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- nation channel ------------------------------------------------------

    /** True when the player talks in nation chat and their faction is still in a nation. */
    public boolean inNationChat(UUID player) {
        PlayerData pd = ctx.repo().playerIfLoaded(player);
        return pd != null && pd.channel() == PlayerData.Channel.NATION && nationOf(factions().factionOf(player)) != null;
    }

    /** Switches the player to nation chat. */
    public Result setNationChannel(UUID player) {
        return setChannel(player, PlayerData.Channel.NATION);
    }

    /** Readable name of the channel the player talks in. Nation chat without a nation reads as ally chat. */
    public String currentChannelName(UUID player) {
        PlayerData.Channel ch = ctx.player(player).channel();
        if (ch == PlayerData.Channel.NATION && !inNationChat(player)) {
            ch = PlayerData.Channel.ALLY;
        }
        return describe(ch);
    }

    /**
     * Writes a chat line to the console. Faction, ally, officer and nation lines go to FINE unless
     * chat.logPrivate is on, so private channels stay out of the normal server log.
     */
    private void log(String line, boolean privateChannel) {
        Level level = privateChannel && !ctx.settings().bool("chat.logPrivate") ? Level.FINE : Level.INFO;
        ctx.logger().at(level).log("%s", line);
    }

    private String plainTag(String tag) {
        return bracketed() ? "[" + tag + "] " : tag + " ";
    }

    private boolean bracketed() {
        return !"plain".equalsIgnoreCase(ctx.settings().text("chat.tagStyle"));
    }

    private static List<UUID> memberIds(Faction faction) {
        List<UUID> out = new ArrayList<>();
        for (Member m : faction.members()) {
            out.add(m.uuid());
        }
        return out;
    }

    private static List<UUID> officers(Faction faction) {
        List<UUID> out = new ArrayList<>();
        for (Member m : faction.members()) {
            Rank r = faction.rankOf(m.uuid());
            if (r != null && r.atLeast(Rank.OFFICER)) {
                out.add(m.uuid());
            }
        }
        return out;
    }

    /** Own members first, then members of every allied faction. */
    private List<UUID> allyAudience(Faction faction) {
        List<UUID> out = memberIds(faction);
        for (Faction ally : allies(faction)) {
            out.addAll(memberIds(ally));
        }
        return out;
    }

    private List<Faction> allies(Faction faction) {
        List<Faction> out = new ArrayList<>();
        for (Map.Entry<UUID, Relation> e : faction.relations().entrySet()) {
            if (e.getValue() != Relation.ALLY) {
                continue;
            }
            Faction other = factions().byId(e.getKey());
            if (other != null && relations().between(faction, other) == Relation.ALLY) {
                out.add(other);
            }
        }
        return out;
    }

    // ---- ChatService -------------------------------------------------------

    @Override
    public Result setChannel(UUID player, PlayerData.Channel channel) {
        if (channel == null) {
            return Result.fail(Messages.get(ChatText.CHANNELS));
        }
        Faction faction = factions().factionOf(player);
        if (channel != PlayerData.Channel.PUBLIC && faction == null) {
            return Result.fail(Messages.get(ChatText.CHANNEL_NEEDS_FACTION, "channel", describe(channel)));
        }
        if (channel == PlayerData.Channel.OFFICER) {
            Rank r = faction.rankOf(player);
            if (r == null || !r.atLeast(Rank.OFFICER)) {
                return Result.fail(Messages.get(ChatText.OFFICER_CHAT_ONLY));
            }
        }
        if (channel == PlayerData.Channel.NATION) {
            if (ctx.serviceOrNull(NationService.class) == null) {
                return Result.fail(Messages.get(ChatText.NATIONS_OFF));
            }
            if (nationOf(faction) == null) {
                return Result.fail(Messages.get(ChatText.NOT_IN_NATION));
            }
        }
        ctx.player(player).setChannel(channel);
        return Result.pass(Messages.get(ChatText.CHANNEL_SET, "channel", describe(channel)));
    }

    @Override
    public PlayerData.Channel cycleChannel(UUID player) {
        PlayerData pd = ctx.player(player);
        Faction faction = factions().factionOf(player);
        PlayerData.Channel next;
        if (faction == null) {
            next = PlayerData.Channel.PUBLIC;
        } else {
            // Public, faction, ally, then nation while in a nation, then officer for officers.
            Rank r = faction.rankOf(player);
            boolean officer = r != null && r.atLeast(Rank.OFFICER);
            boolean hasNation = nationOf(faction) != null;
            PlayerData.Channel afterNation = officer ? PlayerData.Channel.OFFICER : PlayerData.Channel.PUBLIC;
            next = switch (pd.channel()) {
                case PUBLIC -> PlayerData.Channel.FACTION;
                case FACTION -> PlayerData.Channel.ALLY;
                case ALLY -> hasNation ? PlayerData.Channel.NATION : afterNation;
                case NATION -> afterNation;
                case OFFICER -> PlayerData.Channel.PUBLIC;
            };
        }
        pd.setChannel(next);
        return next;
    }

    static String describe(PlayerData.Channel channel) {
        return switch (channel) {
            case PUBLIC -> Messages.get(ChatText.CHANNEL_PUBLIC);
            case FACTION -> Messages.get(ChatText.CHANNEL_FACTION);
            case ALLY -> Messages.get(ChatText.CHANNEL_ALLY);
            case NATION -> Messages.get(ChatText.CHANNEL_NATION);
            case OFFICER -> Messages.get(ChatText.CHANNEL_OFFICER);
        };
    }

    @Override
    public void sendToFaction(Faction faction, Message message, boolean officersOnly) {
        if (faction == null || message == null) {
            return;
        }
        for (UUID id : officersOnly ? officers(faction) : memberIds(faction)) {
            Msg.send(Players.online(id), message);
        }
    }

    @Override
    public void sendToAllies(Faction faction, Message message) {
        if (faction == null || message == null) {
            return;
        }
        Map<UUID, Boolean> seen = new LinkedHashMap<>();
        for (UUID id : allyAudience(faction)) {
            if (seen.put(id, Boolean.TRUE) == null) {
                Msg.send(Players.online(id), message);
            }
        }
    }

    @Override
    public Message tagFor(UUID viewer, UUID sender) {
        Faction faction = sender == null ? null : factions().factionOf(sender);
        if (faction == null) {
            return Message.raw("");
        }
        Relation relation = viewer == null ? Relation.NEUTRAL : relations().betweenPlayers(viewer, sender);
        return Msg.colored(plainTag(faction.tag()), relation.color());
    }

    /**
     * A faction tag coloured by the viewer's relation to that faction, in the configured tag style.
     * The ui module uses it for territory banners so banners and chat colour tags the same way.
     */
    public Message factionTag(UUID viewer, Faction faction) {
        if (faction == null) {
            return Message.raw("");
        }
        Relation relation = viewer == null ? Relation.NEUTRAL : relations().playerToFaction(viewer, faction);
        return Msg.colored(plainTag(faction.tag()), (relation == null ? Relation.NEUTRAL : relation).color());
    }

    @Override
    public Result announce(UUID actor, String text) {
        FactionService fs = factions();
        Faction faction = fs.factionOf(actor);
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!fs.can(actor, FactionAction.ANNOUNCE)) {
            return Result.fail(Messages.get(ChatText.ANNOUNCE_NO_PERMISSION));
        }
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return Result.fail(Messages.get(ChatText.ANNOUNCEMENT_EMPTY));
        }
        Result stored = fs.setMotd(actor, clean);
        if (stored.failed()) {
            return stored;
        }
        Member author = faction.member(actor);
        String by = author == null ? Messages.get(ChatText.AN_OFFICER) : author.name();
        fs.audit(faction, actor, author == null ? "" : author.name(), "announce", Messages.get(ChatText.AUDIT_ANNOUNCEMENT));
        sendToFaction(faction, Message.join(Msg.prefix(), Msg.colored(Messages.get(ChatText.ANNOUNCEMENT_FROM, "tag", faction.tag(), "author", by), faction.color()).bold(true)), false);
        sendToFaction(faction, Msg.colored(clean, Msg.TEXT), false);
        return Result.pass(Messages.get(ChatText.ANNOUNCEMENT_POSTED));
    }

    @Override
    public void alert(Faction faction, Message message) {
        if (faction == null || message == null) {
            return;
        }
        for (Member m : faction.members()) {
            PlayerRef online = Players.online(m.uuid());
            if (online == null) {
                continue;
            }
            PlayerData pd = ctx.repo().playerIfLoaded(m.uuid());
            if (pd == null || pd.alerts()) {
                online.sendMessage(message);
            }
        }
    }

    // ---- login ---------------------------------------------------------------

    void onPlayerReady(PlayerRef player) {
        UUID uuid = player.getUuid();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                Faction faction = factions().factionOf(uuid);
                PlayerRef online = Players.online(uuid);
                if (faction == null || online == null || faction.motd().isBlank()) {
                    return;
                }
                online.sendMessage(Message.join(Msg.prefix(), Msg.colored(Messages.get(ChatText.MOTD_TITLE, "tag", faction.tag()), faction.color()).bold(true)));
                online.sendMessage(Msg.colored(faction.motd(), Msg.TEXT));
            } catch (Exception e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not send the message of the day");
            }
        }, 1, TimeUnit.SECONDS);
    }
}
