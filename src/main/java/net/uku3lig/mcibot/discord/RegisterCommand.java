package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.model.Server;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;

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
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (event.getInteraction().getGuildId().map(Snowflake::asLong).flatMap(serverRepository::findByDiscordId).isPresent()) {
            return event.reply("Server is already registered.").withEphemeral(true);
        }

        return event.deferReply()
                .then(event.getInteraction().getGuild())
                .flatMap(Guild::getOwner)
                .map(User::getId)
                .filter(s -> event.getInteraction().getMember().map(User::getId).filter(s::equals).isPresent())
                .then(Mono.justOrEmpty(event.getInteraction().getGuildId()))
                .doOnNext(serverId -> {
                    Server server = new Server(serverId.asLong(), null, new HashSet<>());
                    serverRepository.save(server);
                })
                .then(event.getInteraction().getGuild())
                .flatMap(g -> event.createFollowup("Server `%s` was successfully registered".formatted(g.getName())))
                .switchIfEmpty(event.createFollowup("You do not appear to be the owner of the guild. pls fix").withEphemeral(true))
                .then();
    }
}
