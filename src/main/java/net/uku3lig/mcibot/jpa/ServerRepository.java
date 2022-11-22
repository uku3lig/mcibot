package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.Server;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends Repository<Server, Long> {
    List<Server> findAll();
    Optional<Server> findByDiscordId(long id);

    void save(Server entity);
}
