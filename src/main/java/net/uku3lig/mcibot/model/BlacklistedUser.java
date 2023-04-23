package net.uku3lig.mcibot.model;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import jakarta.persistence.*;
import lombok.*;
import net.uku3lig.mcibot.util.Displayable;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@NonNull
@Entity
@Service
public class BlacklistedUser implements Serializable, Displayable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private boolean global;

    @Getter(onMethod_ = @Transactional(readOnly = true))
    @ElementCollection(fetch = FetchType.EAGER)
    private List<Long> discordAccounts;

    @Getter(onMethod_ = @Transactional(readOnly = true))
    @ElementCollection(fetch = FetchType.EAGER)
    private List<UUID> minecraftAccounts;

    private String reason;

    private String proofUrl;

    @ManyToMany(fetch = FetchType.EAGER)
    private transient List<Server> servers;

    public BlacklistedUser(long discordId, UUID minecraftUuid, String reason, String proofUrl) {
        this.discordAccounts = new LinkedList<>(Collections.singletonList(discordId));
        this.minecraftAccounts = new LinkedList<>(Collections.singletonList(minecraftUuid));
        this.reason = reason;
        this.proofUrl = proofUrl;
        this.global = true;
    }

    public List<Server> getServers() {
        return servers == null ? new ArrayList<>() : servers;
    }

    public List<Server> getServers(ServerType type) {
        return getServers().stream().filter(s -> s.getType() == type).toList();
    }

    public boolean isEmpty() {
        return getMinecraftAccounts().isEmpty() && getDiscordAccounts().isEmpty();
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

    @Override
    public Mono<EmbedCreateSpec> display(GatewayDiscordClient client) {
        Mono<String> discord = Flux.fromIterable(getDiscordAccounts())
                .map(Snowflake::of)
                .flatMap(client::getUserById)
                .map(u -> "`%s` (`%s`)".formatted(u.getTag(), u.getId().asLong()))
                .collectList()
                .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

        Mono<String> minecraft = Flux.fromIterable(getMinecraftAccounts())
                .flatMap(uuid -> Mono.zip(Mono.just(uuid), Util.getMinecraftUsername(uuid)))
                .map(t -> "`%s` (`%s`)".formatted(t.getT2(), t.getT1()))
                .collectList()
                .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

        String formattedReason = Optional.ofNullable(getReason()).orElse("None");

        String formattedProof = Optional.ofNullable(getProofUrl()).orElse("No proof provided");

        return Mono.zip(discord, minecraft)
                .map(t -> EmbedCreateSpec.builder()
                        .title("Blacklisted user (ID: %d)".formatted(getId()))
                        .addField("Discord Accounts", t.getT1(), false)
                        .addField("Minecraft Accounts", t.getT2(), false)
                        .addField("Reason", formattedReason, false)
                        .addField("Proof", formattedProof, false)
                        .build());
    }
}
