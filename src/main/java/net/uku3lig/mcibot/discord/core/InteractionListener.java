package net.uku3lig.mcibot.discord.core;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class InteractionListener {
    private final List<ICommand> commands;
    private final List<IButton> buttons;
    private final List<IModal> modals;


    public InteractionListener(List<ICommand> commands, List<IButton> buttons, List<IModal> modals, GatewayDiscordClient client) {
        this.commands = commands;
        this.buttons = buttons;
        this.modals = modals;

        client.on(ChatInputInteractionEvent.class, this::onCommand).subscribe();
        client.on(ButtonInteractionEvent.class, this::onButton).subscribe();
        client.on(ModalSubmitInteractionEvent.class, this::onModal).subscribe();
    }


    public Mono<Void> onCommand(ChatInputInteractionEvent event) {
        return Flux.fromIterable(commands)
                .filter(ic -> ic.getCommandData().name().equals(event.getCommandName()))
                .next()
                .flatMap(ic -> ic.onInteraction(event));
    }

    public Mono<Void> onButton(ButtonInteractionEvent event) {
        return Flux.fromIterable(buttons)
                .filter(ib -> event.getCustomId().equals(ib.getButton().getCustomId().orElse("")))
                .next()
                .flatMap(ib -> ib.onInteraction(event));
    }

    public Mono<Void> onModal(ModalSubmitInteractionEvent event) {
        return Flux.fromIterable(modals)
                .filter(im -> event.getCustomId().equals(im.getModal().customId().get()))
                .next()
                .flatMap(im -> im.onInteraction(event));
    }
}
