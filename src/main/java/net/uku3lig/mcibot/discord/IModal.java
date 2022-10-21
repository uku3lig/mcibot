package net.uku3lig.mcibot.discord;

import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.spec.InteractionPresentModalSpec;

public interface IModal extends InteractionHandler<ModalSubmitInteractionEvent> {
    InteractionPresentModalSpec getModal();
}
