package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.MinecraftUserList;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.*;

@Service
@AllArgsConstructor
@Slf4j
public class EditCommand implements ICommand {
    private static final ActionRow EDITED = ActionRow.of(Button.secondary("edited", "Edited").disabled());

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
            return event.deferReply()
                    .then(Util.checkMciAdmin(event))
                    .then(minecraft)
                    .flatMap(uuid -> {
                        String message = "Are you sure you want to edit this user? (%s `%s`)".formatted(operation, uuid) +
                                ((user.getDiscordAccounts().isEmpty() && user.getMinecraftAccounts().size() == 1) ?
                                        " This will lead to the removal of the user from the database." : "");

                        return event.createFollowup(message)
                                .withComponents(Util.CHOICE_ROW)
                                .flatMap(m -> getMinecraftConfirmListener(user, operation, uuid, m, event));
                    });
        } else if (discord != null) {
            return event.deferReply()
                    .then(Util.checkMciAdmin(event))
                    .then(discord)
                    .flatMap(u -> {
                        String message = "Are you sure you want to edit this user? (%s `%s`)".formatted(operation, u.getTag()) +
                                ((user.getMinecraftAccounts().isEmpty() && user.getDiscordAccounts().size() == 1) ?
                                        " This will lead to the removal of the user from the database." : "");

                        return event.createFollowup(message)
                                .withComponents(Util.CHOICE_ROW)
                                .flatMap(m -> getDiscordConfirmListener(user, operation, u, m, event));
                    });
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

                    log.info("{} edited user {}: {} {}", other.getInteraction().getUser().getTag(), user.getId(), operation, uuid);
                    final Mono<MinecraftUserList> list = Util.getMinecraftUsername(uuid)
                            .map(Collections::singletonList)
                            .map(l -> new MinecraftUserList(l, null, !operation.equals("add")));

                    if (operation.equals("add")) {
                        user.getMinecraftAccounts().add(uuid);
                    } else {
                        user.getMinecraftAccounts().remove(uuid);
                    }

                    if (user.isEmpty()) {
                        user.getServers(serverRepository).forEach(s -> {
                            s.getBlacklistedUsers().remove(user);
                            serverRepository.save(s);
                        });
                        userRepository.delete(user);
                    } else {
                        userRepository.save(user);
                    }

                    return evt.edit().withComponents(EDITED)
                            .thenMany(Flux.fromIterable(serverRepository.findAll()))
                            .filter(s -> s.getBlacklistedUsers().contains(user))
                            .zipWith(list)
                            .flatMap(t -> Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(MCIBot.EXCHANGE, t.getT1().getMinecraftId().toString(), t.getT2())))
                            .then(evt.createFollowup("Successfully edited user with UUID `%s`.".formatted(uuid)).withEphemeral(true))
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

                    log.info("{} edited user {}: {} {}", other.getInteraction().getUser().getTag(), user.getId(), operation, discordUser.getTag());
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

                    if (user.isEmpty()) {
                        user.getServers(serverRepository).forEach(s -> {
                            s.getBlacklistedUsers().remove(user);
                            serverRepository.save(s);
                        });
                        userRepository.delete(user);
                    } else {
                        userRepository.save(user);
                    }

                    return evt.edit().withComponents(EDITED)
                            .then(mono)
                            .onErrorResume(t -> Mono.empty())
                            .then(evt.createFollowup("Successfully edited user with discord account `%s`.".formatted(discordUser.getTag())).withEphemeral(true))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }
}
