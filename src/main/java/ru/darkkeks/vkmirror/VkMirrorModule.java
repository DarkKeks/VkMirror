package ru.darkkeks.vkmirror;

import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.darkkeks.vkmirror.tdlib.TelegramClient;
import ru.darkkeks.vkmirror.tdlib.TelegramCredentials;
import ru.darkkeks.vkmirror.vk.object.Message;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class VkMirrorModule extends AbstractModule {

    @Provides
    @Singleton
    public TelegramClient providesTelegramClient() {
        return new TelegramClient(TelegramCredentials.phone(Config.API_ID, Config.API_HASH, Config.PHONE_NUMBER));
    }

    @Provides
    @Singleton
    public UserActor providesUserActor() {
        return new UserActor(Config.USER_ID, Config.USER_TOKEN);
    }

    @Provides
    @Singleton
    public VkApiClient providesVkApiClient() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Message.class, new Message.MessageDeserializer());

        return new VkApiClient(new HttpTransportClient(), builder.create(), 3);
    }

    @Provides
    @Singleton
    public ScheduledExecutorService provideExecutorService() {
        return new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    @Provides
    @Singleton
    public DataSource provideDataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(Config.DATABASE_URL);
        config.setUsername(Config.DATABASE_USERNAME);
        config.setPassword(Config.DATABASE_PASSWORD);

        return new HikariDataSource(config);
    }
}
