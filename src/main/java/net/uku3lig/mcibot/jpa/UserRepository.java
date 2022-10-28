package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.BlacklistedUser;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<BlacklistedUser, Long> {
    void save(BlacklistedUser user);

    boolean existsByDiscordAccountsContaining(String discordId);
}
