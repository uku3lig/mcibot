package net.uku3lig.mcibot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
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
import net.uku3lig.mcibot.util.Util;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@AllArgsConstructor
public class PardonCommand implements ICommand {
    private static final Button PARDON_CONFIRM = Button.primary("pardon_confirm", "Pardon");
    private static final ActionRow PARDONED = ActionRow.of(Button.secondary("pardon", "Pardoned").disabled());

    private GatewayDiscordClient client;
    private UserRepository userRepository;
    private ServerRepository serverRepository;
    private RabbitTemplate rabbitTemplate;

    @Override
    public ApplicationCommandRequest getCommandData() {
        return ApplicationCommandRequest.builder()
                .name("pardon")
                .description("Pardon an user.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("minecraft")
                        .description("The user's Minecraft username.")
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("discord")
                        .description("The user's Discord ID.")
                        .type(ApplicationCommandOption.Type.USER.getValue())
                        .required(false)
                        .build())
                .build();
    }

    @Override
    public Mono<Void> onInteraction(ChatInputInteractionEvent event) {
        if (Util.isNotMciAdmin(event)) return event.reply("You need to be an admin to use this command.").withEphemeral(true);

        Optional<String> minecraft = event.getOption("minecraft").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        Optional<Mono<User>> discord = event.getOption("discord").flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asUser);

        Mono<Tuple2<BlacklistedUser, String>> get;
        if (minecraft.isPresent()) {
            get = Util.getMinecraftUUID(minecraft.get()).flatMap(uuid -> Mono.justOrEmpty(userRepository.findByMinecraftAccountsContaining(uuid)))
                    .zipWith(Mono.just(minecraft.get()));
        } else if (discord.isPresent()) {
            get = discord.get().flatMap(user -> Mono.justOrEmpty(userRepository.findByDiscordAccountsContaining(user.getId().asLong()))
                    .zipWith(Mono.just(user.getTag())));
        } else {
            return event.reply("You need to specify a username or a Discord user.").withEphemeral(true);
        }

        return event.deferReply()
                .then(get)
                .filter(t -> t.getT1().isGlobal()) // if not globally blacklisted, ignore
                .zipWhen(t -> Flux.fromIterable(t.getT1().getMinecraftAccounts()).flatMap(Util::getMinecraftUsername).collectList(), Util::t2to3)
                .flatMap(m -> m)
                .switchIfEmpty(event.createFollowup("User not found.").then(Mono.empty()))
                .flatMap(t -> event.createFollowup("Are you sure you want to pardon this user?")
                        .withComponents(Util.CHOICE_ROW)
                        .flatMap(m -> getConfirmListener(t.getT1(), t.getT2(), t.getT3(), m, event)));
    }

    private Mono<Void> getConfirmListener(BlacklistedUser user, String name, List<String> minecraft, Message message, ChatInputInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, message)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "confirm", message)) return Mono.empty();
                    if (!evt.getInteraction().getUser().equals(other.getInteraction().getUser()))
                        return evt.reply("You can't confirm this pardon.").withEphemeral(true);

                    log.info("New globally pardoned user: name={}, by={}", name, evt.getInteraction().getUser().getTag());
                    user.setGlobal(false);
                    userRepository.save(user);
                    List<Server> servers = serverRepository.findAll().stream().filter(s -> s.getBlacklistedUsers().contains(user)).toList();

                    return evt.edit().withComponents(PARDONED)
                            .then(evt.createFollowup("Pardon message sent to all owners.").withEphemeral(true))
                            .thenMany(Flux.fromIterable(servers))
                            .flatMap(server -> client.getGuildById(Snowflake.of(server.getDiscordId()))
                                    .flatMap(Guild::getOwner)
                                    .flatMap(User::getPrivateChannel)
                                    .flatMap(c -> c.createMessage("The MCI admin team has pardoned a user (name: `%s`)".formatted(name))
                                            .withComponents(ActionRow.of(PARDON_CONFIRM, Util.CANCEL_BUTTON)))
                                    .flatMap(msg -> getPardonListener(user, name, server, minecraft, msg, evt))
                                    .doOnError(t -> log.error("Could not send pardon message to owner.", t))
                            )
                            .then();
                }).timeout(Duration.ofMinutes(5))
                .onErrorResume(TimeoutException.class, t -> other.editReply().withComponents(Util.CANCELLED).then())
                .next();
    }

    private Mono<Void> getPardonListener(BlacklistedUser user, String name, Server server, List<String> minecraft, Message msg, ButtonInteractionEvent other) {
        return client.on(ButtonInteractionEvent.class, evt -> {
                    if (Util.isCancelButton(evt, msg)) return Util.onCancel(evt, other);
                    if (!Util.isButton(evt, "pardon_confirm", msg)) return Mono.empty();
                    log.info("Server owner {} pardoned {} on server {}", evt.getInteraction().getUser().getTag(), name, server.getMinecraftId());

                    final MinecraftUserList list = new MinecraftUserList(minecraft, null, true);
                    log.info("Sending {} to RabbitMQ.", list);
                    rabbitTemplate.convertAndSend(MCIBot.EXCHANGE, server.getMinecraftId().toString(), list);

                    server.getBlacklistedUsers().remove(user);
                    serverRepository.save(server);

                    return evt.edit().withComponents(PARDONED)
                            .then(client.getGuildById(Snowflake.of(server.getDiscordId())))
                            .flatMap(g -> Flux.fromIterable(user.getDiscordAccounts()).map(Snowflake::of).flatMap(g::unban).then())
                            .then(evt.createFollowup("User has been pardoned.").withEphemeral(true))
                            .then();
                })
                .timeout(Duration.ofDays(7))
                .onErrorResume(TimeoutException.class, t -> msg.edit().withComponents(Util.CANCELLED).then())
                .next();
    }
}
