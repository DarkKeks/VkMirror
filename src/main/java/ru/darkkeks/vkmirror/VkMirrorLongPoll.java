package ru.darkkeks.vkmirror;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.vk.UserLongPoll;
import ru.darkkeks.vkmirror.vk.object.Message;

public class VkMirrorLongPoll extends UserLongPoll {

    private static final Logger logger = LoggerFactory.getLogger(VkMirrorLongPoll.class);

    private VkMirror vkMirror;

    public VkMirrorLongPoll(VkMirror vkMirror, VkApiClient client, UserActor actor) {
        super(client, actor);
        this.vkMirror = vkMirror;
    }

    @Override
    public void newMessage(Message message) {
        logger.info("New message from {} -> id={} \"{}\"", message.getFrom(), message.getMessageId(),
                message.getText());

        vkMirror.sendMessage(message).exceptionally(e -> {
            logger.error("Can't process new message", e);
            return null;
        });
    }
}
