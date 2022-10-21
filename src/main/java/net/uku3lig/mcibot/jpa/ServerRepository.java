package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.Server;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ServerRepository extends Repository<Server, Long> {
    @Async
    CompletableFuture<List<Server>> findAll();

    void save(Server entity);

    Optional<Server> findByDiscordId(long id);
}
