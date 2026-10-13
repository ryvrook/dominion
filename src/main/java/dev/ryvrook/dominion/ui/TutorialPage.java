package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.text.Msg;

/** Four short steps that introduce factions. Finishing or skipping marks the tutorial as seen. */
public final class TutorialPage extends DominionPage {
    private static final String[][] STEPS = {
            {"Welcome to Dominion",
                    "Factions are groups of players who claim land together and protect it.",
                    "Open the faction panel any time with /f. It has everything in this tutorial and more."},
            {"Found or join a faction",
                    "Type a short tag in the panel and press Create to found your own faction.",
                    "Or join an open faction from the list, or accept an invite from a friend."},
            {"Claim your land",
                    "Stand where you want to build and press Claim on the land tab.",
                    "Your power and your members' power decide how many chunks you can hold. Set a home so everyone can find base."},
            {"Allies, enemies and raids",
                    "Make allies to share access and set enemies to fight them.",
                    "A faction that holds more land than its power can defend becomes raidable. Stay active and avoid needless deaths."}
    };

    private int step;

    public TutorialPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui) {
        super(player, ctx, ui);
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(FactionInfoPage.PAGE);
        String[] s = STEPS[step];
        c.set("#PageTitle.Text", "Tutorial");
        c.set("#Heading.Text", s[0]);
        c.set("#Subheading.Text", "Step " + (step + 1) + " of " + STEPS.length);
        Body body = new Body(c, events, "#Body");
        body.text(s[1]);
        body.text(s[2], Msg.MUTED);

        boolean last = step == STEPS.length - 1;
        c.set("#F1.Visible", true);
        c.set("#F1.Text", last ? "Finish" : "Next");
        Body.on(events, "#F1", last ? "finish" : "next", "");
        if (!last) {
            c.set("#F2.Visible", true);
            c.set("#F2.Text", "Skip");
            Body.on(events, "#F2", "finish", "");
        }
        if (step > 0) {
            c.set("#F3.Visible", true);
            c.set("#F3.Text", "Back");
            Body.on(events, "#F3", "back", "");
        }
    }

    @Override
    protected boolean onAction(PageEvent e) {
        switch (e.action) {
            case "next" -> {
                step = Math.min(STEPS.length - 1, step + 1);
                return true;
            }
            case "back" -> {
                step = Math.max(0, step - 1);
                return true;
            }
            case "finish" -> {
                ctx.player(uuid()).setTutorialSeen(true);
                ui.openPanel(player(), DominionPanel.OVERVIEW);
                return false;
            }
            default -> {
                return false;
            }
        }
    }
}
