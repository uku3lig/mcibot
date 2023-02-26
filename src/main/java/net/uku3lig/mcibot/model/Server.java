package net.uku3lig.mcibot.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Server {
    @Id
    private long discordId;

    private long promptChannel;

    // TODO
    private UUID minecraftId;

    private long ownerId;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<BlacklistedUser> blacklistedUsers;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Server server)) return false;
        return Objects.equals(discordId, server.discordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(discordId);
    }
}
