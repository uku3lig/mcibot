package net.uku3lig.mcibot.discord;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.util.ClassScanner;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandListener extends ListenerAdapter {
    private final Set<ICommand> commands;
    private final Set<IModal> modals;
    private final Set<IButton> buttons;

    public CommandListener() {
        this.commands = ClassScanner.findSubtypes(ICommand.class);
        this.modals = ClassScanner.findSubtypes(IModal.class);
        this.buttons = ClassScanner.findSubtypes(IButton.class);
        MCIBot.runWhenReady(jda -> {
            Collection<CommandData> data = commands.stream().map(ICommand::getCommandData).toList();
            jda.getGuilds().forEach(g -> g.updateCommands().addCommands(data).queue());
        });
    }

    @Override
    public void onGenericCommandInteraction(@NotNull GenericCommandInteractionEvent event) {
        commands.stream()
                .filter(c -> c.getCommandData().getName().equals(event.getName()))
                .forEach(c -> c.onCommand(event));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        modals.stream()
                .filter(m -> m.getModal().getId().equals(event.getModalId()))
                .forEach(m -> m.onModal(event));
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        buttons.stream()
                .filter(b -> buttonEquals(b, event.getButton()))
                .forEach(b -> b.onButtonClick(event, getArgs(event.getButton())));
    }

    private boolean buttonEquals(@NotNull IButton iButton, @NotNull Button button) {
        if (Objects.equals(iButton.getButton(), button)) return true;
        if (iButton.getButton().getId() == null || button.getId() == null) return false;
        if (iButton.getButton().getId().equalsIgnoreCase(button.getId())) return true;
        String iButtonSplit = iButton.getButton().getId().split(",")[0];
        String buttonSplit = button.getId().split(",")[0];
        return iButtonSplit.equalsIgnoreCase(buttonSplit);
    }

    private List<String> getArgs(Button button) {
        if (button.getId() == null) return Collections.emptyList();
        return Arrays.stream(button.getId().split(",")).skip(1).toList();
    }
}
