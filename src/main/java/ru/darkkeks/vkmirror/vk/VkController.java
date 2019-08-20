package ru.darkkeks.vkmirror.vk;

import com.google.gson.GsonBuilder;
import com.vk.api.sdk.client.Utils;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.groups.Group;
import com.vk.api.sdk.objects.users.Fields;
import com.vk.api.sdk.objects.users.User;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.vk.object.ChatSettings;
import ru.darkkeks.vkmirror.vk.object.Message;
import ru.darkkeks.vkmirror.vk.object.MyConversation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VkController {

    private static final Logger logger = LoggerFactory.getLogger(VkController.class);

    private static final int MULTICHAT_BASE = 2_000_000_000;
    private static final int GROUP_BASE = 1_000_000_000;

    private VkApiClient client;
    private UserActor actor;
    private Integer myId;

    private Map<Class<?>, MessageProcessor<?>> handlers;

    public VkController(int userId, String userToken) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Message.class, new Message.MessageDeserializer());

        client = new VkApiClient(new HttpTransportClient(), builder.create(), 3);
        actor = new UserActor(userId, userToken);

        try {
            myId = client.users().get(actor).execute().get(0).getId();
        } catch (ApiException | ClientException e) {
            logger.error("Can't get my id", e);
        }

        handlers = new HashMap<>();

        Random random = new Random();

        addHandler(TdApi.MessageText.class, (id, message, content) -> Collections.singletonList(
                client.messages().send(actor)
                        .peerId(id)
                        .message(content.text.text)
                        .randomId(random.nextInt())
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

    public Integer getMyId() {
        return myId;
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
