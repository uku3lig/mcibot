package net.uku3lig.mcibot.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class BlacklistedUser implements Serializable {
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
    private List<Server> servers;

    public BlacklistedUser(long discordId, UUID minecraftUuid, String reason, String proofUrl) {
        this.discordAccounts = new LinkedList<>(Collections.singletonList(discordId));
        this.minecraftAccounts = new LinkedList<>(Collections.singletonList(minecraftUuid));
        this.reason = reason;
        this.proofUrl = proofUrl;
        this.global = true;
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
}
