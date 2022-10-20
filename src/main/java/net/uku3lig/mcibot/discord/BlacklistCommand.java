package net.uku3lig.mcibot.discord;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.uku3lig.mcibot.model.BlacklistedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class BlacklistCommand implements ICommand {
    private final WebClient client = WebClient.create("https://api.mojang.com");

    @Override
    public CommandData getCommandData() {
        return Commands.slash("blacklist", "Blacklist an user.")
                .addOption(OptionType.STRING, "uuid", "The user's UUID.", true)
                .addOption(OptionType.STRING, "userid", "The user to blacklist.", true)
                .addOption(OptionType.STRING, "reason", "The reason for the blacklist.", false);
    }

    @Override
    public void onCommand(GenericCommandInteractionEvent event) {
        UUID uuid = UUID.fromString(Objects.requireNonNull(event.getOption("uuid")).getAsString());
        long userId = Long.parseLong(Objects.requireNonNull(event.getOption("userid")).getAsString());
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse(null);
        BlacklistedUser user = new BlacklistedUser(uuid, userId, reason);

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
                .subscribe(p -> event.reply("Not implemented yet. User: `" + user + "`\nMinecraft username: `" + p.getName() + "`").queue());
    }

    @Data
    private static class Profile {
        private String name;
    }
}
