package ru.darkkeks.vkmirror;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.vk.UserLongPoll;
import ru.darkkeks.vkmirror.vk.object.Message;

import java.util.concurrent.ExecutionException;

public class VkMirrorLongPoll extends UserLongPoll {

    private static final Logger logger = LoggerFactory.getLogger(VkMirrorLongPoll.class);

    private VkMirror vkMirror;

    public VkMirrorLongPoll(VkMirror vkMirror, VkApiClient client, UserActor actor) {
        super(client, actor);
        this.vkMirror = vkMirror;
    }

    @Override
    public void newMessage(Message message) {
        logger.info("New message from {} -> {}", message.getFrom(), message.getText());

        vkMirror.getTelegramChat(message.getPeerId()).thenApply(s -> {
            System.out.println("Found chat " + s);
            if(s == null) {
                try {
                    System.out.println("Creating new chat");
                    return vkMirror.createTelegramChat(message.getPeerId(), vkMirror.getChannelTitle(message.getFrom())).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            return s;
        }).thenAccept(s -> {
            logger.info("SEEEEEEEEENDDDDDDDDDIIIIIINNNNNNNNNNGGGGGGGG");
            vkMirror.sendMessage(((TdApi.ChatTypeSupergroup) s.type).supergroupId, message);
        }).exceptionally(e -> {
            logger.error("Can't process new message", e);
            return null;
        });
    }
}
