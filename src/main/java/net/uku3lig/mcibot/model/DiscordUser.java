package net.uku3lig.mcibot.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscordUser {
    private String id;

    private String username;

    private String discriminator;

    private String avatar;

    private boolean bot;

    private boolean system;

    @JsonAlias("mfa_enabled")
    private boolean mfaEnabled;

    private String locale;

    private boolean verified;

    private String email;

    private int flags;

    @JsonAlias("premium_type")
    private int premiumType;

    @JsonAlias("public_flags")
    private int publicFlags;
}
