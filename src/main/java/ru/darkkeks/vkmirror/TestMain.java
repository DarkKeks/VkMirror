package ru.darkkeks.vkmirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestMain {

    private static final Logger logger = LoggerFactory.getLogger(TestMain.class);

    public static void main(String[] args) {

//        kek.createPrivateChat(177257181).thenRun(() -> {
//            kek.sendMessage(177257181, "Alo");
//        });


//        VkMirrorTelegram t1 = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, false, Config.PHONE_NUMBER);
//        VkMirrorTelegram t2 = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, false, "+79854473321");

//        t1.createPrivateChat(886268639).thenAccept(chat -> {
//            System.out.println(String.format("t1(%d) -> t2(%d): %d", t1.getMyId(), t2.getMyId(), chat.id));
//        });
//
//        t2.createPrivateChat(177257181).thenAccept(chat -> {
//            System.out.println(String.format("t2(%d) -> t1(%d): %d", t2.getMyId(), t1.getMyId(), chat.id));
//        });

//        t1.searchPublicUsername("darkkeks_test_account").thenAccept(chat -> {
//            System.out.println(String.format("t1(%d) -> t2(%d): %d", t1.getMyId(), t2.getMyId(), chat.id));
//        });
//
//        t2.searchPublicUsername("darkkeks").thenAccept(chat -> {
//            System.out.println(String.format("t2(%d) -> t1(%d): %d", t2.getMyId(), t1.getMyId(), chat.id));
//        });


//        new VkMirror();

//        HikariDataSource dataSource = Config.createDataSource();
//
//        BotDao botDao = new BotDao(dataSource);
//        botDao.getFreeBots().forEach(System.out::println);
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
