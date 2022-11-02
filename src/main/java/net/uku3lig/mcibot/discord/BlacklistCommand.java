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
import discord4j.rest.util.Permission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCommand implements ICommand {
    private static final ActionRow BLACKLISTED = ActionRow.of(Button.secondary("blacklisted", "Blacklisted").disabled());

    private final GatewayDiscordClient client;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("blacklist")
                .description("Blacklist an user.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("username")
                        .description("The user's Minecraft username.")
                        .type(STRING.getValue())
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("user")
                        .description("The user to blacklist.")
                        .type(USER.getValue())
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("reason")
                        .description("The reason for the blacklist.")
                        .type(STRING.getValue())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        Optional<Long> guildId = event.getInteraction().getGuildId().map(Snowflake::asLong);
        if (guildId.isEmpty() || guildId.get() != MCIBot.getManager().getConfig().getMainDiscordId()) {
            return event.reply("This command can only be used in the MCI server.");
        }

        // check if the user has the required permissions
        if (!event.getInteraction().getMember().map(m -> m.getBasePermissions().block()).map(p -> p.contains(Permission.MANAGE_GUILD)).orElse(false)) {
            return event.reply("You need to be an admin to use this command.");
        }

        String username = event.getOption("username").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse("");
        Mono<User> user = event.getOption("user").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser).orElse(Mono.empty());
        String reason = event.getOption("reason").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse(null);

        return user.flatMap(u -> {
            if (userRepository.existsByDiscordAccountsContaining(u.getId().asLong())) {
                return event.reply("User is already blacklisted.");
            }

            long time = Instant.now().getEpochSecond();
            final Button button = Button.primary("confirm_" + time, "Confirm");

            return event.reply("Are you sure you want to blacklist this user? (discord: `%s`, minecraft: `%s`)".formatted(u.getTag(), username))
                    .withComponents(ActionRow.of(button, Util.CANCEL_BUTTON))
                    .then(Util.getMinecraftUUID(username))
                    .flatMap(uuid -> getConfirmListener(username, u.getTag(), new BlacklistedUser(u.getId().asLong(), uuid, reason), time, event));
        });
    }

    private Mono<Void> getConfirmListener(String username, String tag, BlacklistedUser bu, long time, ChatInputInteractionEvent other) {
        final Button button = Button.primary("blacklist_confirm_" + time, "Blacklist");

        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (evt.getCustomId().equals("cancel")) return Util.onCancel(evt, other);
                    if (!evt.getCustomId().equals("confirm_" + time)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this blacklist.").withEphemeral(true);

                    log.info("New globally blacklisted user: minecraft={}, discord={}, by={}", username, tag, evt.getInteraction().getUser().getTag());
                    userRepository.save(bu);
                    List<Server> servers = serverRepository.findAll();

                    return evt.edit().withComponents(BLACKLISTED)
                            .thenMany(Flux.fromIterable(servers))
                            .flatMap(server -> client.getGuildById(Snowflake.of(server.getDiscordId()))
                                    .flatMap(Guild::getOwner)
                                    .flatMap(User::getPrivateChannel)
                                    .flatMap(c -> c.createMessage("The MCI admin team has blacklisted a new user (discord: `%s`, minecraft: `%s`)"
                                            .formatted(tag, username)).withComponents(ActionRow.of(button, Util.CANCEL_BUTTON)))
                                    .flatMap(msg -> getBlacklistListener(bu, server, username, time, msg, evt))
                                    .doOnError(t -> log.error("Could not send blacklist message to owner.", t))
                            ).then(evt.createFollowup("Blacklist message sent to all owners.").withEphemeral(true))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.NOT_BLACKLISTED).then())
                .next();
    }

    private Mono<Void> getBlacklistListener(BlacklistedUser user, Server server, String username, long time, Message msg, ButtonInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (evt.getCustomId().equals("cancel")) return Util.onCancel(evt, other);
                    if (!evt.getCustomId().equals("blacklist_confirm_" + time)) return Mono.empty();
                    log.info("Server owner {} blacklisted {} on server {}", evt.getInteraction().getUser().getTag(), username, server.getMinecraftId());

                    server.getBlacklistedUsers().add(user);
                    serverRepository.save(server);

                    MinecraftUser mu = new MinecraftUser(username, user.getReason());
                    log.info("Sending {} to RabbitMQ.", mu);
                    rabbitTemplate.convertAndSend(MCIBot.BAN_EXCHANGE, server.getMinecraftId().toString(), mu);

                    return evt.edit().withComponents(BLACKLISTED)
                            .then(client.getGuildById(Snowflake.of(server.getDiscordId())))
                            .flatMap(g -> Flux.fromIterable(user.getDiscordAccounts())
                                    .map(Snowflake::of)
                                    .flatMap(s -> g.ban(s).withReason(user.getReason()))
                                    .then())
                            .then(evt.createFollowup("User has been banned.").withEphemeral(true))
                            .then();
                }).timeout(Duration.ofDays(7))
                .onErrorResume(TimeoutException.class, t -> msg.edit().withComponents(Util.NOT_BLACKLISTED).then())
                .next();
    }

    @Data
    @AllArgsConstructor
    public static class MinecraftUser {
        private String username;
        private String reason;
    }
}
