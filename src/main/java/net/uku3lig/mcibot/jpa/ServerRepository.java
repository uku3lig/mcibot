package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.Server;
import org.springframework.data.repository.CrudRepository;

public interface ServerRepository extends CrudRepository<Server, Long> {
}
