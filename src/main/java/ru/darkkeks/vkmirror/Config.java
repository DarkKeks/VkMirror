package ru.darkkeks.vkmirror;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Optional;

public class Config {

    public static final int USER_ID = Integer.parseInt(getEnv("USER_ID"));
    public static final String USER_TOKEN = getEnv("USER_TOKEN");

    public static final int API_ID = Integer.parseInt(getEnv("API_ID"));
    public static final String API_HASH = getEnv("API_HASH");

    public static final String PHONE_NUMBER = getEnv("PHONE_NUMBER");

    private static final String DATABASE_URL = getEnv("DATABASE_URL");
    private static final String DATABASE_USERNAME = getEnv("DATABASE_USERNAME");
    private static final String DATABASE_PASSWORD = getEnv("DATABASE_PASSWORD");

    public static final String BOT_TOKEN = getEnv("BOT_TOKEN");

    public static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(DATABASE_URL);
        config.setUsername(DATABASE_USERNAME);
        config.setPassword(DATABASE_PASSWORD);

        return new HikariDataSource(config);
    }

    private static String getEnv(String name) {
        return Optional.ofNullable(System.getenv(name)).orElseThrow(() -> new IllegalStateException("Env " + name));
    }
}
