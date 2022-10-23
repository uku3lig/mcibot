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
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCommand implements ICommand {
    private final GatewayDiscordClient client;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;

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
        String username = event.getOption("username").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse("");
        Mono<User> user = event.getOption("user").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser).orElse(Mono.empty());
        String reason = event.getOption("reason").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse(null);

        return user.flatMap(u -> {
            if (userRepository.existsByDiscordId(u.getId().asString())) {
                return event.reply("User is already blacklisted.");
            }

            Button button = Button.success("blacklist_confirm", "Confirm");

            Mono<Void> buttonListener = client.on(ButtonInteractionEvent.class, evt -> {
                        if (!evt.getCustomId().equals("blacklist_confirm")) return Mono.empty();
                        return evt.edit().withComponents()
                                .then(Util.getMinecraftUUID(username))
                                .map(uuid -> new BlacklistedUser(uuid, u.getId().asString(), reason))
                                .flatMap(bu -> Mono.fromRunnable(() -> userRepository.save(bu)))
                                .then(Mono.fromFuture(serverRepository.findAll()))
                                .flatMapMany(Flux::fromIterable)
                                .map(Server::getDiscordId)
                                .map(Snowflake::of)
                                .flatMap(event.getClient()::getGuildById)
                                .flatMap(Guild::getOwner)
                                .flatMap(User::getPrivateChannel)
                                .flatMap(c -> c.createMessage("The MCI admin team has blacklisted a new user (discord: `%s`, minecraft: `%s`)"
                                        .formatted(u.getTag(), username)).withComponents(ActionRow.of(button)))
                                .doOnError(t -> log.error("Could not send blacklist message to owner.", t))
                                .then();
                    })
                    .timeout(Duration.ofMinutes(5))
                    .onErrorResume(TimeoutException.class, t -> event.editReply().withComponents().then())
                    .then();

            return event.reply("Are you sure you want to blacklist this user? (discord: `%s`, minecraft: `%s`)".formatted(u.getTag(), username))
                    .withComponents(ActionRow.of(button))
                    .then(buttonListener);
        });
    }
}
