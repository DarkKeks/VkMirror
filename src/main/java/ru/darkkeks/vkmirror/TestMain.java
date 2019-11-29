package ru.darkkeks.vkmirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.tdlib.TelegramClient;
import ru.darkkeks.vkmirror.tdlib.TelegramCredentials;

public class TestMain {

    private static final Logger logger = LoggerFactory.getLogger(TestMain.class);

    public static void main(String[] args) {
//        TelegramClient c1 = new TelegramClient(TelegramCredentials.phone(Config.API_ID, Config.API_HASH,
//                Config.PHONE_NUMBER));
        TelegramClient c2 = new TelegramClient(TelegramCredentials.bot(Config.API_ID, Config.API_HASH,
                "799490609:AAFujtoE1oKSO4K1_eBTs1XucxKqZ2OzhNs"));

//        c1.start();
        c2.start();
//        c1.groupById(1357704609).thenAccept(g -> {
//            c1.chatAddUser(g.get().id, c2.getMyId());
//        });
        c2.groupById(1357704609);



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
