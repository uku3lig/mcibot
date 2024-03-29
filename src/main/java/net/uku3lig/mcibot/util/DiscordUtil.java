package net.uku3lig.mcibot.util;

import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.model.DiscordUser;
import net.uku3lig.mcibot.model.TokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class DiscordUtil {
    private final WebClient client = WebClient.builder()
            .baseUrl("https://discord.com/api")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .defaultStatusHandler(HttpStatusCode::isError, response -> response.bodyToMono(String.class).map(RuntimeException::new).flatMap(Mono::error))
            .build();

    private final long clientId;
    private final String clientSecret;

    public String getOAuthUrl(String redirectUri, @Nullable String state) {
        return String.format("https://discord.com/oauth2/authorize?client_id=%d&permissions=8&redirect_uri=%s&response_type=code&scope=identify%%20bot%s",
                clientId, redirectUri, (state == null ? "" : "&state=" + state));
    }

    public Mono<TokenResponse> getAccessToken(String code, String redirectUri) {
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", String.valueOf(clientId));
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

    public Mono<DiscordUser> getUser(String accessToken) {
        return client.get()
                .uri("/users/@me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(DiscordUser.class);
    }
}
