package net.uku3lig.mcibot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.RestClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.config.Config;
import net.uku3lig.mcibot.config.ConfigManager;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;

@SpringBootApplication
@Slf4j
public class MCIBot {
    @Getter
    private static final ConfigManager<Config> manager = ConfigManager.create(Config.class, "mcibot_config");

    public static final String EXCHANGE = "mci_blacklist";

    public static void main(String[] args) {
        SpringApplication.run(MCIBot.class, args);
        log.info("=== Finished loading! ===");
    }

    @Bean
    public CommandLineRunner profileChecker(Environment env) {
        return args -> log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
    }

    @Bean
    public Config config() {
        return manager.getConfig();
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
                .gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_BANS, Intent.DIRECT_MESSAGES))
                .login()
                .block();
    }

    @Bean
    public RestClient restClient(GatewayDiscordClient client) {
        return client.getRestClient();
    }

    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(manager.getConfig().getRabbitMqHost());
        factory.setPort(manager.getConfig().getRabbitMqPort());
        return factory;
    }

    @Bean
    public Exchange banExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
