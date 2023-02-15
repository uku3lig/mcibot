package net.uku3lig.mcibot.rest;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.config.Config;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.DiscordUser;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.model.TokenResponse;
import net.uku3lig.mcibot.util.DiscordUtil;
import net.uku3lig.mcibot.util.Util;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DiscordController {
    private DiscordUtil discord;
    private final ServerRepository repository;
    private final GatewayDiscordClient client;
    private final Config config;


    @GetMapping("/discord/oauth")
    public ModelAndView getUrl(@RequestParam String redirectUri, @RequestParam String state) {
        if (isDiscordBroken()) return Util.error("Client ID or secret not set");

        return new ModelAndView("redirect:" + discord.getOAuthUrl(redirectUri, state));
    }

    @GetMapping("/discord")
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam String state) {
        if (isDiscordBroken()) return Util.error("Client ID or secret not set");

        String[] decodedState = new String(Base64.getDecoder().decode(state), StandardCharsets.UTF_8).split("\n");
        UUID minecraftId = UUID.fromString(decodedState[0]);
        String minecraftName = decodedState[1];

        discord.getAccessToken(code, config.getRedirectUri())
                .map(TokenResponse::getAccessToken)
                .flatMap(discord::getUser)
                .map(DiscordUser::getId)
                .doOnNext(id -> {
                    long longId = Long.parseLong(id);
                    Server server = new Server(guildId, minecraftId, longId, new HashSet<>());
                    repository.save(server);
                })
                .flatMap(id -> client.getUserById(Snowflake.of(id)))
                .doOnNext(u -> log.info("User {} linked discord server {} to minecraft server {}", u.getTag(), guildId, minecraftId))
                .flatMap(User::getPrivateChannel)
                .zipWith(client.getGuildById(Snowflake.of(guildId)))
                .flatMap(t -> t.getT1().createMessage("`%s` has linked the minecraft server `%s` to the discord server `%s`."
                        .formatted(minecraftName, minecraftId, t.getT2().getName())))
                .subscribe();

        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }

    private boolean isDiscordBroken() {
        if (discord == null) {
            if (config.getClientId() == -1
                    || config.getClientSecret() == null
                    || config.getClientSecret().isBlank()
                    || config.getMainDiscordId() == -1
                    || config.getRedirectUri().isBlank())
            {
                return true;
            }

            discord = new DiscordUtil(config.getClientId(), config.getClientSecret());
        }
        return false;
    }
}
