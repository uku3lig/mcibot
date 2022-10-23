package net.uku3lig.mcibot.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class Util {
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

    public static Mono<UUID> getMinecraftUUID(String username) {
        return client.get()
                .uri("https://api.mojang.com/users/profiles/minecraft/{username}", username)
                .retrieve()
                .onStatus(HttpStatus.NO_CONTENT::equals, response -> Mono.error(new IllegalArgumentException("Invalid minecraft username")))
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

    @Data
    private static class Profile {
        private String id;
    }

    private Util() {
    }
}
