package net.uku3lig.mcibot.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@NonNull
@Entity
public class BlacklistedUser implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> discordAccounts;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<UUID> minecraftAccounts;

    private String reason;

    public BlacklistedUser(long discordId, UUID minecraftUuid, String reason) {
        this.discordAccounts = Collections.singleton(discordId);
        this.minecraftAccounts = Collections.singleton(minecraftUuid);
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlacklistedUser that)) return false;
        return id == that.id && discordAccounts.equals(that.discordAccounts) && minecraftAccounts.equals(that.minecraftAccounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, discordAccounts, minecraftAccounts);
    }
}
