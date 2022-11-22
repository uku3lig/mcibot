package net.uku3lig.mcibot.jpa;

import net.uku3lig.mcibot.model.BlacklistedUser;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends Repository<BlacklistedUser, Long> {
    void save(BlacklistedUser user);
    void delete(BlacklistedUser user);
    Optional<BlacklistedUser> findById(long id);

    Optional<BlacklistedUser> findByDiscordAccountsContaining(long discordId);
    Optional<BlacklistedUser> findByMinecraftAccountsContaining(UUID minecraftId);

    List<BlacklistedUser> findAll();
}
