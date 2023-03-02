package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static discord4j.core.object.command.ApplicationCommandOption.Type.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigCommand implements ICommand {
    private static final String VALUE = "value";

    private final ServerRepository serverRepository;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("config")
                .description("configures your server owo")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("prompt_channel")
                        .description("the prompt channel")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name(VALUE)
                                .description("the value")
                                .type(CHANNEL.getValue())
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("auto_blacklist")
                        .description("blacklist without prompting")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name(VALUE)
                                .description("the value")
                                .type(BOOLEAN.getValue())
                                .build())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotServerOwner(event, serverRepository)) {
            return event.reply("You're not allowed to do that.").withEphemeral(true);
        }

        Server server = event.getInteraction().getGuildId()
                .map(Snowflake::asLong)
                .flatMap(serverRepository::findByGuildId)
                .orElse(null);

        if (server == null) {
            return event.reply("Your server is not registered. Please retry later.").withEphemeral(true);
        }

        ApplicationCommandInteractionOption subcommand = event.getOptions().get(0);
        final var value = subcommand.getOption(VALUE).flatMap(ApplicationCommandInteractionOption::getValue);

        Mono<Void> action = switch (subcommand.getName()) {
            case "prompt_channel" -> value.map(ApplicationCommandInteractionOptionValue::asChannel)
                    .orElse(Mono.empty())
                    .filter(MessageChannel.class::isInstance)
                    .flatMap(c -> {
                        server.setPromptChannel(c.getId().asLong());
                        return event.reply("Edited prompt channel to <#%s>".formatted(c.getId().asLong()));
                    })
                    .switchIfEmpty(event.reply("Unknown or invalid channel.").withEphemeral(true));

            case "auto_blacklist" -> Mono.justOrEmpty(value)
                    .map(ApplicationCommandInteractionOptionValue::asBoolean)
                    .flatMap(bool -> {
                        server.setAutoBlacklist(bool);
                        return event.reply((Boolean.TRUE.equals(bool) ? "Enabled" : "Disabled") + " auto blacklist.");
                    })
                    .switchIfEmpty(event.reply("Invalid value").withEphemeral(true));

            default -> event.reply("Unknown subcommand.").withEphemeral(true);
        };

        return action.doOnNext(v -> serverRepository.save(server));
    }
}
