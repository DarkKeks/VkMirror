package ru.darkkeks.vkmirror.vk;

import com.vk.api.sdk.callback.longpoll.LongPollQueryBuilder;
import com.vk.api.sdk.callback.objects.longpoll.GetLongPollEventsActInfo;
import com.vk.api.sdk.client.VkApiClient;

import java.util.Arrays;
import java.util.Collection;

public class GetUserLongPollEventsQuery extends LongPollQueryBuilder<GetUserLongPollEventsQuery,
        GetUserLongPollEventsResponse> {

    public GetUserLongPollEventsQuery(VkApiClient client, String url, String key, Integer ts) {
        super(client, url, GetUserLongPollEventsResponse.class);
        act(GetLongPollEventsActInfo.CHECK);
        key(key);
        ts(ts);
    }

    public GetUserLongPollEventsQuery waitTime(Integer value) {
        return unsafeParam("wait", value);
    }

    public GetUserLongPollEventsQuery key(String value) {
        return unsafeParam("key", value);
    }

    public GetUserLongPollEventsQuery ts(Integer value) {
        return unsafeParam("ts", value);
    }

    public GetUserLongPollEventsQuery act(GetLongPollEventsActInfo actInfo) {
        return unsafeParam("act", actInfo.getValue());
    }

    public GetUserLongPollEventsQuery mode(Integer value) {
        return unsafeParam("mode", value);
    }

    public GetUserLongPollEventsQuery version(Integer value) {
        return unsafeParam("version", value);
    }

    @Override
    protected GetUserLongPollEventsQuery getThis() {
        return this;
    }

    @Override
    protected Collection<String> essentialKeys() {
        return Arrays.asList("act", "key", "ts");
    }
}
