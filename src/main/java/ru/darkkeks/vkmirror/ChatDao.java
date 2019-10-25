package ru.darkkeks.vkmirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.vk.ChatType;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// TODO Reflect changes in VkMirrorChat
@Singleton
public class ChatDao {

    private static final Logger logger = LoggerFactory.getLogger(ChatDao.class);

    private static final String SELECT_BY_VK = "SELECT * FROM chats WHERE vkPeerId = ?";
    private static final String SELECT_BY_TELEGRAM = "SELECT * FROM chats WHERE telegramGroupId = ?";
    private static final String INSERT = "INSERT INTO chats(vkpeerid, telegramgroupid) VALUES (?, ?) RETURNING id";

    private static final String CHECK_SYNC_VK = "SELECT COUNT(*) FROM messages " +
            "WHERE chat_id = ? AND vkMessageId = ?";
    private static final String CHECK_SYNC_TELEGRAM = "SELECT COUNT(*) FROM messages " +
            "WHERE chat_id = ? AND telegramMessageId = ?";
    private static final String INSERT_MESSAGE = "INSERT INTO messages(chat_id, vkmessageid, telegrammessageid) " +
            "VALUES (?, ?, ?)";

    private DataSource dataSource;

    @Inject
    public ChatDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Chat getChatBySql(String sql, int id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                return new Chat(
                        result.getInt("id"),
                        result.getInt("vkPeerId"),
                        result.getInt("telegramGroupId"),
                        ChatType.values()[result.getInt("chatType")]);
            }
        } catch (SQLException e) {
            logger.error("Can't load chat", e);
        }
        return null;
    }

    public Chat getChatByVkPeer(int vkChatId) {
        logger.info("Loading chat from peer id {}", vkChatId);

        return getChatBySql(SELECT_BY_VK, vkChatId);
    }

    public Chat getChatByTelegramGroup(int telegramGroupId) {
        logger.info("Loading chat from telegram group id {}", telegramGroupId);

        return getChatBySql(SELECT_BY_TELEGRAM, telegramGroupId);
    }

    public void save(Chat chat) {
        logger.info("Saving chat VkMirrorChat(vkPeerId={}, telegramChatId={})", chat.getVkPeerId(),
                chat.getTelegramId());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setInt(1, chat.getVkPeerId());
            statement.setInt(2, chat.getTelegramId());
            ResultSet resultSet = statement.executeQuery();
            if(resultSet.next()) {
                chat.setId(resultSet.getInt("id"));
            }
        } catch (SQLException e) {
            logger.error("Can't save chat", e);
        }
    }

    private boolean isSynced(String sql, int chatId, long messageId) {
        try(Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, chatId);
            statement.setLong(2, messageId);
            ResultSet resultSet = statement.executeQuery();
            if(resultSet.next()) {
                int count = resultSet.getInt(1);
                return count == 1;
            }
        } catch (SQLException e) {
            logger.error("Can't check message");
        }
        return false;
    }

    public boolean isSyncedVk(Chat chat, int messageId) {
        return isSynced(CHECK_SYNC_VK, chat.getId(), messageId);
    }

    public boolean isSyncedTelegram(Chat chat, long messageId) {
        return isSynced(CHECK_SYNC_TELEGRAM, chat.getId(), messageId);
    }

    public void saveMessage(Chat chat, int vkMessageId, long telegramMessageId) {
        logger.info("Saving message from chat VkMirrorChat(vkPeerId={}, telegramChatId={}) -> " +
                        "vkMessageId = {}, telegramMessageId = {}",
                chat.getVkPeerId(),
                chat.getTelegramId(),
                vkMessageId,
                telegramMessageId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
            statement.setInt(1, chat.getId());
            statement.setInt(2, vkMessageId);
            statement.setLong(3, telegramMessageId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Can't save chat", e);
        }
    }


}
