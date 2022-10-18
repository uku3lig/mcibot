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
    // url: http://localhost:8080/discord/oauth?redirectUri=http://localhost:8080/discord
    private DiscordUtil discord;
    private final ServerRepository repository;


    @GetMapping("/discord/oauth")
    public ModelAndView getUrl(@RequestParam String redirectUri, @RequestParam(required = false) String state) {
        if (isDiscordBroken()) return Util.error("Client ID or secret not set");

        return new ModelAndView("redirect:" + discord.getOAuthUrl(redirectUri, state));
    }

    @GetMapping("/discord")
    // TODO make state required
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam(required = false) String state) {
        if (isDiscordBroken()) return Util.error("Client ID or secret not set");

        discord.getAccessToken(code, "http://localhost:8080/discord")
                .map(TokenResponse::getAccessToken)
                .flatMap(discord::getUser)
                .map(DiscordUser::getId)
                .subscribe(log::info); // TODO dm the user and say hi :3

        Server server = new Server(guildId, state == null ? 0 : Long.parseLong(state));
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
