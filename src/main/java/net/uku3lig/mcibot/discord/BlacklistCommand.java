package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCommand implements ICommand {
    private static final WebClient webClient = WebClient.create("https://api.mojang.com");

    private final ServerRepository repository;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("blacklist")
                .description("Blacklist an user.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("uuid")
                        .description("The user's UUID.")
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
        UUID uuid = event.getOption("uuid").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).map(UUID::fromString).orElse(UUID.randomUUID());
        Mono<User> user = event.getOption("user").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser).orElse(Mono.empty());
        String reason = event.getOption("reason").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).orElse(null);

        return user.map(u -> new BlacklistedUser(uuid, u.getId().asString(), reason))
                .flatMap(bu -> checkMcAccount(event, bu));
    }

    private Mono<Void> checkMcAccount(DeferrableInteractionEvent event, BlacklistedUser user) {
        final String fmt = "Not implemented yet. User: `%s`%nMinecraft username: `%s`%nDiscord tag: `%s`";
        return webClient.get().uri("/user/profile/" + user.getMinecraftUuid()).exchangeToMono(res -> {
            if (res.statusCode().equals(HttpStatus.NO_CONTENT)) {
                return event.reply("User `%s` was not found, are you sure the UUID is correct?".formatted(user.getMinecraftUuid())).then(Mono.empty());
            } else if (!res.statusCode().equals(HttpStatus.OK)) {
                return event.reply("An unknown error happened. (`%d`)".formatted(res.statusCode().value()))
                        .flatMap(v -> res.bodyToMono(String.class)).map(RuntimeException::new).flatMap(Mono::error);
            } else {
                return res.bodyToMono(Profile.class);
            }
        }).flatMap(p -> event.getClient().getUserById(Snowflake.of(user.getDiscordId()))
                .flatMap(u -> event.reply(fmt.formatted(user, p.getName(), u.getTag())))
                .flatMap(v -> Mono.fromFuture(repository.findAll())
                        .flatMapMany(Flux::fromIterable)
                        .map(Server::getDiscordId)
                        .map(Snowflake::of)
                        .flatMap(event.getClient()::getGuildById)
                        .flatMap(Guild::getOwner)
                        .flatMap(User::getPrivateChannel)
                        .flatMap(c -> c.createMessage(fmt.formatted(user, p.getName(), "uhuhuh")))
                        .last()).then());
        // todo check if user is already blacklisted
        // todo add button to say hi :3 aer you sure you want to blacklist the user :3
    }

    @Data
    private static class Profile {
        private String name;
    }
}
