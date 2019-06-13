package ru.darkkeks.vkmirror.vk;

import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class GetUserLongPollEventsResponse {

    @SerializedName("ts")
    private Integer ts;

    @SerializedName("updates")
    private List<JsonArray> updates;

    public Integer getTs() {
        return ts;
    }

    public List<JsonArray> getUpdates() {
        return updates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetUserLongPollEventsResponse that = (GetUserLongPollEventsResponse) o;
        return Objects.equals(ts, that.ts) &&
                Objects.equals(updates, that.updates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ts, updates);
    }

    @Override
    public String toString() {
        return "GetUserLongPollEventsResponse{" +
                "ts=" + ts +
                ", updates=" + updates +
                '}';
    }
}
