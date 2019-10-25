package ru.darkkeks.vkmirror.autoreg;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BotDataManager {

    private BotDao botDao;

    private Map<Integer, VkMirrorBot> bots;

    public BotDataManager(BotDao botDao) {
        this.botDao = botDao;
        this.bots = new HashMap<>();
    }

    public CompletableFuture<VkMirrorBot> getBotForVk(int id) {
        if(bots.containsKey(id)) {
            return CompletableFuture.completedFuture(bots.get(id));
        } else {
            return CompletableFuture.supplyAsync(() -> {
                VkMirrorBot bot = botDao.getBotByVkId(id);

                if(bot == null) {
                    bot = bindFree(id);
                }

                if(bot != null) {
                    bots.put(id, bot);
                }

                return bot;
            });
        }
    }


    // TODO Ask user if he wants to use one free bot on this vk account
    private VkMirrorBot bindFree(int id) {
        return null;
    }
}
