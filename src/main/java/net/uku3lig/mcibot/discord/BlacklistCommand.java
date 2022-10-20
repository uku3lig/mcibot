package net.uku3lig.mcibot.discord;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.model.BlacklistedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class BlacklistCommand implements ICommand {
    private static final WebClient client = WebClient.create("https://api.mojang.com");

    @Override
    public CommandData getCommandData() {
        return Commands.slash("blacklist", "Blacklist an user.")
                .addOption(OptionType.STRING, "uuid", "The user's UUID.", true)
                .addOption(OptionType.USER, "user", "The user to blacklist.", true)
                .addOption(OptionType.STRING, "reason", "The reason for the blacklist.");
    }

    @Override
    public void onCommand(GenericCommandInteractionEvent event) {
        UUID uuid = UUID.fromString(Objects.requireNonNull(event.getOption("uuid")).getAsString());
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse(null);

        BlacklistedUser blacklistedUser = new BlacklistedUser(uuid, user.getId(), reason);

        checkMcAccount(uuid, event, blacklistedUser);
    }

    private static void checkMcAccount(UUID uuid, IReplyCallback event, BlacklistedUser user) {
        client.get().uri("/user/profile/" + uuid).exchangeToMono(res -> {
                    if (res.statusCode().equals(HttpStatus.NO_CONTENT)) {
                        event.replyFormat("User `%s` was not found, are you sure the UUID is correct?", uuid).queue();
                        return Mono.empty();
                    } else if (!res.statusCode().equals(HttpStatus.OK)) {
                        event.replyFormat("An unknown error happened. (`%d`)", res.statusCode().value()).queue();
                        return res.bodyToMono(String.class).map(RuntimeException::new).flatMap(Mono::error);
                    } else {
                        return res.bodyToMono(Profile.class);
                    }
                })
                // todo check if user is already blacklisted
                // todo add button to say hi :3 aer you sure you want to blacklist the user :3
                .subscribe(p -> MCIBot.getJda().retrieveUserById(user.getDiscordId())
                        .flatMap(u -> event.reply("Not implemented yet. User: `%s`%nMinecraft username: `%s`%nDiscord tag: `%s`"
                                .formatted(user, p.getName(), u.getAsTag()))).queue());
    }

    @Data
    private static class Profile {
        private String name;
    }
}
