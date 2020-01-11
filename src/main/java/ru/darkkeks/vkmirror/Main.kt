package ru.darkkeks.vkmirror

import com.google.gson.GsonBuilder
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import kotlinx.coroutines.runBlocking
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.singleton
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.userTelegramCredentials
import ru.darkkeks.vkmirror.util.getEnv
import ru.darkkeks.vkmirror.vk.*
import ru.darkkeks.vkmirror.vk.`object`.Message

val USER_ID = getEnv("USER_ID").toInt()
val USER_TOKEN = getEnv("USER_TOKEN")

val API_ID = getEnv("API_ID").toInt()
val API_HASH = getEnv("API_HASH")

val PHONE_NUMBER = getEnv("PHONE_NUMBER")


val logger: Logger = LoggerFactory.getLogger("main")

val kodein = Kodein {
    bind<TelegramClient>() with singleton {
        TelegramClient(userTelegramCredentials(API_ID, API_HASH, PHONE_NUMBER))
    }

    bind<UserActor>() with singleton {
        UserActor(USER_ID, USER_TOKEN)
    }

    bind<VkApiClient>() with singleton {
        val gson = GsonBuilder().apply {
            registerTypeAdapter(Message::class.java, Message.MessageDeserializer())
            registerTypeAdapter(UserIsTyping::class.java, UserIsTyping.UserIsTypingDeserializer())
            registerTypeAdapter(UsersAreTyping::class.java, UsersAreTyping.UsersAreTypingDeserializer())
            registerTypeAdapter(MessageReadUpTo::class.java, MessageReadUpTo.MessageReadUpToDeserializer())
        }.create()

        VkApiClient(HttpTransportClient(), gson, 3)
    }

    bind<VkController>() with singleton {
        VkController(kodein)
    }

    bind<CoroutineDatabase>() with singleton {
        val credential = MongoCredential.createCredential("root", "admin", "root".toCharArray())
        KMongo.createClient(MongoClientSettings.builder().credential(credential).build()).coroutine
                .getDatabase("vk_mirror")
    }
}

fun main() = runBlocking {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Thread {} didn't handle exception", thread, throwable)
    }

    VkMirror(kodein).start()
}