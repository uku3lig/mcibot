package net.uku3lig.mcibot;

import net.uku3lig.mcibot.model.Server;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

@RestController
public class ServerRestController {
    private final Set<Server> servers = new HashSet<>();

    @GetMapping({"/server/{id}", "/server/{id}/"})
    public Server getServer(@PathVariable long id) {
        return servers.stream().filter(server -> server.getDiscordId() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping({"/server", "/server/"})
    public Server createServer(@RequestBody Server server) {
        if (servers.contains(server)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A server with this id already exists.");
        }
        servers.add(server);
        return server;
    }
}
