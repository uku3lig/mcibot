package net.uku3lig.mcibot.util;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class Util {
    public static final ActionRow CANCELLED = ActionRow.of(Button.secondary("cancelled", "Cancelled").disabled());

    private static final WebClient client = WebClient.create();

    public static ModelAndView error(String msg) {
        log.error(msg);
        return new ModelAndView("redirect:/error", Map.of("err_msg", msg));
    }

    public static UUID convertUUID(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    public static Button cancelButton(Object... args) {
        if (args.length == 0) return Button.danger("cancel", "Cancel");

        String[] argsStr = Arrays.stream(args)
                .map(o -> {
                    if (o instanceof Snowflake s) return s.asString();
                    else return Objects.toString(o);
                })
                .toArray(String[]::new);
        return Button.secondary("cancel_" + String.join("_", argsStr), "Cancel");
    }

    public static boolean isButton(ButtonInteractionEvent event, String id, Object... args) {
        if (args.length == 0) return event.getCustomId().equals(id);

        String[] argsStr = Arrays.stream(args)
                .map(o -> {
                    if (o instanceof Snowflake s) return s.asString();
                    else return Objects.toString(o);
                })
                .toArray(String[]::new);
        return event.getCustomId().equals(id + "_" + String.join("_", argsStr));
    }

    public static boolean isCancelButton(ButtonInteractionEvent event, Object... args) {
        return isButton(event, "cancel", args);
    }

    public static Mono<UUID> getMinecraftUUID(String username) {
        return client.get()
                .uri("https://api.mojang.com/users/profiles/minecraft/{username}", username)
                .retrieve()
                .onStatus(HttpStatus.NO_CONTENT::equals, response -> Mono.empty())
                .onStatus(code -> !HttpStatus.OK.equals(code), response -> {
                    log.error("Error while getting minecraft uuid: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(s -> Mono.fromRunnable(() -> log.error("Response: {}", s)))
                            .then(Mono.error(new IllegalArgumentException("An unknown error happened.")));
                })
                .bodyToMono(Profile.class)
                .map(Profile::getId)
                .map(Util::convertUUID);
    }

    public static Mono<String> getMinecraftUsername(UUID uuid) {
        return client.get()
                .uri("https://api.mojang.com/user/profile/{uuid}", uuid.toString())
                .retrieve()
                .onStatus(HttpStatus.NO_CONTENT::equals, response -> Mono.empty())
                .onStatus(code -> !HttpStatus.OK.equals(code), response -> {
                    log.error("Error while getting minecraft username: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(s -> Mono.fromRunnable(() -> log.error("Response: {}", s)))
                            .then(Mono.error(new IllegalArgumentException("An unknown error happened.")));
                })
                .bodyToMono(Profile.class)
                .map(Profile::getName);
    }

    public static Mono<Void> onCancel(ButtonInteractionEvent event, InteractionCreateEvent other) {
        if (event.getInteraction().getUser().equals(other.getInteraction().getUser())) {
            return event.edit().withComponents(CANCELLED).then();
        } else {
            return event.reply("You can't cancel this interaction.").withEphemeral(true).then();
        }
    }

    @Data
    private static class Profile {
        private String id;
        private String name;
    }

    private Util() {
    }
}
