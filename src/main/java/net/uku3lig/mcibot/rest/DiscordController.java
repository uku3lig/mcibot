package net.uku3lig.mcibot.rest;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.MCIBot;
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

        discord.getAccessToken(code, "http://localhost:8080/discord")
                .map(TokenResponse::getAccessToken)
                .flatMap(discord::getUser)
                .map(DiscordUser::getId)
                .flatMap(id -> client.getUserById(Snowflake.of(id)))
                .doOnNext(u -> log.info("User {} linked discord server {} to minecraft server {}", u.getTag(), guildId, minecraftId))
                .flatMap(User::getPrivateChannel)
                .flatMap(pc -> pc.createMessage("%s has requested to link the minecraft server to the discord server %s".formatted(minecraftName, guildId))
                        .withComponents(ActionRow.of(Button.secondary("lol", "haha cant click me").disabled(true))))
                .subscribe();

        Server server = new Server(guildId, minecraftId, new HashSet<>());
        repository.save(server);

        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }

    private boolean isDiscordBroken() {
        if (discord == null) {
            Config config = MCIBot.getManager().getConfig();
            if (config.getClientId() == -1 || config.getClientSecret() == null || config.getClientSecret().isEmpty()) {
                return true;
            }

            discord = new DiscordUtil(config.getClientId(), config.getClientSecret());
        }
        return false;
    }
}
