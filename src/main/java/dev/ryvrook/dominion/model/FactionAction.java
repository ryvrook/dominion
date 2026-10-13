package dev.ryvrook.dominion.model;

import java.util.EnumSet;

/** Every permissioned thing a player can do in or to a faction. */
public enum FactionAction {
    BUILD("Build and break blocks", true, true),
    CONTAINER("Open containers", true, true),
    INTERACT("Use doors, benches and beds", true, true),
    DAMAGE_ENTITY("Hit animals and NPCs", true, true),
    CLAIM("Claim land", true, false),
    UNCLAIM("Unclaim land", true, false),
    INVITE("Invite players", true, false),
    KICK("Kick members", true, false),
    PROMOTE("Promote and demote", true, false),
    SET_HOME("Set the faction home", true, false),
    USE_HOME("Teleport to the faction home", true, true),
    SET_WARP("Create and delete warps", true, false),
    USE_WARP("Use warps", true, true),
    DEPOSIT("Deposit to the bank", true, false),
    WITHDRAW("Withdraw from the bank", true, false),
    VAULT("Open the faction vault", true, false),
    RELATIONS("Change relations", true, false),
    SETTINGS("Change settings and identity", true, false),
    PERMS("Edit permissions", true, false),
    SIEGE("Start sieges", true, false),
    ANNOUNCE("Post announcements", true, false),
    DISBAND("Disband, transfer and unclaim all", true, false);

    private final String description;
    private final boolean rankScoped;
    private final boolean relationScoped;

    FactionAction(String description, boolean rankScoped, boolean relationScoped) {
        this.description = description;
        this.rankScoped = rankScoped;
        this.relationScoped = relationScoped;
    }

    public String description() {
        return description;
    }

    /** Actions that can be granted to a rank inside the faction. */
    public boolean rankScoped() {
        return rankScoped;
    }

    /** Actions that can be granted to outsiders by relation. */
    public boolean relationScoped() {
        return relationScoped;
    }

    public static EnumSet<FactionAction> relationScopedSet() {
        EnumSet<FactionAction> set = EnumSet.noneOf(FactionAction.class);
        for (FactionAction a : values()) {
            if (a.relationScoped) {
                set.add(a);
            }
        }
        return set;
    }
}
