package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.IButton;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCommand implements ICommand {
    private static final WebClient webClient = WebClient.create("https://api.mojang.com");

    private final ServerRepository serverRepository;
    private final UserRepository userRepository;

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

        return user.flatMap(u -> {
            if (userRepository.existsByDiscordId(u.getId().asString())) {
                return event.reply("User is already blacklisted.");
            }

            BlacklistedUser blacklistedUser = new BlacklistedUser(uuid, u.getId().asString(), reason);
            ConfirmButton button = new ConfirmButton(Arrays.asList(uuid.toString(), u.getId().asString(), reason));

            return webClient.get().uri("/user/profile/" + uuid).exchangeToMono(res -> {
                if (res.statusCode().equals(HttpStatus.NO_CONTENT)) {
                    return event.reply("User `%s` was not found, are you sure the UUID is correct?".formatted(uuid)).then(Mono.empty());
                } else if (!res.statusCode().equals(HttpStatus.OK)) {
                    return event.reply("An unknown error happened. (`%d`)".formatted(res.statusCode().value()))
                            .flatMap(v -> res.bodyToMono(String.class)).map(RuntimeException::new).flatMap(Mono::error);
                } else {
                    return res.bodyToMono(Profile.class);
                }
            }).then(event.reply("Are you sure you want to blacklist this user? (user: `%s`)".formatted(blacklistedUser)).withComponents(ActionRow.of(button.getButton())));
        });
    }

    @Data
    private static class Profile {
        private String name;
    }

    @Component
    private class ConfirmButton extends IButton {

        protected ConfirmButton(List<String> args) {
            super(args);
        }

        @Override
        public Button getButton() {
            return Button.success(getId("confirm"), "Confirm");
        }

        @Override
        public Mono<Void> onButtonClick(ButtonInteractionEvent event, List<String> args) {
            BlacklistedUser blacklistedUser = new BlacklistedUser(UUID.fromString(args.get(0)), args.get(1), args.get(2));
            return event.edit().withComponents()
                    .then(Mono.fromRunnable(() -> userRepository.save(blacklistedUser)))
                    .then(Mono.fromFuture(serverRepository.findAll()))
                    .flatMapMany(Flux::fromIterable)
                    .map(Server::getDiscordId)
                    .map(Snowflake::of)
                    .flatMap(event.getClient()::getGuildById)
                    .flatMap(Guild::getOwner)
                    .flatMap(User::getPrivateChannel)
                    .flatMap(c -> c.createMessage(blacklistedUser.toString()))
                    .doOnError(t -> log.error("Could not send blacklist message to owner.", t))
                    .then();
        }
    }
}
