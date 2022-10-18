package net.uku3lig.mcibot.config;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@NonNull
public class Config implements IConfig<Config> {
    private String connectionUrl;
    private String databaseUsername;
    private String databasePassword;

    @Override
    public Config defaultConfig() {
        return new Config("jdbc:mariadb://localhost:3306/mci_db", "mci", "");
    }
}
