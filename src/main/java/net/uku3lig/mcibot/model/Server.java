package net.uku3lig.mcibot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.Objects;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity
public class Server {
    @Id
    private long discordId;
    private long minecraftId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Server server)) return false;
        return discordId == server.discordId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(discordId);
    }
}
