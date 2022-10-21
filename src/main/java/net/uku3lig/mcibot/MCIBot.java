package net.uku3lig.mcibot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.rest.RestClient;
import lombok.Getter;
import net.uku3lig.mcibot.config.Config;
import net.uku3lig.mcibot.config.ConfigManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@SpringBootApplication
public class MCIBot {
    @Getter
    private static final ConfigManager<Config> manager = ConfigManager.create(Config.class, "mcibot_config");

    public static void main(String[] args) {
        SpringApplication.run(MCIBot.class, args);
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(manager.getConfig().getConnectionUrl());
        dataSource.setUsername(manager.getConfig().getDatabaseUsername());
        dataSource.setPassword(manager.getConfig().getDatabasePassword());

        return dataSource;
    }

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        return DiscordClientBuilder.create(manager.getConfig().getDiscordToken())
                .build()
                .login()
                .block();
    }

    @Bean
    public RestClient restClient(GatewayDiscordClient client) {
        return client.getRestClient();
    }
}
