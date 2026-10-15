package dev.ryvrook.dominion.vault;

import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.market.Items;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Faction vaults as virtual chest pages. Each page is a SimpleItemContainer kept in memory and
 * shared by everyone who opens it, persisted to vault/factionId.json through ItemStack.CODEC on
 * every window close, once a minute while a page has changed, and on shutdown. Pages are numbered from 1.
 * A stored item that cannot be read back, for example while its item asset is missing, is kept as
 * its raw document under the page's Unreadable list, so a save never deletes it.
 */
public final class VaultServiceImpl implements dev.ryvrook.dominion.service.VaultService {
    private final DominionContext ctx;
    private final Path dir;
    private final Map<UUID, List<SimpleItemContainer>> vaults = new ConcurrentHashMap<>();
    /** Defender faction to siege id to the time the breach flag lapses on its own. */
    private final Map<UUID, Map<UUID, Long>> breaches = new ConcurrentHashMap<>();
    /** Factions whose pages changed since the last save. Filled by container change events. */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    /** Raw documents of stored items that could not be read, per faction and zero based page. */
    private final Map<UUID, Map<Integer, List<Document>>> unreadable = new ConcurrentHashMap<>();
    /** Vault windows this service opened, per player, so a breach can close them. */
    private final Map<UUID, List<OpenVault>> openWindows = new ConcurrentHashMap<>();

    /** A vault window a player has open and the faction whose vault it shows. */
    private record OpenVault(UUID factionId, ContainerWindow window) {}

