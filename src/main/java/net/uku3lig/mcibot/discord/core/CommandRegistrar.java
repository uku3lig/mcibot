package net.uku3lig.mcibot.discord.core;

import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!dev")
public class CommandRegistrar implements ApplicationRunner {
    private final RestClient client;
    private final List<ICommand> commands;


    //This method will run only once on each start up and is automatically called with Spring so blocking is okay.
    @Override
    public void run(ApplicationArguments args) {
        final ApplicationService applicationService = client.getApplicationService();
        final long applicationId = Objects.requireNonNull(client.getApplicationId().block());

        List<ApplicationCommandRequest> requests = this.commands.stream().map(ICommand::getCommandData).toList();

        //Register the commands
        applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, requests)
                .doOnNext(response -> log.info("Registered {} global commands", requests.size()))
                .doOnError(e -> log.error("Failed to register global commands", e))
                .subscribe();
    }
}
