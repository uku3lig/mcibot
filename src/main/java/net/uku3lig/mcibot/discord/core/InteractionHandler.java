package net.uku3lig.mcibot.discord.core;

import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import reactor.core.publisher.Mono;

public interface InteractionHandler<T extends DeferrableInteractionEvent> {
    Mono<Void> onInteraction(T event);

}
