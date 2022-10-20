package net.uku3lig.mcibot.discord;

import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.LinkedList;
import java.util.List;

@NoArgsConstructor
public abstract class IButton {
    private final List<String> args = new LinkedList<>();
    
    protected IButton(List<String> args) {
        this.args.addAll(args);
    }

    public final Button getButton() {
        return getButtonBase().withId(getButtonBase().getId() + (args.isEmpty() ? "" : "," + String.join(",", args)));
    }

    protected abstract Button getButtonBase();

    public abstract void onButtonClick(ButtonInteractionEvent event, List<String> args);
}
