package net.uku3lig.mcibot.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@Slf4j
public class DiscordController {
    @GetMapping("/discord")
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam(required = false) String state) {
        if (state != null) {
            log.info("Received discord request with guild id {}, code {} and state {}", guildId, code, state);
        } else {
            log.info("Received discord request with guild id {} and code {}", guildId, code);
        }
        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }
}
