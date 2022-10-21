package net.uku3lig.mcibot.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@AllArgsConstructor
@Slf4j
public class ServerRestController {
    private ServerRepository repository;

    @GetMapping("/server/{id}")
    public Server getServer(@PathVariable long id) {
        return repository.findByDiscordId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
