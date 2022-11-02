package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.Server;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface ServerRepository extends Repository<Server, Long> {
    List<Server> findAll();

    void save(Server entity);
}
