package net.uku3lig.mcibot.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Server {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private ServerType type;

    // === DISCORD ATTRIBUTES === //
    private long guildId = -1;
    private long promptChannel = -1;
    private boolean autoBlacklist = false;

    // === MINECRAFT ATTRIBUTES === //
    private String registrarName = null;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<BlacklistedUser> blacklistedUsers;

    public Server(ServerType type) {
        this.type = type;
        this.blacklistedUsers = new HashSet<>();
    }

    public static Server fromGuildId(long guildId) {
        Server server = new Server(ServerType.DISCORD);
        server.setGuildId(guildId);
        return server;
    }

    public static Server fromMinecraft(String username) {
        Server server = new Server(ServerType.MINECRAFT);
        server.setRegistrarName(username);
        return server;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Server server)) return false;
        return id == server.id && type == server.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
