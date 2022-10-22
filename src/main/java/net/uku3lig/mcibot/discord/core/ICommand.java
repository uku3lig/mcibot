package net.uku3lig.mcibot.discord.core;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;

public interface ICommand extends InteractionHandler<ChatInputInteractionEvent> {
    ApplicationCommandRequest getCommandData();
}
