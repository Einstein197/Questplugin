package commands;

import listeners.QuestListener;
import quest.QuestPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.lang.reflect.Field;

public class StartQuestCommand {

    private static Connection connection;

    // Register the "startquest" command
    public static void register(QuestPlugin plugin) {
        try {
            Field f = plugin.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(plugin.getServer());

            // Register the "startquest" command
            commandMap.register("startquest", new Command("startquest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        if (args.length == 0) {
                            player.sendMessage("§cYou need to specify the quest title.");
                            return false;
                        }

                        // Join the title in case it has multiple words
                        String questTitle = String.join(" ", args).trim();

                        // Check if the player has a WAM stored in the database
                        String wam = getWAMFromDatabase(player);

                        if (wam != null) {
                            // Player has a WAM, start the quest regardless of DB
                            QuestListener.startQuest(player, questTitle);
                            player.sendMessage("§aYour quest '" + questTitle + "' has started!");
                            trackPlayerQuest(player, questTitle);
                        } else {
                            // Player does not have a WAM
                            player.sendMessage("§cYou need to log in with your wallet to start a quest." +
                                    "\n/loginquest <wam>");
                        }
                    } else {
                        sender.sendMessage("§cOnly players can use this command.");
                    }
                    return true;
                }
            });


            // Register a new command for completing quests
            commandMap.register("completequest", new Command("completequest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        if (args.length == 0) {
                            player.sendMessage("§cYou need to specify the quest title.");
                            return false;
                        }

                        String questTitle = args[0];

                        // Check if the quest exists and complete it
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

    // Connect to the SQLite database
    private static void connectToDatabase() {
        try {
            // Create the database file if it doesn't exist
            File dbFile = new File("plugins/QuestPlugin/quests.db");
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }

            // Open the database connection
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    // Retrieve the player's WAM from the database
    private static String getWAMFromDatabase(Player player) {
        try {
            if (connection == null) {
                connectToDatabase();
            }

            // Query to get the WAM for the player
            PreparedStatement stmt = connection.prepareStatement("SELECT wam FROM player_wams WHERE player_name = ?");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("wam");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Return null if no WAM found
    }

    // Check if the quest exists in the database
    private static boolean isQuestAvailable(String questTitle) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT title FROM quests WHERE title = ?");
            stmt.setString(1, questTitle);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Return true if the quest exists
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Return false if the quest doesn't exist
    }

    // Track player progress in the quest
    private static void trackPlayerQuest(Player player, String questTitle) {
        try {
            // Insert the player's quest progress into the database
            PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO quest_completions (player_name, quest_title, completion_time) VALUES (?, ?, ?)");
            stmt.setString(1, player.getName());
            stmt.setString(2, questTitle);
            stmt.setInt(3, 0); // Starting progress for the quest (e.g., 0)
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Complete a quest for a player
    private static void completeQuest(Player player, String questTitle) {
        try {
            // Update the player's quest progress to complete (e.g., 100% progress)
            PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE m SET completion_at = 100 WHERE player_uuid = ? AND quest_title = ?");
            stmt.setString(1, player.getName());
            stmt.setString(2, questTitle);
            stmt.executeUpdate();

            player.sendMessage("§aCongratulations! You have completed the quest: " + questTitle);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
