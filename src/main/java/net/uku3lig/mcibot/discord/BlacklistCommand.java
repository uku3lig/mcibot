package net.uku3lig.mcibot.discord;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.uku3lig.mcibot.MCIBot;
import net.uku3lig.mcibot.model.BlacklistedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
public class BlacklistCommand implements ICommand {
    private static final WebClient client = WebClient.create("https://api.mojang.com");

    @Override
    public CommandData getCommandData() {
        return Commands.slash("blacklist", "Blacklist an user.")
                .addOption(OptionType.STRING, "uuid", "The user's UUID.", true)
                .addOption(OptionType.STRING, "userid", "The user to blacklist.", true)
                .addOption(OptionType.STRING, "reason", "The reason for the blacklist.");
    }

    @Override
    public void onCommand(GenericCommandInteractionEvent event) {
        UUID uuid = UUID.fromString(Objects.requireNonNull(event.getOption("uuid")).getAsString());
        String userId = Objects.requireNonNull(event.getOption("userid")).getAsString();
        String reason = Optional.ofNullable(event.getOption("reason")).map(OptionMapping::getAsString).orElse(null);
        BlacklistedUser user = new BlacklistedUser(uuid, userId, reason);

        MCIBot.getJda().retrieveUserById(userId).queue(u -> checkMcAccount(uuid, event, user),
                new ErrorHandler().handle(ErrorResponse.UNKNOWN_USER, e -> event.reply("User `" + userId + "` was not found. Are you sure the ID is correct?")
                        .addActionRow(new AreYouSureButton(Arrays.asList(uuid.toString(), userId, reason)).getButton()).queue()));
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
                .subscribe(p -> event.reply("Not implemented yet. User: `" + user + "`\nMinecraft username: `" + p.getName() + "`").queue());
    }

    @Data
    private static class Profile {
        private String name;
    }

    @NoArgsConstructor
    public static class AreYouSureButton extends IButton {
        public AreYouSureButton(List<String> args) {
            super(args);
        }

        @Override
        public Button getButtonBase() {
            return Button.primary("are_you_sure", "Click to confirm");
        }

        @Override
        public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
            String reason = args.get(2).equals("null") ? null : args.get(2);
            checkMcAccount(UUID.fromString(args.get(0)), event, new BlacklistedUser(UUID.fromString(args.get(0)), args.get(1), reason));
        }
    }
}
