package net.uku3lig.mcibot.util;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;

public interface Displayable {
    Mono<EmbedCreateSpec> display(GatewayDiscordClient client);
}
