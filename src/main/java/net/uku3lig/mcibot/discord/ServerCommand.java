package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.model.MinecraftUserList;
import net.uku3lig.mcibot.model.Server;
import net.uku3lig.mcibot.model.ServerType;
import net.uku3lig.mcibot.util.Util;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static discord4j.core.object.command.ApplicationCommandOption.Type.*;

@Service
@AllArgsConstructor
@Slf4j
public class ServerCommand implements ICommand {
    private static final ActionRow REMOVED = ActionRow.of(Button.secondary("removed", "Removed").disabled());
    private static final String REMOVE = "remove"; // lol

    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final GatewayDiscordClient client;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("server")
                .description("Manage registered servers.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name(REMOVE)
                        .description("Unregister a server.")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The id of the server to remove.")
                                .type(STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("list")
                        .description("List all registered servers.")
                        .type(SUB_COMMAND.getValue())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("info")
                        .description("Show info about a server.")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The id of the discord server.")
                                .type(STRING.getValue())
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("blacklist")
                        .description("Manage the blacklists on this server")
                        .type(SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("operation")
                                .description("The operation to execute")
                                .type(STRING.getValue())
                                .choices(Util.choices("add", REMOVE))
                                .required(true)
                                .build())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("id")
                                .description("The ID of the blacklisted user.")
                                .type(INTEGER.getValue())
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

        if (subcommand.getName().equals("list")) {
            if (Util.isNotMciAdmin(event))
                return event.reply("You need to be an admin to use this command.").withEphemeral(true);

            return listServers(event);
        }

        if (subcommand.getName().equals("info")) {
            return showServerInfo(event);
        }

        if (subcommand.getName().equals("blacklist")) {
            if (Util.isNotServerOwner(event, serverRepository))
                return event.reply("You need to be a server owner to do that.").withEphemeral(true);

            return manageServerBlacklists(event, subcommand);
        }

        Server server = subcommand.getOption("id")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .flatMap(Util::toLong)
                .flatMap(serverRepository::find)
                .orElse(null);

        if (server == null)
            return event.reply("Invalid ID. (use `/list servers` to show all the servers)").withEphemeral(true);

        if (Util.isNotMciAdmin(event))
            return event.reply("You need to be an admin to use this command.").withEphemeral(true);

        if (subcommand.getName().equalsIgnoreCase(REMOVE)) {
            return event.deferReply()
                    .then(event.createFollowup("Are you sure you want to remove server `%s`?".formatted(server.getId())).withComponents(Util.CHOICE_ROW))
                    .flatMap(m -> getRemoveListener(server, event, m));
        } else {
            return event.reply("Invalid subcommand.").withEphemeral(true);
        }
    }

    private Mono<Void> listServers(ChatInputInteractionEvent event) {
        return event.deferReply()
                .thenMany(Flux.fromIterable(serverRepository.findAll()))
                .flatMap(server -> Mono.zip(Mono.just(server), getServerDesc(server)))
                .map(tuple -> {
                    // t1 = Server // t2 = String //
                    String desc = tuple.getT2() + "\nType: `" + tuple.getT1().getType() + "`";
                    return EmbedCreateFields.Field.of("ID: " + tuple.getT1().getId(), desc, false);
                })
                .collectList()
                .map(l -> EmbedCreateSpec.builder()
                        .title("Registered servers")
                        .fields(l)
                        .footer("Do /list local <id> to show info about a specific server", null)
                        .build()
                )
                .flatMap(e -> event.createFollowup().withEmbeds(e))
                .then();
    }

    private Mono<Void> showServerInfo(ChatInputInteractionEvent event) {
        Optional<Server> serverOptional = event.getOptions().get(0)
                .getOption("id")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .flatMap(Util::toLong)
                .flatMap(serverRepository::find);

        if (Util.isNotMciAdmin(event) || serverOptional.isEmpty()) {
            serverOptional = event.getInteraction().getGuildId()
                    .map(Snowflake::asLong)
                    .flatMap(serverRepository::findByGuildId);
        }

        final Server server = serverOptional.orElse(null);

        if (server == null)
            return event.reply("Invalid ID. (use `/list servers` to show all the servers)").withEphemeral(true);

        Mono<String> users = Flux.fromIterable(server.getBlacklistedUsers())
                .flatMap(u -> {
                    Mono<String> tag = client.getUserById(Snowflake.of(u.getDiscordAccounts().get(0))).map(User::getTag);
                    Mono<String> username = Util.getMinecraftUsername(u.getMinecraftAccounts().get(0));
                    return Mono.zip(Mono.just(u.getId()), tag, username);
                })
                .map(t -> "ID: `%s` (`%s`, `%s`)".formatted(t.getT1(), t.getT2(), t.getT3()))
                .collectList()
                .map(l -> l.isEmpty() ? "None" : String.join("\n", l));

        return event.deferReply()
                .then(Mono.zip(getServerDesc(server), users))
                .map(t -> EmbedCreateSpec.builder()
                        .title("Server (ID: %d)".formatted(server.getGuildId()))
                        .addField("Info", t.getT1(), false) // t1 = String (info)
                        .addField("Blacklisted Users", t.getT2(), false) // t2 = String (blacklisted)
                        .build())
                .flatMap(e -> event.createFollowup().withEmbeds(e))
                .then();
    }

    private Mono<String> getServerDesc(Server server) {
        if (server.getType() == ServerType.MINECRAFT) {
            return Mono.just("Registrar: `%s`".formatted(server.getRegistrarName()));
        } else {
            return client.getGuildById(Snowflake.of(server.getGuildId()))
                    .map(Guild::getName)
                    .map("Discord Server: `%s`"::formatted);
        }
    }

    public Mono<Void> manageServerBlacklists(ChatInputInteractionEvent event, ApplicationCommandInteractionOption subcommand) {
        Server server = event.getInteraction().getGuildId()
                .map(Snowflake::asLong)
                .flatMap(serverRepository::findByGuildId)
                .orElse(null);

        if (server == null) return event.reply("your server is not allowed to do that").withEphemeral(true);

        String operation = subcommand.getOption("operation")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");

        BlacklistedUser user = subcommand.getOption("id")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .flatMap(userRepository::findById)
                .orElse(null);

        if (user == null) return event.reply("unknown user (use /list to find the id)").withEphemeral(true);

        return switch (operation) {
            case "add" -> {
                server.getBlacklistedUsers().add(user);
                serverRepository.save(server);

                Mono<Void> action = switch (server.getType()) {
                    case MINECRAFT -> Flux.fromIterable(user.getMinecraftAccounts())
                            .flatMap(Util::getMinecraftUsername)
                            .collectList()
                            .map(l -> new MinecraftUserList(l, user.getReason(), false))
                            .doOnNext(list -> {
                                log.info("Sending {} to RabbitMQ.", list);
                                rabbitTemplate.convertAndSend(MCIBot.EXCHANGE, String.valueOf(server.getId()), list);
                            })
                            .then();
                    case DISCORD -> client.getGuildById(Snowflake.of(server.getGuildId()))
                            .flatMap(g -> Flux.fromIterable(user.getDiscordAccounts())
                                    .map(Snowflake::of)
                                    .flatMap(s -> g.ban(s).withReason(user.getReason()))
                                    .then());
                };

                yield event.deferReply()
                        .then(action)
                        .then(event.createFollowup("User has been blacklisted."))
                        .then();
            }
            case REMOVE -> {
                server.getBlacklistedUsers().remove(user);
                serverRepository.save(server);

                Mono<Void> action = switch (server.getType()) {
                    case MINECRAFT -> Flux.fromIterable(user.getMinecraftAccounts())
                            .flatMap(Util::getMinecraftUsername)
                            .collectList()
                            .map(l -> new MinecraftUserList(l, user.getReason(), true))
                            .doOnNext(list -> {
                                log.info("Sending {} to RabbitMQ.", list);
                                rabbitTemplate.convertAndSend(MCIBot.EXCHANGE, String.valueOf(server.getId()), list);
                            })
                            .then();
                    case DISCORD -> client.getGuildById(Snowflake.of(server.getGuildId()))
                            .flatMap(g -> Flux.fromIterable(user.getDiscordAccounts())
                                    .map(Snowflake::of)
                                    .flatMap(g::unban)
                                    .then());
                };

                yield event.deferReply()
                        .then(action)
                        .then(event.createFollowup("User has been blacklisted."))
                        .then();
            }

            default -> event.reply("unknown operation").withEphemeral(true);
        };
    }

    private Mono<Void> getRemoveListener(Server server, ChatInputInteractionEvent other, Message message) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this action.").withEphemeral(true);

                    log.info("{} removed server {} ", other.getInteraction().getUser().getTag(), server.getId());
                    serverRepository.delete(server);
                    return evt.edit().withComponents(REMOVED)
                            .then(evt.createFollowup("Successfully removed server.").withEphemeral(true))
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }
}
