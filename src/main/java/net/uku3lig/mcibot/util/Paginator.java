package net.uku3lig.mcibot.util;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.reaction.ReactionEmoji;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// I probably made this in the worst possible way, but I just want to get it done man :(
public class Paginator<T extends Displayable> {
    private static final Button FIRST = Button.secondary("paginator_first", ReactionEmoji.unicode("⏮"));
    private static final Button BACK = Button.secondary("paginator_back", ReactionEmoji.unicode("◀"));
    private static final Button STOP = Button.danger("paginator_stop", ReactionEmoji.unicode("⏹"));
    private static final Button NEXT = Button.secondary("paginator_next", ReactionEmoji.unicode("▶"));
    private static final Button LAST = Button.secondary("paginator_last", ReactionEmoji.unicode("⏭"));

    private final AtomicInteger position = new AtomicInteger(0);
    private final GatewayDiscordClient client;
    private final List<T> objects;

    public Paginator(Snowflake messageId, GatewayDiscordClient client, List<T> objects) {
        this.client = client;
        this.objects = objects;

        client.on(ButtonInteractionEvent.class)
                .filter(event -> position.get() >= 0 && event.getCustomId().startsWith("paginator") && event.getMessageId().equals(messageId))
                .flatMap(this::handleButtonPress)
                .subscribe();
    }

    public static <T extends Displayable> Mono<Void> paginate(List<T> objects, DeferrableInteractionEvent event) {
        if (objects.isEmpty()) {
            return event.createFollowup("No items to be displayed.").withEphemeral(true).then();
        }

        return objects.get(0).display(event.getClient())
                .flatMap(embed -> event.createFollowup()
                        .withEmbeds(embed)
                        .withComponents(ActionRow.of(FIRST.disabled(), BACK.disabled(), STOP, NEXT, LAST))
                        .map(message -> new Paginator<>(message.getId(), event.getClient(), objects))
                )
                .then();
    }

    private Mono<Void> handleButtonPress(ButtonInteractionEvent event) {
        if (event.getCustomId().equals("paginator_first")) {
            position.set(0);
        } else if (event.getCustomId().equals("paginator_back")) {
            position.decrementAndGet();
        } else if (event.getCustomId().equals("paginator_next")) {
            position.incrementAndGet();
        } else if (event.getCustomId().equals("paginator_last")) {
            position.set(objects.size() - 1);
        } else if (event.getCustomId().equals("paginator_stop")) {
            position.set(-1);
            return event.edit().withComponents();
        }

        // shouldn't be needed, but we never know /shrug
        Util.clamp(position, 0, objects.size() - 1);

        return event.deferEdit()
                .then(objects.get(position.get()).display(client))
                .flatMap(embed -> event.editReply()
                        .withEmbeds(embed)
                        .withComponents(ActionRow.of(
                                position.get() == 0 ? FIRST.disabled() : FIRST,
                                position.get() == 0 ? BACK.disabled() : BACK,
                                STOP,
                                position.get() == objects.size() - 1 ? NEXT.disabled() : NEXT,
                                position.get() == objects.size() - 1 ? LAST.disabled() : LAST
                        ))
                )
                .then();
    }
}
