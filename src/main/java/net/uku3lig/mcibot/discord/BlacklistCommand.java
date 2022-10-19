package net.uku3lig.mcibot.discord;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.uku3lig.mcibot.model.BlacklistedUser;

import java.util.Objects;
import java.util.UUID;

public class BlacklistCommand implements ICommand {
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
        BlacklistedUser user = new BlacklistedUser(uuid, userId, Objects.requireNonNull(event.getOption("reason")).getAsString());
        event.reply("Not implemented yet. User: `" + user + "`").queue();
    }
}
