package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static discord4j.core.object.command.ApplicationCommandOption.Type.*;

@Service
@AllArgsConstructor
public class ListCommand implements ICommand {
    private GatewayDiscordClient client;
    private UserRepository userRepository;
    private ServerRepository serverRepository;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("list")
                .description("List blacklisted users.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("all")
                        .description("List all of the blacklisted users")
                        .type(SUB_COMMAND.getValue())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("servers")
                        .description("List all the registered servers")
                        .type(SUB_COMMAND.getValue())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("info")
                        .description("Get information about a blacklisted user")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The user's ID")
                                .type(INTEGER.getValue())
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("local")
                        .description("Get information about a server")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The server's ID")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        ApplicationCommandInteractionOption subcommand = event.getOptions().get(0);

        return (switch (subcommand.getName()) {
            case "all" -> event.deferReply()
                    .thenMany(Flux.fromIterable(userRepository.findAll()))
                    .flatMap(u -> {
                        Mono<String> tag = client.getUserById(Snowflake.of(u.getDiscordAccounts().get(0))).map(User::getTag);
                        Mono<String> username = Util.getMinecraftUsername(u.getMinecraftAccounts().get(0));
                        return Mono.zip(Mono.just(u.getId()), tag, username);
                    })
                    .map(t -> EmbedCreateFields.Field.of("ID: " + t.getT1(), "Discord: `%s`%nMinecraft: `%s`".formatted(t.getT2(), t.getT3()), false))
                    .collectList()
                    .map(l -> EmbedCreateSpec.builder()
                            .title("Blacklisted users")
                            .fields(l)
                            .footer("Do /list info <id> to show info about a specific user", null)
                            .build()
                    )
                    .flatMap(e -> event.createFollowup().withEmbeds(e));

            case "servers" -> {
                Optional<Long> guildId = event.getInteraction().getGuildId().map(Snowflake::asLong);
                if (guildId.isEmpty() || guildId.get() != MCIBot.getManager().getConfig().getMainDiscordId()) {
                    yield event.reply("This command can only be used in the MCI server.").withEphemeral(true);
                }

                // check if the user has the required permissions
                if (!event.getInteraction().getMember().map(m -> m.getBasePermissions().block()).map(p -> p.contains(Permission.MANAGE_GUILD)).orElse(false)) {
                    yield event.reply("You need to be an admin to use this command.").withEphemeral(true);
                }

                yield event.deferReply()
                        .thenMany(Flux.fromIterable(serverRepository.findAll()))
                        .flatMap(s -> {
                            Mono<String> discord = client.getGuildById(Snowflake.of(s.getDiscordId())).map(Guild::getName);
                            Mono<String> minecraft = Mono.just(s.getMinecraftId()).map(UUID::toString);
                            return Mono.zip(Mono.just(s.getDiscordId()), discord, minecraft, Mono.just(s.getBlacklistedUsers().size()));
                        })
                        .map(t -> EmbedCreateFields.Field.of("ID: " + t.getT1(), "Discord: `%s`%nMinecraft: `%s`%n`%d` blacklisted users"
                                .formatted(t.getT2(), t.getT3(), t.getT4()), false))
                        .collectList()
                        .map(l -> EmbedCreateSpec.builder()
                                .title("Registered servers")
                                .fields(l)
                                .footer("Do /list local <id> to show info about a specific server", null)
                                .build()
                        )
                        .flatMap(e -> event.createFollowup().withEmbeds(e));
            }

            case "info" -> {
                Optional<BlacklistedUser> bu = subcommand.getOption("id")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asLong)
                        .flatMap(userRepository::findById);

                if (bu.isEmpty()) yield event.reply("Invalid ID.").withEphemeral(true);

                Mono<String> discord = Flux.fromIterable(bu.get().getDiscordAccounts())
                        .map(Snowflake::of)
                        .flatMap(client::getUserById)
                        .map(User::getTag)
                        .map("`%s`"::formatted)
                        .collectList()
                        .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

                Mono<String> minecraft = Flux.fromIterable(bu.get().getMinecraftAccounts())
                        .flatMap(Util::getMinecraftUsername)
                        .map("`%s`"::formatted)
                        .collectList()
                        .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

                String reason = Optional.ofNullable(bu.get().getReason()).orElse("None");

                yield event.deferReply()
                        .then(Mono.zip(discord, minecraft))
                        .map(t -> EmbedCreateSpec.builder()
                                .title("Blacklisted user (ID: %d)".formatted(bu.get().getId()))
                                .addField("Discord Accounts", t.getT1(), false)
                                .addField("Minecraft Accounts", t.getT2(), false)
                                .addField("Reason", reason, false)
                                .build())
                        .flatMap(e -> event.createFollowup().withEmbeds(e));
            }

            case "local" -> {
                Optional<Long> id = subcommand.getOption("id")
                        .flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .flatMap(Util::toLong);

                Optional<Long> guildId = event.getInteraction().getGuildId().map(Snowflake::asLong);
                if (guildId.isEmpty() || guildId.get() != MCIBot.getManager().getConfig().getMainDiscordId()) {
                    id = guildId;
                }

                // check if the user has the required permissions
                if (!event.getInteraction().getMember().map(m -> m.getBasePermissions().block()).map(p -> p.contains(Permission.MANAGE_GUILD)).orElse(false)) {
                    id = guildId;
                }

                Optional<Server> server = id.flatMap(serverRepository::findByDiscordId);
                if (server.isEmpty()) yield event.reply("Invalid ID.").withEphemeral(true);

                Mono<String> name = client.getGuildById(Snowflake.of(server.get().getDiscordId()))
                        .map(Guild::getName)
                        .map("`%s`"::formatted);

                Mono<String> users = Flux.fromIterable(server.get().getBlacklistedUsers())
                        .flatMap(u -> {
                            Mono<String> tag = client.getUserById(Snowflake.of(u.getDiscordAccounts().get(0))).map(User::getTag);
                            Mono<String> username = Util.getMinecraftUsername(u.getMinecraftAccounts().get(0));
                            return Mono.zip(Mono.just(u.getId()), tag, username);
                        })
                        .map(t -> "ID: `%s` (`%s`, `%s`)".formatted(t.getT1(), t.getT2(), t.getT3()))
                        .collectList()
                        .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

                yield event.deferReply()
                        .then(Mono.zip(name, users))
                        .map(t -> EmbedCreateSpec.builder()
                                .title("Server (ID: %d)".formatted(server.get().getDiscordId()))
                                .addField("Discord Server", t.getT1(), false)
                                .addField("Minecraft Server", "`%s`".formatted(server.get().getMinecraftId()), false)
                                .addField("Blacklisted Users", t.getT2(), false)
                                .build())
                        .flatMap(e -> event.createFollowup().withEmbeds(e));
            }
            default -> event.reply("Invalid subcommand.").withEphemeral(true);
        }).then();
    }
}
