package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.*;

@Service
@AllArgsConstructor
public class EditCommand implements ICommand {
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final GatewayDiscordClient client;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("edit")
                .description("Edit a blacklisted user.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("id")
                        .description("The user's ID (use /list to find this)")
                        .type(INTEGER.getValue())
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("operation")
                        .description("The operation to perform")
                        .type(STRING.getValue())
                        .choices(Util.choices("add", "remove"))
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("discord")
                        .description("The Discord ID to add or remove")
                        .type(USER.getValue())
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("minecraft")
                        .description("The Minecraft UUID to add or remove")
                        .type(STRING.getValue())
                        .required(false)
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        Optional<Long> guildId = event.getInteraction().getGuildId().map(Snowflake::asLong);
        if (guildId.isEmpty() || guildId.get() != MCIBot.getManager().getConfig().getMainDiscordId()) {
            return event.reply("This command can only be used in the MCI server.").withEphemeral(true);
        }

        // check if the user has the required permissions
        if (!event.getInteraction().getMember().map(m -> m.getBasePermissions().block()).map(p -> p.contains(Permission.MANAGE_GUILD)).orElse(false)) {
            return event.reply("You need to be an admin to use this command.").withEphemeral(true);
        }

        final BlacklistedUser user = event.getOption("id")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .flatMap(userRepository::findById)
                .orElse(null);

        if (user == null)
            return event.reply("User not found in database. Make sure you used the ID found using /list.").withEphemeral(true);

        final String operation = event.getOption("operation")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");

        if (operation.isEmpty()) return event.reply("Operation not specified.").withEphemeral(true);

        Mono<UUID> minecraft = event.getOption("minecraft").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(Util::getMinecraftUUID)
                .orElse(null);

        Mono<User> discord = event.getOption("discord")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser).orElse(null);

        if (minecraft != null) {
            return minecraft.flatMap(uuid -> event.createFollowup("Are you sure you want to edit this user?")
                    .withComponents(Util.CHOICE_ROW)
                    .flatMap(m -> getMinecraftConfirmListener(user, operation, uuid, m, event)));
        } else if (discord != null) {
            return discord.flatMap(u -> event.createFollowup("Are you sure you want to edit this user?")
                    .withComponents(Util.CHOICE_ROW)
                    .flatMap(m -> getDiscordConfirmListener(user, operation, u, m, event)));
        } else {
            return event.reply("You need to specify either a Minecraft UUID or a Discord ID.").withEphemeral(true);
        }
    }

    private Mono<Void> getMinecraftConfirmListener(BlacklistedUser user, String operation, UUID uuid, Message message, ChatInputInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this pardon.").withEphemeral(true);

                    final EditMessage editMessage = new EditMessage(operation, uuid.toString());

                    if (operation.equals("add")) {
                        user.getMinecraftAccounts().add(uuid);
                    } else {
                        user.getMinecraftAccounts().remove(uuid);
                    }

                    return evt.deferReply().withEphemeral(true)
                            .thenMany(Flux.fromIterable(serverRepository.findAll()))
                            .filter(s -> s.getBlacklistedUsers().contains(user))
                            .map(Server::getMinecraftId)
                            .flatMap(su -> Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(MCIBot.EDIT_EXCHANGE, su.toString(), editMessage)))
                            .then(Mono.fromRunnable(() -> userRepository.save(user)))
                            .then(evt.createFollowup("Successfully edited user with UUID `%s`.".formatted(uuid)))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }

    private Mono<Void> getDiscordConfirmListener(BlacklistedUser user, String operation, User discordUser, Message message, ChatInputInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this pardon.").withEphemeral(true);

                    Flux<Guild> guilds = Flux.fromIterable(serverRepository.findAll())
                            .filter(s -> s.getBlacklistedUsers().contains(user))
                            .map(Server::getDiscordId)
                            .map(Snowflake::of)
                            .flatMap(client::getGuildById);

                    Mono<Void> mono;
                    if (operation.equals("add")) {
                        user.getDiscordAccounts().add(discordUser.getId().asLong());
                        mono = guilds.flatMap(g -> g.ban(discordUser.getId())).then();
                    } else { // remove
                        user.getDiscordAccounts().remove(discordUser.getId().asLong());
                        mono = guilds.flatMap(g -> g.unban(discordUser.getId())).then();
                    }

                    return evt.deferReply().withEphemeral(true)
                            .then(mono)
                            .then(Mono.fromRunnable(() -> userRepository.save(user)))
                            .then(evt.createFollowup("Successfully edited user with discord account `%s`.".formatted(discordUser.getTag())))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }

    private record EditMessage(String operation, String value) {
    }
}
