package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.PartialMember;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
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
                .then(Mono.justOrEmpty(event.getInteraction().getMember()))
                .flatMap(PartialMember::getBasePermissions)
                .filter(p -> p.contains(Permission.MANAGE_GUILD)) // TODO ask owner for permission
                .then(Mono.justOrEmpty(event.getInteraction().getGuildId()))
                .doOnNext(serverId -> {
                    Server server = new Server(serverId.asLong(), null, event.getInteraction().getUser().getId().asLong(), new HashSet<>());
                    serverRepository.save(server);
                })
                .then(event.getInteraction().getGuild())
                .flatMap(g -> event.createFollowup("Server `%s` was successfully registered".formatted(g.getName())))
                .switchIfEmpty(event.createFollowup("You do not appear to be the owner of the guild. pls fix").withEphemeral(true))
                .then();
    }
}
