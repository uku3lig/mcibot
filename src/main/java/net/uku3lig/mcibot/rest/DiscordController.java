package net.uku3lig.mcibot.rest;

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

@RestController
@RequiredArgsConstructor
@Slf4j
public class DiscordController {
    // url: https://discord.com/oauth2/authorize?client_id=1004131023338086451&permissions=8&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fdiscord&response_type=code&scope=identify%20bot
    private DiscordUtil discord;
    private final ServerRepository repository;


    @GetMapping("/discord/url")
    public String getUrl(@RequestParam String redirectUri, @RequestParam(required = false) String state) {
        return discord.getOAuthUrl(redirectUri, state);
    }

    @GetMapping("/discord")
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam(required = false) String state) {
        if (discord == null) {
            Config config = MCIBot.getManager().getConfig();
            if (config.getClientId() == -1 || config.getClientSecret() == null || config.getClientSecret().isEmpty()) {
                return Util.error("Client ID or secret not set");
            }

            discord = new DiscordUtil(config.getClientId(), config.getClientSecret());
        }

        discord.getAccessToken(code, "http://localhost:8080/discord")
                .map(TokenResponse::getAccessToken)
                .flatMap(discord::getUser)
                .map(DiscordUser::getId)
                .subscribe(log::info); // TODO dm the user and say hi :3

        Server server = new Server(guildId, state == null ? 0 : Long.parseLong(state));
        repository.save(server);

        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }
}
