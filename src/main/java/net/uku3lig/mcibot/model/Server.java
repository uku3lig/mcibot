package net.uku3lig.mcibot.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class Server {
    /**
     * Primary key.
     */
    private long discordId;
    private UUID minecraftId;

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
