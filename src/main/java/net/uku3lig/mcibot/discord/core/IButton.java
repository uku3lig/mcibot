package net.uku3lig.mcibot.discord.core;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.Button;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public abstract class IButton implements InteractionHandler<ButtonInteractionEvent> {
    private final List<String> args = new LinkedList<>();
    
    protected IButton(List<String> args) {
        this.args.addAll(args);
    }

    public abstract Button getButton();

    protected String getId(String id) {
        return id + (args.isEmpty() ? "" : "," + String.join(",", args));
    }

    public abstract Mono<Void> onButtonClick(ButtonInteractionEvent event, List<String> args);

    @Override
    public final Mono<Void> onInteraction(ButtonInteractionEvent event) {
        return onButtonClick(event, Arrays.stream(event.getCustomId().split(",")).skip(1).toList());
    }
}
