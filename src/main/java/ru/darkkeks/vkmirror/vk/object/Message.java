package ru.darkkeks.vkmirror.vk.object;

import com.google.gson.*;

import java.lang.reflect.Type;

public class Message {

    private int messageId;
    private int flags;
    private int peerId;
    private int timestamp;
    private String text;
    private JsonObject attachements;

    private String title;
    private int from;

    public int getMessageId() {
        return messageId;
    }

    public int getFlags() {
        return flags;
    }

    public int getPeerId() {
        return peerId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public String getText() {
        return text;
    }

    public JsonObject getAttachements() {
        return attachements;
    }

    public String getTitle() {
        return title;
    }

    public int getFrom() {
        return from;
    }

    public static class MessageDeserializer implements JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonArray array = json.getAsJsonArray();

            Message result = new Message();
            result.messageId = array.get(1).getAsInt();
            result.flags = array.get(2).getAsInt();
            if(array.size() > 3) {
                result.peerId = array.get(3).getAsInt();
            }
            if(array.size() > 4) {
                result.timestamp = array.get(4).getAsInt();
                result.text = array.get(5).getAsString();
            }
            if(array.size() > 6) {
                JsonObject additional = array.get(6).getAsJsonObject();
                if(additional.has("title")) {
                    result.title = additional.get("title").getAsString();
                }
                if(additional.has("from")) {
                    result.from = Integer.parseInt(additional.get("from").getAsString());
                }
            }
            if(array.size() > 7) {
                result.attachements = array.get(7).getAsJsonObject();
            }
            if(result.from == 0) {
                result.from = result.peerId;
            }
            return result;
        }
    }

    public static class Flags {
        public static final int UNREAD = 1;
        public static final int OUTBOX = 2;
        public static final int REPLIED = 4;
        public static final int IMPORTANT = 8;
        public static final int CHAT = 16;
        public static final int FRIENDS = 32;
        public static final int SPAM = 64;
        public static final int DELЕTЕD = 128;
        public static final int FIXED = 256;
        public static final int MEDIA = 512;
        public static final int HIDDEN = 65536;
    }
}
