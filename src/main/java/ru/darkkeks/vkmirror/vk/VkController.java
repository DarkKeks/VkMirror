package ru.darkkeks.vkmirror.vk;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.groups.Group;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.messages.responses.GetChatPreviewResponse;
import com.vk.api.sdk.objects.users.Fields;
import com.vk.api.sdk.objects.users.User;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.vk.object.ChatSettings;
import ru.darkkeks.vkmirror.vk.object.MyConversation;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class VkController {

    private static final Logger logger = LoggerFactory.getLogger(VkController.class);

    private static final int MULTICHAT_BASE = 2_000_000_000;
    private static final int GROUP_BASE = 1_000_000_000;

    private VkApiClient client;
    private UserActor actor;
    private Integer myId;

    private Map<Class<?>, MessageProcessor<?>> handlers;

    @Inject
    public VkController(VkApiClient client, UserActor actor) {
        this.client = client;
        this.actor = actor;

        try {
            myId = client.users().get(actor).execute().get(0).getId();
        } catch (ApiException | ClientException e) {
            logger.error("Can't get my id", e);
        }

        handlers = new HashMap<>();

        addHandler(TdApi.MessageText.class, (id, message, content) -> Collections.singletonList(
                client.messages().send(actor)
                        .peerId(id)
                        .message(content.text.text)
                        .randomId(ThreadLocalRandom.current().nextInt())
                        .execute()));
    }

    private <T extends TdApi.MessageContent> void addHandler(Class<T> clazz, MessageHandler<T> handler) {
        handlers.put(clazz, new MessageProcessor<>(clazz, handler));
    }

    public void run(UserLongPoll longPoll) {
        try {
            longPoll.run();
        } catch (ClientException | ApiException e) {
            logger.error("Long polling exception", e);
        }
    }

    public List<Integer> sendMessage(int vkPeerId, TdApi.Message message) {
        MessageProcessor<?> messageProcessor = handlers.get(message.content.getClass());

        if(messageProcessor != null) {
            return messageProcessor.accept(vkPeerId, message);
        } else {
            logger.info("Received unsupported message: {}", message.content.getClass());
            return Collections.emptyList();
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

    public Path downloadConversationImage(int peerId) {
        try {
            MyGetConversationsResponse result = new MyGetConversationsQuery(client, actor, peerId).execute();
            MyConversation conversation = result.getItems().get(0);

            URL photo = null;

            if (isMultichat(peerId)) {
                ChatSettings chatSettings = conversation.getChatSettings();
                if (chatSettings != null && chatSettings.getPhotos() != null) {
                    photo = chatSettings.getPhotos().getPhoto200();
                }
            } else if (isGroup(peerId)) {
                Group group = result.getGroups().get(0);
                photo = group.getPhoto200();
            } else {
                User user = result.getProfiles().get(0);
                List<UserXtrCounters> userDetails = client.users().get(actor)
                        .userIds(String.valueOf(user.getId()))
                        .fields(Fields.PHOTO_200)
                        .execute();

                URL userPhoto = userDetails.get(0).getPhoto200();
                if (userPhoto != null) {
                    photo = userPhoto;
                }
            }

            if (photo != null) {
                Path tempFile = Files.createTempFile("vkmirror-image-", null);
                ReadableByteChannel rbc = Channels.newChannel(photo.openStream());
                FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                return tempFile;
            }
        } catch (ApiException | ClientException e) {
            logger.error("Can't get peer info", e);
        } catch (IOException e) {
            logger.error("Can't load image", e);
        }
        return null;
    }

    /**
     * Multichat is a chat with multiple people (or group bots)
     * @return true if chat with that id is a multichat, false otherwise
     */
    public boolean isMultichat(int id) {
        return id >= MULTICHAT_BASE;
    }

    /**
     * @return true if chat with that id is a private chat with group
     */
    public boolean isGroup(int id) {
        return id < 0;
    }

    public boolean isPrivateChat(int id) {
        return !isMultichat(id) && !isGroup(id);
    }

    public UserActor getActor() {
        return actor;
    }

    public VkApiClient getClient() {
        return client;
    }

    public Integer getMyId() {
        return myId;
    }

    public String getChannelTitle(int peerId) {
        try {
            if (isMultichat(peerId)) {
                GetChatPreviewResponse chat =
                        getClient().messages().getChatPreview(getActor())
                                .peerId(peerId)
                                .execute();
                return chat.getPreview().getTitle();
            } else if (isGroup(peerId)) {
                GroupFull group = getClient().groups().getById(getActor())
                        .groupId(Integer.toString(-peerId))
                        .execute().get(0);
                return group.getName();
            } else {
                UserXtrCounters user = getClient().users().get(getActor())
                        .userIds(Integer.toString(peerId))
                        .execute().get(0);

                return String.format("%s %s", user.getFirstName(), user.getLastName());
            }
        } catch (ClientException | ApiException e) {
            logger.error("Cant get channel title {}: ", peerId, e);
            return null;
        }
    }

    private static class MessageProcessor<T extends TdApi.MessageContent> {

        private Class<T> clazz;
        private MessageHandler<T> handler;

        public MessageProcessor(Class<T> clazz, MessageHandler<T> handler) {
            this.clazz = clazz;
            this.handler = handler;
        }

        public List<Integer> accept(int vkPeerId, TdApi.Message message) {
            try {
                return handler.accept(vkPeerId, message, clazz.cast(message.content));
            } catch (Exception e) {
                logger.error("Exception processing message", e);
            }
            return Collections.emptyList();
        }
    }
    private interface MessageHandler<T extends TdApi.MessageContent> {
        List<Integer> accept(Integer vkPeerId, TdApi.Message message, T content) throws Exception;
    }
}
