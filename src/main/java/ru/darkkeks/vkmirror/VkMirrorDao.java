package ru.darkkeks.vkmirror;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VkMirrorDao {

    private static final Logger logger = LoggerFactory.getLogger(VkMirrorDao.class);

    private static final String SELECT = "SELECT * FROM chats WHERE vkPeerId = ?";
    private static final String INSERT = "INSERT INTO chats(vkPeerId, telegramGroupId) VALUES (?, ?) " +
            "ON CONFLICT (vkPeerId) DO UPDATE SET " +
                "vkPeerId = excluded.vkPeerId," +
                "telegramGroupId = excluded.telegramGroupId";

    private HikariDataSource dataSource;

    public VkMirrorDao(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public VkMirrorChat getChat(int vkChatId) {
        logger.info("Loading chat from peer id {}", vkChatId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setInt(1, vkChatId);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                return new VkMirrorChat(
                        result.getInt("vkPeerId"),
                        result.getInt("telegramGroupId"));
            }
        } catch (SQLException e) {
            logger.error("Can't load chat", e);
        }
        return null;
    }

    public void save(VkMirrorChat chat) {
        logger.info("Saving chat VkMirrorChat(vkPeerId={}, telegramChatId={})", chat.getVkPeerId(),
                chat.getTelegramChannelId());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setInt(1, chat.getVkPeerId());
            statement.setInt(2, chat.getTelegramChannelId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Can't load chat", e);
        }
    }
}
