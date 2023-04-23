package net.uku3lig.mcibot.model;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.MessageChannel;
import jakarta.persistence.*;
import lombok.*;
import reactor.core.publisher.Mono;

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
    @Getter(AccessLevel.PRIVATE)
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

    public Mono<MessageChannel> getPromptChannel(GatewayDiscordClient client) {
        return client.getChannelById(Snowflake.of(promptChannel))
                .ofType(MessageChannel.class)
                .switchIfEmpty(Mono.empty())
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Guild> getGuild(GatewayDiscordClient client) {
        return client.getGuildById(Snowflake.of(guildId))
                .switchIfEmpty(Mono.empty())
                .onErrorResume(e -> Mono.empty());
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
