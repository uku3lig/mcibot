package net.uku3lig.mcibot;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@RestController
@AllArgsConstructor
@Slf4j
public class ServerRestController {
    private ServerRepository repository;

    @GetMapping({"/server/{id}", "/server/{id}/"})
    public Server getServer(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping({"/server", "/server/"})
    public Server createServer(@RequestBody @Valid Server server) {
        if (repository.existsById(server.getDiscordId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A server with this id already exists.");
        }
        repository.save(server);
        return server;
    }

    @GetMapping({"/discord", "/discord/"})
    public ModelAndView discord(@RequestParam("guild_id") long guildId, @RequestParam String code, @RequestParam(required = false) String state) {
        if (state != null) {
            log.info("Received discord request with guild id {}, code {} and state {}", guildId, code, state);
        } else {
            log.info("Received discord request with guild id {} and code {}", guildId, code);
        }
        return new ModelAndView("redirect:https://discord.com/oauth2/authorized");
    }
}
