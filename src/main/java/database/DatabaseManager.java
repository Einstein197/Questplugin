package database;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class DatabaseManager {

    private static Connection connection;

    // Connect to the SQLite database
    public static void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                // Create the database file if it doesn't exist
                File dbFile = new File("plugins/QuestPlugin/quests.db");
                if (!dbFile.exists()) {
                    dbFile.getParentFile().mkdirs();
                    dbFile.createNewFile();
                }

                // Open the database connection
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    // Close the database connection
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
