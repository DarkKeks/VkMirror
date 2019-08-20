package ru.darkkeks.vkmirror.vk;

import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.objects.base.UserGroupFields;

import java.util.Arrays;
import java.util.Collection;

public class MyGetConversationsQuery extends AbstractQueryBuilder<MyGetConversationsQuery, MyGetConversationsResponse> {

    public MyGetConversationsQuery(VkApiClient client, UserActor actor, Integer... peerIds) {
        super(client, "messages.getConversationsById", MyGetConversationsResponse.class);
        accessToken(actor.getAccessToken());
        peerIds(peerIds);
        extended(true);
    }

    public MyGetConversationsQuery extended(Boolean value) {
        return unsafeParam("extended", value);
    }

    public MyGetConversationsQuery groupId(Integer value) {
        return unsafeParam("group_id", value);
    }

    public MyGetConversationsQuery peerIds(Integer... value) {
        return unsafeParam("peer_ids", value);
    }

    public MyGetConversationsQuery fields(UserGroupFields... value) {
        return unsafeParam("fields", value);
    }

    @Override
    protected MyGetConversationsQuery getThis() {
        return this;
    }

    @Override
    protected Collection<String> essentialKeys() {
        return Arrays.asList("peer_ids", "access_token");
    }
}
