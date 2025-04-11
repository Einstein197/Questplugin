package commands;

import quest.QuestPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Field;
import java.sql.*;

public class QuestCommandExecutor {

    private static Connection connection;

    // Register the command manually in the command map
    public static void register(QuestPlugin plugin) {
        try {
            Field f = plugin.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(plugin.getServer());

            // Register the "loginquest" command manually
            commandMap.register("loginquest", new Command("loginquest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) {
                        if (args.length == 1) {
                            String wam = args[0];

                            // Check length limit
                            if (wam.length() > 12) {
                                player.sendMessage("Your WAM must be 12 characters or fewer.");
                                return true;
                            }

                            storeWAMInDatabase(player, wam);
                            player.sendMessage("Your WAM has been saved: " + wam);
                        } else {
                            player.sendMessage("Usage: /loginquest <wam>");
                        }
                    } else {
                        sender.sendMessage("Only players can use this command.");
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to create or get the SQLite connection
    public static void connectToDatabase() {
        try {
            // Create the database if it doesn't exist
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

    // Store the player's WAM in the SQLite database
    public static void storeWAMInDatabase(Player player, String wam) {
        try {
            // Ensure database connection is established
            if (connection == null) {
                connectToDatabase();
            }

            // Insert the WAM into the database
            PreparedStatement stmt = connection.prepareStatement("INSERT OR REPLACE INTO player_wams (player_name, wam) VALUES (?, ?)");
            stmt.setString(1, player.getName());
            stmt.setString(2, wam);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Retrieve the player's WAM from the database (if needed)
    public static String getWAMFromDatabase(Player player) {
        try {
            if (connection == null) {
                connectToDatabase();
            }

            // Get the WAM from the database
            PreparedStatement stmt = connection.prepareStatement("SELECT wam FROM player_wams WHERE player_name = ?");
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
}
