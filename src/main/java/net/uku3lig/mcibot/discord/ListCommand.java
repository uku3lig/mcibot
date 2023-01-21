package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AllArgsConstructor;
import net.uku3lig.mcibot.discord.core.ICommand;
import net.uku3lig.mcibot.jpa.ServerRepository;
import net.uku3lig.mcibot.jpa.UserRepository;
import net.uku3lig.mcibot.model.BlacklistedUser;
import net.uku3lig.mcibot.util.Util;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static discord4j.core.object.command.ApplicationCommandOption.Type.INTEGER;
import static discord4j.core.object.command.ApplicationCommandOption.Type.SUB_COMMAND;

@Service
@AllArgsConstructor
public class ListCommand implements ICommand {
    private static final Button NO_PROOF = Button.secondary("proof", "No proof link").disabled();

    private GatewayDiscordClient client;
    private UserRepository userRepository;
    private final ServerRepository serverRepository;

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
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotServerOwner(event, serverRepository))
            return event.reply("You're not allowed to do that.").withEphemeral(true);

        ApplicationCommandInteractionOption subcommand = event.getOptions().get(0);

        Mono<Void> action = (switch (subcommand.getName()) {
            case "all" -> {
                List<BlacklistedUser> users = userRepository.findAll();

                yield event.deferReply()
                        .thenMany(Flux.fromIterable(users))
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

                Button proof = Optional.ofNullable(bu.get().getProofUrl()).map(u -> Button.link(u, "Proof")).orElse(NO_PROOF);

                yield event.deferReply()
                        .then(Mono.zip(discord, minecraft))
                        .map(t -> EmbedCreateSpec.builder()
                                .title("Blacklisted user (ID: %d)".formatted(bu.get().getId()))
                                .addField("Discord Accounts", t.getT1(), false)
                                .addField("Minecraft Accounts", t.getT2(), false)
                                .addField("Reason", reason, false)
                                .build())
                        .flatMap(e -> event.createFollowup().withEmbeds(e).withComponents(ActionRow.of(proof)));
            }
            default -> event.reply("Invalid subcommand.").withEphemeral(true);
        }).then();

        return Util.checkMciAdmin(event).then(action);
    }
}
