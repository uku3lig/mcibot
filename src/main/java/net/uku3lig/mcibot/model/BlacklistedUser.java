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

    private boolean global;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Long> discordAccounts;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<UUID> minecraftAccounts;

    private String reason;

    public BlacklistedUser(long discordId, UUID minecraftUuid, String reason) {
        this.discordAccounts = Collections.singletonList(discordId);
        this.minecraftAccounts = Collections.singletonList(minecraftUuid);
        this.reason = reason;
        this.global = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlacklistedUser that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
