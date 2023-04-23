package net.uku3lig.mcibot.util;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PartialMember;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Permission;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Util {
    public static final Button CONFIRM_BUTTON = Button.primary("confirm", "Confirm");
    public static final Button CANCEL_BUTTON = Button.secondary("cancel", "Cancel");

    public static final ActionRow CHOICE_ROW = ActionRow.of(CONFIRM_BUTTON, CANCEL_BUTTON);
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

    public static boolean isButton(ButtonInteractionEvent event, String id, Message message) {
        return event.getCustomId().equals(id) && event.getMessageId().equals(message.getId());
    }

    public static boolean isCancelButton(ButtonInteractionEvent event, Message message) {
        return isButton(event, "cancel", message);
    }

    public static Mono<UUID> getMinecraftUUID(String username) {
        return getUUID(username).map(uuid -> getMinecraftUsername(uuid).map(s -> uuid)) // the lambda checks if the profile exists
                .orElseGet(() -> client.get()
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
                        .map(Util::convertUUID));
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

    public static ApplicationCommandOptionChoiceData choice(String value) {
        return ApplicationCommandOptionChoiceData.builder().name(value).value(value).build();
    }

    public static List<ApplicationCommandOptionChoiceData> choices(String... values) {
        return Arrays.stream(values).map(Util::choice).toList();
    }

    public static Optional<UUID> getUUID(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            // ignore
        }

        try {
            return Optional.of(convertUUID(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<Long> toLong(String str) {
        try {
            return Optional.of(Long.parseLong(str));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean isNotMciAdmin(InteractionCreateEvent event) {
        final Snowflake mainId = Snowflake.of(MCIBot.getManager().getConfig().getMainDiscordId());
        return Boolean.TRUE.equals(event.getClient().getGuildMembers(mainId)
                .filter(m -> m.getId().equals(event.getInteraction().getUser().getId()))
                .flatMap(PartialMember::getBasePermissions)
                .any(p -> !(p.contains(Permission.MANAGE_GUILD) || p.contains(Permission.ADMINISTRATOR)))
                .onErrorReturn(true)
                .blockOptional()
                .orElse(true));
    }

    public static boolean isNotServerOwner(InteractionCreateEvent event, ServerRepository serverRepository) {
        Optional<Server> server = event.getInteraction().getGuildId().map(Snowflake::asLong)
                .flatMap(serverRepository::findByGuildId);

        if (server.isEmpty()) return true;

        return event.getInteraction().getMember()
                .map(PartialMember::getBasePermissions)
                .orElse(Mono.empty())
                .map(p -> !(p.contains(Permission.MANAGE_GUILD) || p.contains(Permission.ADMINISTRATOR)))
                .blockOptional()
                // in doubt, not an admin
                .orElse(true);
    }

    public static void clamp(AtomicInteger atomicInteger, int min, int max) {
        if (atomicInteger.get() < min) atomicInteger.set(min);
        if (atomicInteger.get() > max) atomicInteger.set(max);
    }

    @Data
    private static class Profile {
        private String id;
        private String name;
    }

    private Util() {
    }
}
