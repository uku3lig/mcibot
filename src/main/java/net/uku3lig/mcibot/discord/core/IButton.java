package net.uku3lig.mcibot.discord.core;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.Button;

public interface IButton extends InteractionHandler<ButtonInteractionEvent> {
    Button getButton();
}
