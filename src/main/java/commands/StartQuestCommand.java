package commands;

import database.DatabaseUtils;
import listeners.QuestListener;
import quest.QuestPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.sql.*;

public class StartQuestCommand {

    // Register the "startquest" command
    public static void register(QuestPlugin plugin) {
        try {
            Field f = plugin.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(plugin.getServer());

            commandMap.register("startquest", new Command("startquest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        if (args.length == 0) {
                            player.sendMessage("§cYou need to specify the quest title.");
                            return false;
                        }

                        String questTitle = String.join(" ", args).trim();
                        String wam = getWAMFromDatabase(player);

                        if (wam != null) {
                            QuestListener.startQuest(player, questTitle);
                            player.sendMessage("§aYour quest '" + questTitle + "' has started!");
                            trackPlayerQuest(player, questTitle);
                        } else {
                            player.sendMessage("§cYou need to log in with your wallet to start a quest.\n/loginquest <wam>");
                        }
                    } else {
                        sender.sendMessage("§cOnly players can use this command.");
                    }
                    return true;
                }
            });

            commandMap.register("completequest", new Command("completequest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        if (args.length == 0) {
                            player.sendMessage("§cYou need to specify the quest title.");
                            return false;
                        }

                        String questTitle = args[0];

                        if (isQuestAvailable(questTitle)) {
                            completeQuest(player, questTitle);
                        } else {
                            player.sendMessage("§cThis quest does not exist.");
                        }
                    } else {
                        sender.sendMessage("§cOnly players can use this command.");
                    }
                    return true;
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getWAMFromDatabase(Player player) {
        try (Connection conn = DatabaseUtils.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT wam FROM player_wams WHERE player_name = ?");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("wam");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isQuestAvailable(String questTitle) {
        try (Connection conn = DatabaseUtils.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT title FROM quests WHERE title = ?");
            stmt.setString(1, questTitle);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void trackPlayerQuest(Player player, String questTitle) {
        try (Connection conn = DatabaseUtils.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO quest_completions (player_name, quest_title, completion_time) VALUES (?, ?, ?)"
            );
            stmt.setString(1, player.getName());
            stmt.setString(2, questTitle);
            stmt.setInt(3, 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void completeQuest(Player player, String questTitle) {
        try (Connection conn = DatabaseUtils.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE quest_completions SET completion_time = 100 WHERE player_name = ? AND quest_title = ?"
            );
            stmt.setString(1, player.getName());
            stmt.setString(2, questTitle);
            stmt.executeUpdate();
            player.sendMessage("§aCongratulations! You have completed the quest: " + questTitle);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
