package ru.darkkeks.vkmirror.autoreg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BotDao {

    private static final Logger logger = LoggerFactory.getLogger(BotDao.class);

    private static final String INSERT_BOT = "INSERT INTO bots(username, token, vk_id) VALUES (?, ?, ?) RETURNING id";
    private static final String SELECT_BOT_VK = "SELECT * FROM bots WHERE vk_id = ?";
    private static final String SELECT_BOT_TELEGRAM = "SELECT * FROM bots WHERE username = ?";

    private DataSource dataSource;

    @Inject
    public BotDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertBot(VkMirrorBot bot) {
        logger.info("Inserting {}", bot);
        try(Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(INSERT_BOT)) {
            preparedStatement.setString(1, bot.getUsername());
            preparedStatement.setString(2, bot.getToken());
            preparedStatement.setInt(3, bot.getVkId());
            ResultSet resultSet = preparedStatement.executeQuery();
            bot.setId(resultSet.getInt(1));
        } catch (SQLException e) {
            logger.error("Can't insert {}", bot, e);
        }
    }

    public VkMirrorBot getBotByTelegramUsername(String username) {
        logger.info("Selecting bot by telegram username {}", username);
        try(Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(SELECT_BOT_TELEGRAM)) {
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next()) {
                return constructBot(resultSet);
            }
        } catch (SQLException e) {
            logger.error("Can't select bot by telegram username {}", username, e);
        }
        return null;
    }

    public VkMirrorBot getBotByVkId(int vkId) {
        logger.info("Selecting bot by vk_id {}", vkId);
        try(Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(SELECT_BOT_VK)) {
            preparedStatement.setInt(1, vkId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next()) {
                return constructBot(resultSet);
            }
        } catch (SQLException e) {
            logger.error("Can't select bot by vk_id {}", vkId, e);
        }
        return null;
    }

    public List<VkMirrorBot> getFreeBots() {
        logger.info("Selecting free bots");
        try(Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(SELECT_BOT_VK)) {
            preparedStatement.setInt(1, VkMirrorBot.VK_NOT_ASSIGNED);
            ResultSet resultSet = preparedStatement.executeQuery();
            List<VkMirrorBot> result = new ArrayList<>();
            while(resultSet.next()) {
                result.add(constructBot(resultSet));
            }
            return result;
        } catch (SQLException e) {
            logger.error("Can't select free bots");
        }
        return new ArrayList<>();
    }

    private VkMirrorBot constructBot(ResultSet resultSet) throws SQLException {
        return new VkMirrorBot(
                resultSet.getInt("id"),
                resultSet.getString("username"),
                resultSet.getString("token"),
                resultSet.getInt("vk_id")
        );
    }
}
