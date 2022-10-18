package net.uku3lig.mcibot.rest;

import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.model.DiscordUser;
import net.uku3lig.mcibot.model.TokenResponse;
import net.uku3lig.mcibot.util.DiscordOAuth2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@RestController
@Slf4j
public class DiscordController {
    // url: https://discord.com/oauth2/authorize?client_id=1004131023338086451&permissions=8&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fdiscord&response_type=code&scope=identify%20bot
    private final DiscordOAuth2 discord = new DiscordOAuth2("1004131023338086451", System.getenv("MCI_CLIENT_SECRET"));

    @GetMapping("/discord/url")
    public String getUrl(@RequestParam String redirectUri, @RequestParam(required = false) String state) {
        return discord.getOAuthUrl(redirectUri, state);
    }

    @GetMapping("/discord")
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam(required = false) String state) {
        if (System.getenv("MCI_CLIENT_SECRET") == null) {
            log.error("MCI_CLIENT_SECRET is not set");
            return new ModelAndView("redirect:/error", Map.of("err_msg", "MCI_CLIENT_SECRET is not set"));
        }

        discord.getAccessToken(code, "http://localhost:8080/discord")
                .map(TokenResponse::getAccessToken)
                .flatMap(discord::getUser)
                .map(DiscordUser::getId)
                .subscribe(log::info);

        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }
}
