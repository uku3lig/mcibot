package net.uku3lig.mcibot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
public class BlacklistedUser {
    @Id
    private UUID minecraftUuid;
    private String discordId;
    private String reason;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlacklistedUser that)) return false;
        return Objects.equals(discordId, that.discordId) && Objects.equals(minecraftUuid, that.minecraftUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minecraftUuid, discordId);
    }
}
