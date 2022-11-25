package net.uku3lig.mcibot.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.SUB_COMMAND;

@Service
@AllArgsConstructor
@Slf4j
public class ServerCommand implements ICommand {
    private static final ActionRow REMOVED = ActionRow.of(Button.secondary("removed", "Removed").disabled());
    private static final ActionRow EDITED = ActionRow.of(Button.secondary("edited", "Edited").disabled());

    private final ServerRepository serverRepository;
    private final GatewayDiscordClient client;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("server")
                .description("Manage registered servers.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("remove")
                        .description("Unregister a server.")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The id of the server to remove.")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("edit")
                        .description("Edit a registered server.")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The id of the server to edit.")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("value")
                                .description("The value to edit")
                                .type(STRING.getValue())
                                .choices(Util.choices("discord_id", "minecraft_id"))
                                .required(true)
                                .build())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("new_value")
                                .description("The new value.")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotMciAdmin(event))
            return event.reply("You need to be an admin to use this command.").withEphemeral(true);

        ApplicationCommandInteractionOption subcommand = event.getOptions().get(0);
        Server server = subcommand.getOption("id")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .flatMap(Util::toLong)
                .flatMap(serverRepository::findByDiscordId)
                .orElse(null);

        if (server == null)
            return event.reply("Invalid ID. (use `/list servers` to show all the servers)").withEphemeral(true);

        return switch (subcommand.getName()) {
            case "remove" -> event.deferReply()
                    .then(event.createFollowup("Are you sure you want to remove server `%s`?".formatted(server.getDiscordId())).withComponents(Util.CHOICE_ROW))
                    .flatMap(m -> getRemoveListener(server, event, m));
            case "edit" -> {
                Optional<String> value = subcommand.getOption("new_value")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString);

                if (value.isEmpty()) yield event.reply("Invalid value.").withEphemeral(true);

                String toEdit = subcommand.getOption("value")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .orElse("");

                Mono<Void> editor = switch (toEdit) {
                    case "discord_id" -> {
                        Optional<Long> l = value.flatMap(Util::toLong);
                        if (l.isEmpty()) {
                            yield event.reply("Invalid ID.").withEphemeral(true);
                        }
                        yield Mono.fromRunnable(() -> server.setDiscordId(l.get()));
                    }
                    case "minecraft_id" -> {
                        Optional<UUID> u = value.flatMap(Util::getUUID);
                        if (u.isEmpty()) {
                            yield event.reply("Invalid UUID.").withEphemeral(true);
                        }
                        yield Mono.fromRunnable(() -> server.setMinecraftId(u.get()));
                    }
                    default -> event.reply("Invalid value to edit.").withEphemeral(true);
                };

                yield event.deferReply()
                        .then(event.createFollowup("Are you sure you want to edit server `%s`?".formatted(server.getDiscordId())).withComponents(Util.CHOICE_ROW))
                        .flatMap(m -> getEditListener(server, toEdit, value.get(), editor, event, m));
            }
            default -> event.reply("Invalid subcommand.").withEphemeral(true);
        };
    }

    private Mono<Void> getRemoveListener(Server server, ChatInputInteractionEvent other, Message message) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this action.").withEphemeral(true);

                    log.info("{} removed server {} (mc: {})", other.getInteraction().getUser().getTag(), server.getDiscordId(), server.getMinecraftId());
                    serverRepository.delete(server);
                    return evt.edit().withComponents(REMOVED)
                            .then(evt.createFollowup("Successfully removed server.").withEphemeral(true))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }

    private Mono<Void> getEditListener(Server server, String valueToEdit, String value, Mono<Void> editor, ChatInputInteractionEvent other, Message message) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this action.").withEphemeral(true);

                    log.info("{} edited server {}'s {} value to {}", other.getInteraction().getUser().getTag(), server.getDiscordId(), valueToEdit, value);
                    return evt.edit().withComponents(EDITED)
                            .then(editor)
                            .then(Mono.fromRunnable(() -> serverRepository.save(server)))
                            .then(evt.createFollowup("Successfully edited server with `%s`.".formatted(value)).withEphemeral(true))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }
}
