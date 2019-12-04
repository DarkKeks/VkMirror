package ru.darkkeks.vkmirror.vk.`object`

import com.google.gson.annotations.SerializedName
import java.net.URL

class ChatSettings {
    @SerializedName("photo")
    val photos: Photos? = null

    val title: String? = null

    class Photos {
        @SerializedName("photo_50")
        val photo50: URL? = null

        @SerializedName("photo_100")
        val photo100: URL? = null

        @SerializedName("photo_200")
        val photo200: URL? = null
    }
}