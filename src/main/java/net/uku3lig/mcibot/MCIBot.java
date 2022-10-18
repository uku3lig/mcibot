package net.uku3lig.mcibot;

import lombok.Getter;
import net.uku3lig.mcibot.config.Config;
import net.uku3lig.mcibot.config.ConfigManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MCIBot {
    @Getter
    private static final ConfigManager<Config> manager = ConfigManager.create(Config.class, "mcibot_config");

    public static void main(String[] args) {
        SpringApplication.run(MCIBot.class, args);
    }
}
