package ru.darkkeks.vkmirror.vk;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.groups.Group;
import com.vk.api.sdk.objects.users.User;
import ru.darkkeks.vkmirror.vk.object.MyConversation;

import java.util.List;
import java.util.Objects;

public class MyGetConversationsResponse {

    /**
     * Total number
     */
    @SerializedName("count")
    private Integer count;

    @SerializedName("items")
    private List<MyConversation> items;

    @SerializedName("profiles")
    private List<User> profiles;

    @SerializedName("groups")
    private List<Group> groups;

    public Integer getCount() {
        return count;
    }

    public MyGetConversationsResponse setCount(Integer count) {
        this.count = count;
        return this;
    }

    public List<MyConversation> getItems() {
        return items;
    }

    public List<User> getProfiles() {
        return profiles;
    }

    public List<Group> getGroups() {
        return groups;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, profiles, items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyGetConversationsResponse getConversationsByIdExtendedResponse = (MyGetConversationsResponse) o;
        return Objects.equals(count, getConversationsByIdExtendedResponse.count) &&
                Objects.equals(profiles, getConversationsByIdExtendedResponse.profiles) &&
                Objects.equals(items, getConversationsByIdExtendedResponse.items);
    }

    @Override
    public String toString() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    public String toPrettyString() {
        final StringBuilder sb = new StringBuilder("GetConversationsByIdExtendedResponse{");
        sb.append("count=").append(count);
        sb.append(", profiles=").append(profiles);
        sb.append(", items=").append(items);
        sb.append('}');
        return sb.toString();
    }
}
