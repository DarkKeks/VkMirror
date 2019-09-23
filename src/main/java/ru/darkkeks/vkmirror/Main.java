package ru.darkkeks.vkmirror;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.autoreg.BotDao;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
//        new VkMirror();

        HikariDataSource dataSource = Config.createDataSource();

        BotDao botDao = new BotDao(dataSource);
        botDao.getFreeBots().forEach(System.out::println);
//
//        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
//
//        VkMirrorTelegram account = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, false, "+79854473321");
//
//        BotAutoReg botAutoReg = new BotAutoReg(account, executor);
//
//        botAutoReg.setDescription("@vkmirror_1_bot", "Описание это жоско)0)0").exceptionally(e -> {
//            logger.error("Can't set description", e);
//            return null;
//        });
//
//        botAutoReg.register("vkmirror_1_bot", "Еще один собеседник в вк").thenAccept(result -> {
//            logger.info("Register result: {}", result);
//        }).exceptionally(e -> {
//            logger.error("Can't register bot", e);
//            return null;
//        });
    }

}
