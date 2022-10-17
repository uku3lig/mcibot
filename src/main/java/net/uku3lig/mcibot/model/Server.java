package net.uku3lig.mcibot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity
public class Server {
    @Id
    @NotNull
    private Long discordId;

    @NotNull
    private Long minecraftId;

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
