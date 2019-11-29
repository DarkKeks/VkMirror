package ru.darkkeks.vkmirror.autoreg;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BotDataManager {

    private BotDao botDao;

    private Map<Integer, VkMirrorBot> botsByVkId;
    private Map<String, VkMirrorBot> botsByTelegramId;

    @Inject
    public BotDataManager(BotDao botDao) {
        this.botDao = botDao;
        this.botsByVkId = new HashMap<>();
        this.botsByTelegramId = new HashMap<>();
    }

    public CompletableFuture<VkMirrorBot> getBotForVk(int id) {
        if (botsByVkId.containsKey(id)) {
            return CompletableFuture.completedFuture(botsByVkId.get(id));
        } else {
            return CompletableFuture.supplyAsync(() -> {
                VkMirrorBot bot = botDao.getBotByVkId(id);

                if (bot == null) {
                    bot = bindFree(id);
                }

                if (bot != null) {
                    botsByTelegramId.put(bot.getUsername(), bot);
                    botsByVkId.put(bot.getVkId(), bot);
                }

                return bot;
            });
        }
    }

    public CompletableFuture<VkMirrorBot> getBotForTelegram(String username) {
        if (botsByTelegramId.containsKey(username)) {
            return CompletableFuture.completedFuture(botsByTelegramId.get(username));
        } else {
            return CompletableFuture.supplyAsync(() -> {
                VkMirrorBot bot = botDao.getBotByTelegramUsername(username);
                if (bot != null) {
                    botsByTelegramId.put(bot.getUsername(), bot);
                    botsByVkId.put(bot.getVkId(), bot);
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
