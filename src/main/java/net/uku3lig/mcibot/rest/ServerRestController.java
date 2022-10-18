package net.uku3lig.mcibot.rest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@AllArgsConstructor
@Slf4j
public class ServerRestController {
    private ServerRepository repository;

    @GetMapping("/server/{id}")
    public Server getServer(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/server")
    public Server createServer(@RequestBody @Valid Server server) {
        if (repository.existsById(server.getDiscordId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A server with this id already exists.");
        }
        repository.save(server);
        return server;
    }
}