package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegisterCommand implements ICommand {
    private final ServerRepository serverRepository;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("register")
                .description("Register the current server")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("prompt_channel")
                        .description("the channel where blacklist prompts will be sent")
                        .type(ApplicationCommandOption.Type.CHANNEL.getValue())
                        .required(true)
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (event.getInteraction().getGuildId().map(Snowflake::asLong).flatMap(serverRepository::findByGuildId).isPresent()) {
            return event.reply("Server is already registered.").withEphemeral(true);
        }

        Mono<MessageChannel> channel = event.getOption("prompt_channel").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asChannel)
                .orElse(Mono.empty())
                .filter(MessageChannel.class::isInstance)
                .map(MessageChannel.class::cast);

        return event.deferReply()
                .then(Mono.justOrEmpty(event.getInteraction().getMember()))
                .flatMap(PartialMember::getBasePermissions)
                .filter(p -> p.contains(Permission.MANAGE_GUILD))
                .then(Mono.justOrEmpty(event.getInteraction().getGuildId()))
                .zipWith(channel)
                .doOnNext(tuple -> {
                    // t1 is guildId // t2 is prompt channel //
                    Server server = Server.fromGuildId(tuple.getT1().asLong());
                    server.setPromptChannel(tuple.getT2().getId().asLong());
                    serverRepository.save(server);
                })
                .then(event.getInteraction().getGuild())
                .flatMap(g -> event.createFollowup("Server `%s` was successfully registered".formatted(g.getName())))
                .switchIfEmpty(event.createFollowup("You do not appear to be the owner of the guild. pls fix").withEphemeral(true))
                .then();
    }
}
