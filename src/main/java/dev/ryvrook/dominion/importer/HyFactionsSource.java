package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.text.Messages;

import dev.ryvrook.dominion.model.Rank;
import org.bson.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HyFactions (HyFaction by Kaws) is closed source, so this reader is experimental. Its
 * CurseForge page names the files and folder (config/faction/<uuid>.json per faction, plus
 * Claims.json and NameCache.json) and public importers in other open source plugins describe the
 * keys. Faction files hold Id, Owner, Name, Members, Color and MemberGrades. Claims.json uses
 * the same Dimensions and ChunkInfo layout as old Simple Claims, with ChunkY meaning the Z axis.
 * The chunk unit is assumed to be 32 block Hytale chunks, which has not been verified.
 */
final class HyFactionsSource implements Source {

    @Override public String id() { return "hyfactions"; }
    @Override public String label() { return "HyFactions"; }

    @Override
    public void read(Locator loc, Plan plan) throws IOException {
        plan.experimental = true;
        Set<Path> roots = new LinkedHashSet<>();
        for (Path p : loc.modFolders(List.of("Kaws_Hyfaction", "Hyfaction", "HyFactions"), "hyfaction")) {
            if (!p.getFileName().toString().toLowerCase(Locale.ROOT).contains("hyperfaction")) {
                roots.add(p);
            }
        }
        roots.add(loc.work.resolve("plugins").resolve("Hyfaction"));
        Path config = null;
        for (Path r : roots) {
            Path c = r.resolve("config");
            plan.looked(c.resolve("faction"));
            plan.looked(c.resolve("Claims.json"));
            if (config == null && (Data.isDir(c.resolve("faction")) || Data.isFile(c.resolve("Claims.json")))) {
                config = c;
            }
        }
        if (config == null) {
            return;
        }
        plan.found = config;
        Map<UUID, String> names = SimpleClaimsSource.nameCache(config.resolve("NameCache.json"), plan);
        Map<String, Plan.Faction> byId = new HashMap<>();
        for (Path file : Data.list(config.resolve("faction"), "*.json")) {
            Document d;
            try {
                d = Data.json(file);
            } catch (Exception e) {
                plan.skipped.add(Messages.get(ImporterText.FILE_UNREADABLE, "file", file.getFileName(), "reason", e.getMessage()));
                continue;
            }
            String id = Data.str(d, "Id");
            UUID owner = Data.uuid(d.get("Owner"));
            String name = Data.str(d, "Name");
            if (id == null || owner == null || name == null) {
                plan.skipped.add(Messages.get(ImporterText.HYF_FILE_INCOMPLETE, "file", file.getFileName()));
                continue;
            }
            Plan.Faction f = new Plan.Faction(id);
            f.name = name;
            f.color = Data.color(Data.num(d, "Color"));
            f.leader = owner;
            f.addMember(owner, names.get(owner), Rank.LEADER);
            Document grades = Data.doc(d, "MemberGrades");
            for (Object m : Data.list(d, "Members")) {
                UUID u = Data.uuid(m);
                if (u == null || u.equals(owner)) {
                    continue;
                }
                String grade = grades == null ? null : Data.str(grades, u.toString());
                f.addMember(u, names.get(u), "OFFICER".equalsIgnoreCase(grade) ? Rank.OFFICER : Rank.MEMBER);
            }
            byId.put(id, f);
            plan.factions.add(f);
        }
        Path claims = config.resolve("Claims.json");
        if (Data.isFile(claims)) {
            SimpleClaimsSource.legacyClaims(Data.json(claims), byId, plan);
        } else {
            plan.notes.add(Messages.get(ImporterText.HYF_NO_CLAIMS));
        }
        for (String zoneFile : List.of("SafeZones.json", "WarZones.json")) {
            if (Data.isFile(config.resolve(zoneFile))) {
                plan.skipped.add(Messages.get(ImporterText.HYF_ZONES, "file", zoneFile));
            }
        }
    }
}
