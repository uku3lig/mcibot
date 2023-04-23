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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.MinecraftUserList;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.model.ServerType;
import net.uku3lig.mcibot.util.Util;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCommand implements ICommand {
    private static final Button BLACKLIST_CONFIRM = Button.primary("blacklist_confirm", "Blacklist");
    private static final ActionRow BLACKLISTED = ActionRow.of(Button.secondary("blacklisted", "Blacklisted").disabled());

    private static final String BLACKLIST_MSG = "Are you sure you want to blacklist this user? (discord: `%s`, minecraft: `%s`)";

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
                .addOption(ApplicationCommandOptionData.builder()
                        .name("proof_url")
                        .description("The url to the proof of the reason.")
                        .type(STRING.getValue())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotMciAdmin(event))
            return event.reply("You need to be an admin to use this command.").withEphemeral(true);

        String username = event.getOption("username").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse("");
        Mono<User> user = event.getOption("user").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser).orElse(Mono.empty());
        String reason = event.getOption("reason").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse(null);
        String proof = event.getOption("proof_url").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse(null);

        return user.zipWith(Util.getMinecraftUUID(username))
                .switchIfEmpty(event.reply("Invalid minecraft username.").withEphemeral(true).then(Mono.empty()))
                .flatMap(p -> {
                    final User u = p.getT1();
                    final UUID uuid = p.getT2();

                    Optional<BlacklistedUser> opt = userRepository.findByDiscordAccountsContaining(u.getId().asLong())
                            .or(() -> userRepository.findByMinecraftAccountsContaining(uuid));
                    if (opt.isPresent() && opt.get().isGlobal()) {
                        return event.reply("User is already blacklisted.").withEphemeral(true);
                    }

                    return event.reply(BLACKLIST_MSG.formatted(u.getTag(), username))
                            .withComponents(Util.CHOICE_ROW)
                            .then(event.getReply())
                            .flatMap(m -> getConfirmListener(username, u.getTag(), opt.orElse(new BlacklistedUser(u.getId().asLong(), uuid, reason, proof)), m, event));
                });
    }

    private Mono<Void> getConfirmListener(String username, String tag, BlacklistedUser bu, Message message, ChatInputInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this blacklist.").withEphemeral(true);

                    log.info("New globally blacklisted user: minecraft={}, discord={}, by={}", username, tag, evt.getInteraction().getUser().getTag());
                    bu.setGlobal(true);
                    userRepository.save(bu);
                    List<Server> servers = serverRepository.findAll().stream().filter(s -> !s.getBlacklistedUsers().contains(bu)).toList();

                    return evt.edit().withComponents(BLACKLISTED)
                            .then(evt.createFollowup("Blacklist message sent to all owners.").withEphemeral(true))
                            .thenMany(Flux.fromIterable(servers))
                            .flatMap(server -> {
                                if (server.getType() == ServerType.MINECRAFT) {
                                    return Mono.fromRunnable(() -> {
                                        MinecraftUserList mu = new MinecraftUserList(Collections.singletonList(username), bu.getReason(), false);
                                        log.info("Sending {} to RabbitMQ.", mu);
                                        rabbitTemplate.convertAndSend(MCIBot.EXCHANGE, String.valueOf(server.getId()), mu);
                                    });
                                } else if (server.isAutoBlacklist()) {
                                    return blacklistUser(server, bu);
                                } else {
                                    return server.getPromptChannel(client)
                                            .switchIfEmpty(server.getGuild(client).flatMap(Guild::getOwner).flatMap(User::getPrivateChannel))
                                            .zipWith(server.getGuild(client))
                                            .flatMap(t -> t.getT1().createMessage(getBlacklistMessage(tag, username, bu, t.getT2().getName()))
                                                    .withComponents(ActionRow.of(BLACKLIST_CONFIRM, Util.CANCEL_BUTTON))
                                                    .flatMap(msg -> getBlacklistListener(bu, server, t.getT2(), username, msg, evt)))
                                            .doOnError(t -> log.error("Could not send blacklist message to owner.", t));
                                }
                            })
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }

    private Mono<Void> getBlacklistListener(BlacklistedUser user, Server server, Guild guild, String username, Message msg, ButtonInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, msg)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "blacklist_confirm", msg)) return Mono.empty();
                    log.info("Server owner {} blacklisted {} on server {}", evt.getInteraction().getUser().getTag(), username, guild.getName());

                    return evt.edit().withComponents(BLACKLISTED)
                            .then(blacklistUser(server, user))
                            .then(evt.createFollowup("User has been banned.").withEphemeral(true))
                            .then();
                }).timeout(Duration.ofDays(7))
                .onErrorResume(TimeoutException.class, t -> msg.edit().withComponents(Util.CANCELLED).then())
                .next();
    }

    private Mono<Void> blacklistUser(Server server, BlacklistedUser user) {
        server.getBlacklistedUsers().add(user);
        serverRepository.save(server);

        return server.getGuild(client)
                .flatMap(guild -> Flux.fromIterable(user.getDiscordAccounts())
                        .map(Snowflake::of)
                        .flatMap(s -> guild.ban(s).withReason(user.getReason())
                                .onErrorResume(t -> Mono.fromRunnable(() -> log.error("Failed to ban", t))))
                        .then());
    }

    private String getBlacklistMessage(String tag, String username, BlacklistedUser user, String guildName) {
        StringBuilder builder = new StringBuilder("The MCI admin team has blacklisted a new user (discord: `%s`, minecraft: `%s`)".formatted(tag, username));

        if (user.getReason() != null) {
            builder.append(" for the following reason: `%s`".formatted(user.getReason()));
        }

        if (user.getProofUrl() != null) {
            builder.append(" with the following proof: %s".formatted(user.getProofUrl()));
        }

        return builder.append("%nWhat action would you like to take on server `%s`?".formatted(guildName)).toString();
    }
}
