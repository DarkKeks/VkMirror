package ru.darkkeks.vkmirror.vk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException;
import com.vk.api.sdk.objects.messages.LongpollParams;
import ru.darkkeks.vkmirror.vk.object.Message;

public class UserLongPoll {

    private static final int WAIT_TIME = 25;

    private VkApiClient client;
    private UserActor actor;
    private LongPollListener listener;

    public UserLongPoll(VkApiClient client, UserActor actor, LongPollListener listener) {
        this.client = client;
        this.actor = actor;
        this.listener = listener;
    }

    public void run() throws ClientException, ApiException {
        LongpollParams longPollServer = getLongPollServer();
        int lastTimeStamp = longPollServer.getTs();

        while (true) {
            try {
                GetUserLongPollEventsResponse events = new GetUserLongPollEventsQuery(client,
                        String.format("https://%s", longPollServer.getServer()),
                        longPollServer.getKey(),
                        lastTimeStamp)
                        .mode(2 | 8 | 64)
                        .version(3)
                        .waitTime(WAIT_TIME)
                        .execute();
                events.getUpdates().forEach(this::parse);
                lastTimeStamp = events.getTs();
            } catch (LongPollServerKeyExpiredException e) {
                longPollServer = getLongPollServer();
            }
        }
    }

    private LongpollParams getLongPollServer() throws ClientException, ApiException {
        return client.messages().getLongPollServer(actor)
                .lpVersion(3)
                .execute();
    }

    private void parse(JsonElement update) {
        JsonArray updateArray = update.getAsJsonArray();

        int id = updateArray.get(0).getAsInt();

        //noinspection SwitchStatementWithTooFewBranches
        switch (id) {
            case 4:
                listener.newMessage(client.getGson().fromJson(update, Message.class));
                break;

        }
    }

}
