package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

/** Four short steps that introduce factions. Finishing or skipping marks the tutorial as seen. */
public final class TutorialPage extends DominionPage {
    private static final String[][] STEPS = {
            {UiText.TUTORIAL_1_TITLE, UiText.TUTORIAL_1_TEXT, UiText.TUTORIAL_1_HINT},
            {UiText.TUTORIAL_2_TITLE, UiText.TUTORIAL_2_TEXT, UiText.TUTORIAL_2_HINT},
            {UiText.TUTORIAL_3_TITLE, UiText.TUTORIAL_3_TEXT, UiText.TUTORIAL_3_HINT},
            {UiText.TUTORIAL_4_TITLE, UiText.TUTORIAL_4_TEXT, UiText.TUTORIAL_4_HINT}
    };

    private int step;

    public TutorialPage(PlayerRef player, DominionContext ctx, UiServiceImpl ui) {
        super(player, ctx, ui);
    }

    @Override
    protected void render(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder events, Store<EntityStore> store) {
        c.append(FactionInfoPage.PAGE);
        String[] s = STEPS[step];
        c.set("#PageTitle.Text", Messages.get(UiText.TUTORIAL_TITLE));
        c.set("#Heading.Text", Messages.get(s[0]));
        c.set("#Subheading.Text", Messages.get(UiText.TUTORIAL_STEP, "step", step + 1, "total", STEPS.length));
        Body body = new Body(c, events, "#Body");
        body.text(Messages.get(s[1]));
        body.text(Messages.get(s[2]), Msg.MUTED);

        boolean last = step == STEPS.length - 1;
        c.set("#F1.Visible", true);
        c.set("#F1.Text", last ? Messages.get(UiText.FINISH) : Messages.get(UiText.NEXT));
        Body.on(events, "#F1", last ? "finish" : "next", "");
        if (!last) {
            c.set("#F2.Visible", true);
            c.set("#F2.Text", Messages.get(UiText.SKIP));
            Body.on(events, "#F2", "finish", "");
        }
        if (step > 0) {
            c.set("#F3.Visible", true);
            c.set("#F3.Text", Messages.get(UiText.BACK));
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
