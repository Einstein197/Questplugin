package database;

import com.shadowlegend.questplugin.QuestType;
import quest.QuestData;
import quest.QuestPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseUtils {

    private static final String DB_URL = "jdbc:sqlite:plugins/QuestPlugin/quests.db?journal_mode=wal";
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // ✅ Initializes the quests and completions tables
    public static void initializeDatabase() {
        String sql1 = """
            CREATE TABLE IF NOT EXISTS quests (
                title TEXT PRIMARY KEY,
                type TEXT,
                target TEXT,
                amount INTEGER,
                reward TEXT,
                duration INTEGER
            );
        """;

        String sql2 = """
            CREATE TABLE IF NOT EXISTS quest_completions (
                player_name TEXT,
                quest_title TEXT,
                completion_time INTEGER,
                PRIMARY KEY (player_name, quest_title)  -- Changed to player_name and quest_title
            );
        """;

        String sql3 = """
            CREATE TABLE IF NOT EXISTS player_wams (
                player_name TEXT PRIMARY KEY,
                wam TEXT NOT NULL
            );
        """;

        runAsync(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql1);
                stmt.execute(sql2);
                stmt.execute(sql3);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ✅ Run DB tasks asynchronously
    public static void runAsync(Runnable task) {
        dbExecutor.submit(task);
    }

    // ✅ Saves quest data to the database
    public static synchronized void saveQuestToDatabase(String title, String type, String target, int amount, String reward, long durationMillis) {
        long expirationTime = System.currentTimeMillis() + durationMillis;

        runAsync(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                String sql = """
                INSERT OR REPLACE INTO quests (title, type, target, amount, reward, duration)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, title);
                    stmt.setString(2, type);
                    stmt.setString(3, target);
                    stmt.setInt(4, amount);
                    stmt.setString(5, reward);
                    stmt.setLong(6, expirationTime);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ✅ Save quest completion data to the database asynchronously
    public static void saveQuestCompletionToDatabase(String playerUUID, String playerName, String questTitle) {
        runAsync(() -> {
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                connection.setAutoCommit(false);  // Start transaction

                // Try to update the completion record if it exists
                String updateQuery = "UPDATE quest_completions SET completion_time = ? WHERE player_name = ? AND quest_title = ?";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                    updateStmt.setLong(1, System.currentTimeMillis());  // Set current time as completion time
                    updateStmt.setString(2, playerName);  // Player's name
                    updateStmt.setString(3, questTitle);  // Quest title
                    int rowsUpdated = updateStmt.executeUpdate();

                    // If no rows were updated (i.e., no existing record), insert a new one
                    if (rowsUpdated == 0) {
                        insertQuestCompletion(playerUUID, playerName, questTitle, connection);
                    }

                    connection.commit();  // Commit the transaction
                } catch (SQLException e) {
                    connection.rollback();  // Rollback in case of error
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Helper method to insert a new completion record if needed
    private static void insertQuestCompletion(String playerUUID, String playerName, String questTitle, Connection connection) {
        try {
            String query = "INSERT INTO quest_completions (player_name, quest_title, completion_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerName);  // Player's name
                statement.setString(2, questTitle);  // Quest title
                statement.setLong(3, System.currentTimeMillis());  // Set completion time to current time
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ✅ Gets formatted quest completions with WAM values
    public static List<String> getFormattedCompletionsWithWam(String questTitle) {
        List<String> results = new ArrayList<>();
        String query = """
            SELECT qc.player_name, pw.wam 
            FROM quest_completions qc
            JOIN player_wams pw ON qc.player_name = pw.player_name
            WHERE qc.quest_title = ?
            """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, questTitle);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    String wam = rs.getString("wam");
                    results.add(name + " (WAM: " + wam + ")");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    // ✅ Clears old quest completions for a quest asynchronously
    public static synchronized void clearQuestCompletions(String questTitle) {
        runAsync(() -> {
            String sql = "DELETE FROM quest_completions WHERE quest_title = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, questTitle);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ✅ Removes expired quest from the database
    public static synchronized void removeExpiredQuestFromDatabase(String questTitle) {
        runAsync(() -> {
            String sql1 = "DELETE FROM quests WHERE title = ?";
            String sql2 = "DELETE FROM quest_completions WHERE quest_title = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                // Delete from the quests table
                try (PreparedStatement pstmt1 = conn.prepareStatement(sql1)) {
                    pstmt1.setString(1, questTitle);
                    pstmt1.executeUpdate();
                }

                // Delete from the quest_completions table
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, questTitle);
                    pstmt2.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    public static List<String> getCSVEntriesForQuest(String questTitle) {
        List<String> entries = new ArrayList<>();

        // Load contract and memo from the config
        String contract = QuestPlugin.getInstance().getConfig().getString("quest.contract", "default_contract");
        String memo = QuestPlugin.getInstance().getConfig().getString("quest.memo", "default_memo");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:plugins/QuestPlugin/quests.db")) {
            String query = "SELECT pw.wam AS \"to\", q.amount, q.reward AS token " +
                    "FROM quest_completions qc " +
                    "JOIN player_wams pw ON qc.player_name = pw.player_name " +
                    "JOIN quests q ON qc.quest_title = q.title " +
                    "WHERE qc.quest_title = ?";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, questTitle);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String to = rs.getString("to");
                        String amount = rs.getString("amount");
                        String token = rs.getString("token");

                        // Use the loaded contract and memo values
                        entries.add(String.join(",", to, amount, token, contract, memo));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return entries;
    }




    // Inside your Quest class (or wherever `expireQuest()` is)
    public static File createCSV(List<String> entries, String displayTitle) {
        File csvFile = new File("quest_" + displayTitle.replaceAll("\\s+", "_") + ".csv");
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("to,amount,token,contract,memo"); // Header row

            for (String entry : entries) {
                writer.println(entry);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return csvFile;
    }




    // ✅ Properly shut down the executor when the plugin is disabled
    public static void shutdown() {
        dbExecutor.shutdown();
    }

}
