package net.uku3lig.mcibot.model;

import java.util.Collection;

public record MinecraftUserList(Collection<String> usernames, String reason, boolean pardon) {
}
