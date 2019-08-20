package ru.darkkeks.vkmirror.vk.object;

import com.google.gson.annotations.SerializedName;

import java.net.URL;

public class ChatSettings {

    @SerializedName("photo")
    private Photos photos;

    @SerializedName("title")
    private String title;

    public Photos getPhotos() {
        return photos;
    }

    public String getTitle() {
        return title;
    }

    public static class Photos {
        @SerializedName("photo_50")
        private URL photo50;

        @SerializedName("photo_100")
        private URL photo100;

        @SerializedName("photo_200")
        private URL photo200;


        public URL getPhoto50() {
            return photo50;
        }

        public URL getPhoto100() {
            return photo100;
        }

        public URL getPhoto200() {
            return photo200;
        }
    }
}
