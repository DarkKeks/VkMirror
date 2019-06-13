package ru.darkkeks.vkmirror.vk;

import com.google.gson.GsonBuilder;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.vk.object.Message;

public class VkController {

    private static final Logger logger = LoggerFactory.getLogger(VkController.class);
    private static final int MULTICHAT_BASE = 2_000_000_000;
    private static final int GROUP_BASE = 1_000_000_000;

    private final VkApiClient client;
    private final UserActor actor;

    public VkController(int userId, String userToken) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Message.class, new Message.MessageDeserializer());

        client = new VkApiClient(new HttpTransportClient(), builder.create(), 3);
        actor = new UserActor(userId, userToken);
    }

    public void run(UserLongPoll longPoll) {
        try {
            longPoll.run();
        } catch (ClientException | ApiException e) {
            logger.error("Long polling exception", e);
        }
    }

    public String getChatUrl(int peerId) {
        if(isMultichat(peerId)) {
            return String.format("https://vk.com/im?sel=c%d", peerId - MULTICHAT_BASE);
        } else if(isGroup(peerId)) {
            return String.format("https://vk.com/im?sel=-%d", peerId - GROUP_BASE);
        }
        return String.format("https://vk.com/im?sel=%d", peerId);
    }

    public boolean isMultichat(int id) {
        return id >= MULTICHAT_BASE;
    }

    public boolean isGroup(int id) {
        return id < 0;
    }

    public UserActor getActor() {
        return actor;
    }

    public VkApiClient getClient() {
        return client;
    }
}
