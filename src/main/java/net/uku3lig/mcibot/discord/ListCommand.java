package net.uku3lig.mcibot.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.util.Paginator;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;
import static discord4j.core.object.command.ApplicationCommandOption.Type.SUB_COMMAND;

@Service
@AllArgsConstructor
public class ListCommand implements ICommand {
    private GatewayDiscordClient client;
    private UserRepository userRepository;
    private final ServerRepository serverRepository;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("list")
                .description("List blacklisted users.")
                .defaultMemberPermissions(String.valueOf(Permission.BAN_MEMBERS.getValue()))
                .addOption(ApplicationCommandOptionData.builder()
                        .name("all")
                        .description("List all of the blacklisted users")
                        .type(SUB_COMMAND.getValue())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("info")
                        .description("Get information about a blacklisted user")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The user's ID")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotMciAdmin(event) && Util.isNotServerOwner(event, serverRepository))
            return event.reply("You're not allowed to do that.").withEphemeral(true);

        ApplicationCommandInteractionOption subcommand = event.getOptions().get(0);

        return (switch (subcommand.getName()) {
            case "all" -> {
                List<BlacklistedUser> users = userRepository.findAll();

                yield event.deferReply().then(Paginator.paginate(users, event));
            }

            case "info" -> {
                Optional<BlacklistedUser> bu = subcommand.getOption("id")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .flatMap(s -> Util.getUUID(s).flatMap(userRepository::findByMinecraftAccountsContaining)
                                .or(() -> Util.toLong(s).flatMap(userRepository::findByDiscordAccountsContaining)));

                if (bu.isEmpty()) yield event.reply("Invalid ID.").withEphemeral(true);

                yield event.deferReply()
                        .then(bu.get().display(client))
                        .flatMap(e -> event.createFollowup().withEmbeds(e));
            }
            default -> event.reply("Invalid subcommand.").withEphemeral(true);
        }).then();
    }
}
