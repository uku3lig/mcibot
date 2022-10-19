package net.uku3lig.mcibot;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.uku3lig.mcibot.config.Config;
import net.uku3lig.mcibot.config.ConfigManager;
import net.uku3lig.mcibot.discord.CommandListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@SpringBootApplication
public class MCIBot {
    private static final Set<Consumer<JDA>> consumers = new HashSet<>();

    @Getter
    private static final ConfigManager<Config> manager = ConfigManager.create(Config.class, "mcibot_config");
    @Getter
    private static JDA jda;

    public static void main(String[] args) throws InterruptedException{
        SpringApplication.run(MCIBot.class, args);

        jda = JDABuilder.createLight(manager.getConfig().getDiscordToken())
                .addEventListeners(new CommandListener())
                .build()
                .awaitReady();

        consumers.forEach(c -> c.accept(jda));
    }

    public static void runWhenReady(Consumer<JDA> consumer) {
        consumers.add(consumer);
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(manager.getConfig().getConnectionUrl());
        dataSource.setUsername(manager.getConfig().getDatabaseUsername());
        dataSource.setPassword(manager.getConfig().getDatabasePassword());

        return dataSource;
    }
}