    public VaultServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.dir = ctx.repo().root().resolve("vault");
    }

    @Override
    public int pages(Faction faction) {
        if (faction == null) {
            return 0;
        }
        return Math.max(1, ctx.settings().integer("vault.basePages") + faction.upgrade("vault"));
    }

    @Override
    public Result open(UUID player, int page) {
        if (!ctx.settings().bool("vault.enabled")) {
            return Result.fail(Messages.get(CommonText.FEATURE_OFF));
        }
        FactionService factions = ctx.service(FactionService.class);
        Faction f = factions.factionOf(player);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        Member m = f.member(player);
        if (m != null && m.onHold(Time.now())) {
            return Result.fail(Messages.get(VaultText.NEW_MEMBER_HOLD, "time", Time.remaining(m.holdUntil() - Time.now())));
        }
        if (!factions.can(player, FactionAction.VAULT)) {
            return Result.fail(Messages.get(VaultText.RANK_CANNOT_OPEN));
        }
        int total = pages(f);
        if (page < 1 || page > total) {
            return Result.fail(total == 1 ? Messages.get(VaultText.ONE_PAGE) : Messages.get(VaultText.PAGE_RANGE, "total", total));
        }
        if (page > ctx.settings().integer("vault.memberPages") && (m == null || !m.rank().atLeast(Rank.OFFICER))) {
            return Result.fail(Messages.get(VaultText.OFFICER_PAGE, "page", page));
        }
        if (breached(f.id())) {
            return Result.fail(Messages.get(VaultText.SEALED));
        }
        PlayerRef p = Players.online(player);
        if (p == null) {
            return Result.fail(Messages.get(VaultText.MUST_BE_ONLINE));
        }
        SimpleItemContainer container = page(f, page);
        String name = p.getUsername();
        CompletableFuture<Boolean> opened = Items.onWorld(p, (store, ref) -> {
            Player pc = store.getComponent(ref, Player.getComponentType());
            if (pc == null) {
                return false;
            }
            ContainerWindow window = new ContainerWindow(container);
            window.registerCloseEvent(e -> {
                untrack(player, window);
                onClose(f, container, player, name, page);
            });
            boolean ok = pc.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
            if (ok) {
                track(player, f.id(), window);
            }
            return ok;
        });
        if (opened.isDone()) {
            Boolean ok = opened.getNow(false);
            return Boolean.TRUE.equals(ok) ? Result.pass(Messages.get(VaultText.OPENED, "page", page))
                    : Result.fail(Messages.get(VaultText.OPEN_FAILED));
        }
        opened.whenComplete((ok, err) -> {
            if (err != null) {
                ctx.logger().at(Level.WARNING).withCause(err).log("Dominion vault window failed to open");
            }
            if (!Boolean.TRUE.equals(ok)) {
                Msg.send(p, Msg.bad(Messages.get(VaultText.OPEN_FAILED)));
            }
        });
        return Result.pass(Messages.get(VaultText.OPENING, "page", page));
    }

    @Override
    public ItemStack peekSlot(Faction faction, int page, int slot) {
        SimpleItemContainer c = existingPage(faction, page);
        if (c == null || slot < 1 || slot > c.getCapacity()) {
            return null;
        }
        ItemStack s = c.getItemStack((short) (slot - 1));
        return ItemStack.isEmpty(s) ? null : s;
    }

    /** Removes and returns a slot without an audit line. Refused while the vault is sealed by a breach. */
    @Override
    public ItemStack takeSlot(Faction faction, int page, int slot) {
        return takeSlot(faction, page, slot, null);
    }

    /**
     * Removes and returns the item in a slot, or null when the slot is empty, out of range or the
     * vault is sealed by a breach. The page is marked dirty and, when an actor is given, the take is
     * audited under that player.
     */
    ItemStack takeSlot(Faction faction, int page, int slot, UUID actor) {
        if (faction == null || breached(faction.id())) {
            return null;
        }
        SimpleItemContainer c = existingPage(faction, page);
        if (c == null || slot < 1 || slot > c.getCapacity()) {
            return null;
        }
        com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction t = c.removeItemStackFromSlot((short) (slot - 1));
        if (t == null || !t.succeeded()) {
            return null;
        }
        ItemStack taken = t.getSlotBefore();
        if (ItemStack.isEmpty(taken)) {
            return null;
        }
        dirty.add(faction.id());
        if (actor != null) {
            try {
                ctx.service(FactionService.class).audit(faction, actor, null, "vault",
                        Messages.get(VaultText.AUDIT_TOOK, "count", taken.getQuantity(), "item", taken.getItemId(), "page", page, "slot", slot));
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion vault audit failed");
            }
        }
        return taken;
    }

    /** A page that is within the faction's page count, loaded or created empty, or null when out of range. */
    private SimpleItemContainer existingPage(Faction faction, int page) {
        if (faction == null || page < 1 || page > pages(faction) || ctx.repo().faction(faction.id()) == null) {
            return null;
        }
        return page(faction, page);
    }

    private void onClose(Faction f, SimpleItemContainer container, UUID player, String name, int page) {
        List<SimpleItemContainer> live = vaults.get(f.id());
        boolean orphaned;
        if (live == null) {
            orphaned = true;
        } else {
            synchronized (live) {
                // Identity, because SimpleItemContainer.equals compares contents.
                orphaned = live.stream().noneMatch(c -> c == container);
            }
        }
        if (orphaned || ctx.repo().faction(f.id()) == null) {
            // The faction disbanded while this window was open. Anything left in the page would be lost.
            rescue(f, container, player);
            return;
        }
        save(f.id());
        try {
            ctx.service(FactionService.class).audit(f, player, name, "vault", Messages.get(VaultText.AUDIT_OPENED, "page", page));
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion vault audit failed");
        }
    }

    /** Mails whatever is still in an orphaned page to the player who had it open. */
    private void rescue(Faction f, SimpleItemContainer container, UUID player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack s : container.removeAllItemStacks()) {
            if (!ItemStack.isEmpty(s)) {
                stacks.add(s);
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        MarketService market = ctx.serviceOrNull(MarketService.class);
        if (market != null) {
            market.mail(player, stacks, 0);
            Msg.send(Players.online(player), Msg.info(Messages.get(VaultText.RESCUED, "faction", f.tag())));
        } else {
            keep(f.id(), stacks);
        }
    }

    @Override
    public void onDisband(Faction faction, UUID leader) {
        if (faction == null) {
            return;
        }
        List<SimpleItemContainer> pages = load(faction.id());
        List<ItemStack> stacks = new ArrayList<>();
        synchronized (pages) {
            for (SimpleItemContainer c : pages) {
                for (ItemStack s : c.removeAllItemStacks()) {
                    if (!ItemStack.isEmpty(s)) {
                        stacks.add(s);
                    }
                }
            }
        }
        vaults.remove(faction.id());
        breaches.remove(faction.id());
        dirty.remove(faction.id());
        List<Document> raw = new ArrayList<>();
        Map<Integer, List<Document>> stuck = unreadable.remove(faction.id());
        if (stuck != null) {
            stuck.values().forEach(raw::addAll);
        }
        if (!raw.isEmpty()) {
            keepDocuments(faction.id(), raw);
            ctx.logger().at(Level.WARNING).log("Dominion kept %d unreadable vault items of disbanded faction %s in %s",
                    raw.size(), faction.tag(), dir.resolve(faction.id() + ".disbanded.json"));
        }
        if (!stacks.isEmpty()) {
            MarketService market = ctx.serviceOrNull(MarketService.class);
            if (leader != null && market != null) {
                market.mail(leader, stacks, 0);
                Msg.send(Players.online(leader), Msg.info(Messages.get(VaultText.EMPTIED_TO_MAILBOX)));
            } else {
                // Nobody can receive the items, so they are kept on disk for an admin instead of being destroyed.
                keep(faction.id(), stacks);
                ctx.logger().at(Level.WARNING).log("Dominion kept %d item stacks from the vault of disbanded faction %s in %s",
                        stacks.size(), faction.tag(), dir.resolve(faction.id() + ".disbanded.json"));
            }
        }
        ctx.files().delete(file(faction.id()));
    }

    /** Appends stacks to the disbanded vault file of a faction. */
    private void keep(UUID factionId, List<ItemStack> stacks) {
        List<Document> docs = new ArrayList<>();
        for (ItemStack s : stacks) {
            docs.add(Items.toDocument(s));
        }
        keepDocuments(factionId, docs);
    }

    /** Appends stored stack documents to the disbanded vault file of a faction. */
    private synchronized void keepDocuments(UUID factionId, List<Document> docs) {
        Path out = dir.resolve(factionId + ".disbanded.json");
        Document d = ctx.files().read(out);
        List<Document> items = new ArrayList<>();
        if (d != null && d.get("Items") instanceof List<?> old) {
            for (Object o : old) {
                if (o instanceof Document doc) {
                    items.add(doc);
                }
            }
        }
        items.addAll(docs);
        if (!ctx.files().write(out, new Document("Items", items))) {
            ctx.logger().at(Level.SEVERE).log("Dominion could not write %s with %d vault items", out, docs.size());
        }
    }

    // ---- breach flag ----------------------------------------------------------------

    void onEvent(DominionEvent event) {
        if (event instanceof DominionEvent.BreachChanged b && b.breached() && b.defender() != null) {
            closeOpenWindows(b.defender());
        } else if (event instanceof DominionEvent.SiegePhase phase && phase.defender() != null && phase.siegeId() != null) {
            if ("BREACHED".equalsIgnoreCase(phase.phase())) {
                long lapse = Time.now() + (ctx.settings().longValue("siege.breachMinutes") + 1) * 60_000L;
                breaches.computeIfAbsent(phase.defender(), k -> new ConcurrentHashMap<>()).put(phase.siegeId(), lapse);
                closeOpenWindows(phase.defender());
            } else {
                Map<UUID, Long> map = breaches.get(phase.defender());
                if (map != null) {
                    map.remove(phase.siegeId());
                }
            }
        } else if (event instanceof DominionEvent.FactionDisbanded gone) {
            breaches.remove(gone.factionId());
        }
    }

    // ---- open window tracking ---------------------------------------------------------

    private void track(UUID player, UUID factionId, ContainerWindow window) {
        List<OpenVault> list = openWindows.computeIfAbsent(player, k -> new ArrayList<>());
        synchronized (list) {
            list.add(new OpenVault(factionId, window));
        }
    }

    private void untrack(UUID player, ContainerWindow window) {
        List<OpenVault> list = openWindows.get(player);
        if (list == null) {
            return;
        }
        synchronized (list) {
            list.removeIf(o -> o.window() == window);
            if (list.isEmpty()) {
                openWindows.remove(player, list);
            }
        }
    }

    /** Called when a player leaves so the tracking map does not hold their windows. */
    void forget(UUID player) {
        openWindows.remove(player);
    }

    /**
     * Closes every vault window of the faction that is still open. Each window is taken out of the
     * tracking map before the close is queued, so the many breach events of one siege close it once.
     */
    private void closeOpenWindows(UUID factionId) {
        for (Map.Entry<UUID, List<OpenVault>> e : openWindows.entrySet()) {
            List<ContainerWindow> toClose = new ArrayList<>();
            List<OpenVault> list = e.getValue();
            synchronized (list) {
                list.removeIf(o -> {
                    if (o.factionId().equals(factionId)) {
                        toClose.add(o.window());
                        return true;
                    }
                    return false;
                });
            }
            if (toClose.isEmpty()) {
                continue;
            }
            PlayerRef p = Players.online(e.getKey());
            if (p == null) {
                continue;
            }
            Items.onWorld(p, (store, ref) -> {
                Player pc = store.getComponent(ref, Player.getComponentType());
                if (pc == null) {
                    return false;
                }
                for (ContainerWindow w : toClose) {
                    // Closing through the window manager fires the close event, which saves the page.
                    pc.getWindowManager().closeWindow(ref, w.getId(), store);
                }
                pc.getPageManager().setPage(ref, store, Page.None);
                // Saved here as well in case the close event does not fire for a window closed by the server.
                save(factionId);
                return true;
            }).whenComplete((ok, err) -> {
                if (err != null) {
                    ctx.logger().at(Level.WARNING).withCause(err).log("Dominion could not close a vault window during a breach");
                }
            });
            Msg.send(p, Msg.warn(Messages.get(VaultText.CLOSED_BY_BREACH)));
        }
    }

    /**
     * The siege service is the source of truth, so a breach restored after a restart seals the vault
     * too. The event map is only a fallback for servers without the siege module.
     */
    boolean breached(UUID factionId) {
        SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
        if (sieges != null) {
            for (Siege s : sieges.active()) {
                if (s.defender().equals(factionId) && s.phase() == Siege.Phase.BREACHED) {
                    return true;
                }
            }
            return false;
        }
        Map<UUID, Long> map = breaches.get(factionId);
        if (map == null) {
            return false;
        }
        long now = Time.now();
        map.values().removeIf(until -> until <= now);
        return !map.isEmpty();
    }

    // ---- storage --------------------------------------------------------------------

    private SimpleItemContainer page(Faction f, int page) {
        List<SimpleItemContainer> pages = load(f.id());
        synchronized (pages) {
            short slots = (short) Math.max(1, Math.min(Short.MAX_VALUE, ctx.settings().integer("vault.slotsPerPage")));
            while (pages.size() < page) {
                SimpleItemContainer c = new SimpleItemContainer(slots);
                watch(f.id(), c);
                pages.add(c);
            }
            return pages.get(page - 1);
        }
    }

    private List<SimpleItemContainer> load(UUID factionId) {
        return vaults.computeIfAbsent(factionId, id -> {
            List<SimpleItemContainer> pages = new ArrayList<>();
            Document d = ctx.files().read(file(id));
            if (d == null || !(d.get("Pages") instanceof List<?> list)) {
                return pages;
            }
            int slots = Math.max(1, ctx.settings().integer("vault.slotsPerPage"));
            for (Object o : list) {
                int index = pages.size();
                Document pd = o instanceof Document doc ? doc : new Document();
                Document items = pd.get("Items") instanceof Document doc ? doc : new Document();
                int highest = -1;
                for (String key : items.keySet()) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(key));
                    } catch (NumberFormatException ignored) {
                        // not a slot
                    }
                }
                SimpleItemContainer c = new SimpleItemContainer((short) Math.min(Short.MAX_VALUE, Math.max(slots, highest + 1)));
                List<Document> raw = new ArrayList<>();
                for (Map.Entry<String, Object> e : items.entrySet()) {
                    if (!(e.getValue() instanceof Document sd)) {
                        continue;
                    }
                    ItemStack s = Items.fromDocument(sd);
                    boolean placed = false;
                    try {
                        short slot = Short.parseShort(e.getKey());
                        if (s != null && !ItemStack.isEmpty(s)) {
                            c.setItemStackForSlot(slot, s);
                            placed = true;
                        }
                    } catch (RuntimeException ex) {
                        ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion vault %s slot %s could not be restored", id, e.getKey());
                    }
                    if (!placed) {
                        ctx.logger().at(Level.WARNING).log("Dominion vault %s keeps an unreadable item from page %d slot %s", id, index + 1, e.getKey());
                        raw.add(sd);
                    }
                }
                for (Object u : pd.get("Unreadable") instanceof List<?> l ? l : List.of()) {
                    if (u instanceof Document sd && !restore(c, sd)) {
                        raw.add(sd);
                    }
                }
                if (!raw.isEmpty()) {
                    unreadable.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(index, raw);
                }
                watch(id, c);
                pages.add(c);
            }
            return pages;
        });
    }

    /** Puts a once unreadable item into the first free slot when it can be read now. */
    private static boolean restore(SimpleItemContainer c, Document sd) {
        ItemStack s = Items.fromDocument(sd);
        if (s == null || ItemStack.isEmpty(s)) {
            return false;
        }
        for (short slot = 0; slot < c.getCapacity(); slot++) {
            if (ItemStack.isEmpty(c.getItemStack(slot))) {
                try {
                    c.setItemStackForSlot(slot, s);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Marks the faction for the next periodic save whenever a page changes. */
    private void watch(UUID factionId, SimpleItemContainer c) {
        c.registerChangeEvent(e -> dirty.add(factionId));
    }

    /** Called once a minute. Saves pages that changed so a crash cannot lose or duplicate vault items. */
    void saveDirty() {
        for (UUID id : List.copyOf(dirty)) {
            dirty.remove(id);
            if (!save(id)) {
                // Tried again next minute rather than waiting for the next change.
                dirty.add(id);
            }
        }
    }

    /** Writes the vault file. False when it could not be written. */
    boolean save(UUID factionId) {
        List<SimpleItemContainer> pages = vaults.get(factionId);
        if (pages == null) {
            return true;
        }
        Map<Integer, List<Document>> raw = unreadable.getOrDefault(factionId, Map.of());
        List<Document> out = new ArrayList<>();
        synchronized (pages) {
            for (int i = 0; i < pages.size(); i++) {
                SimpleItemContainer c = pages.get(i);
                Document items = new Document();
                for (short slot = 0; slot < c.getCapacity(); slot++) {
                    ItemStack s = c.getItemStack(slot);
                    if (!ItemStack.isEmpty(s)) {
                        items.append(Short.toString(slot), Items.toDocument(s));
                    }
                }
                Document page = new Document("Capacity", (int) c.getCapacity()).append("Items", items);
                List<Document> stuck = raw.get(i);
                if (stuck != null && !stuck.isEmpty()) {
                    page.append("Unreadable", new ArrayList<>(stuck));
                }
                out.add(page);
            }
        }
        boolean ok = ctx.files().write(file(factionId), new Document("Pages", out));
        if (!ok) {
            ctx.logger().at(Level.WARNING).log("Dominion could not save the vault of %s", factionId);
        }
        return ok;
    }

    void saveAll() {
        for (UUID id : vaults.keySet()) {
            save(id);
        }
    }

    private Path file(UUID factionId) {
        return dir.resolve(factionId + ".json");
    }
}
