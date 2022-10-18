package net.uku3lig.mcibot.util;

import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.model.DiscordUser;
import net.uku3lig.mcibot.model.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class DiscordOAuth2 {
    private final WebClient client = WebClient.builder()
            .baseUrl("https://discord.com/api")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();

    private final String clientId;
    private final String clientSecret;

    public Mono<TokenResponse> getAccessToken(String code, String redirectUri) {
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        return client.post()
                .uri("/oauth2/token")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TokenResponse.class);
    }

    public Mono<String> getUserAvatar(String accessToken) {
        return client.get()
                .uri("/users/@me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(DiscordUser.class)
                .map(u -> "https://cdn.discordapp.com/avatars/" + u.getId() + "/" + u.getAvatar() + ".png?size=4096");
    }
}
