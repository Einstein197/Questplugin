package quest;

import database.DatabaseManager;
import database.DatabaseUtils;
import commands.QuestCommandExecutor;
import commands.SetQuestCommand;
import commands.StartQuestCommand;
import listeners.QuestListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDA;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class QuestPlugin extends JavaPlugin {
    private static QuestPlugin instance;
    public static JDA jda;
    private static String discordUserId;
    private static String botToken;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("QuestPlugin enabled!");
        DatabaseManager.connect();
        DatabaseUtils.initializeDatabase();

        // Ensure the config file exists and is loaded
        saveDefaultConfig();  // This creates the config.yml if it doesn't exist

        // Load config settings
        FileConfiguration config = getConfig();

        // Load bot token and user ID from the config
        botToken = config.getString("discord.botToken");
        discordUserId = config.getString("discord.userId");

        if (botToken != null && !botToken.isEmpty()) {
            // Asynchronously initialize the JDA bot to avoid blocking the main server thread
            getServer().getScheduler().runTask(this, () -> {
                try {
                    // Initialize JDA with the bot token from config
                    jda = JDABuilder.createDefault(botToken).build();
                    jda.awaitReady(); // Wait until JDA is ready
                    getLogger().info("JDA bot initialized successfully!");
                } catch (Exception e) {
                    getLogger().severe("Error initializing JDA: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            getLogger().warning("No bot token found in the config!");
        }

        // Register commands and listeners
        DatabaseUtils.initializeDatabase();
        commands.StartQuestCommand.register(this);
        commands.SetQuestCommand.register(this);
        QuestCommandExecutor.register(this);
        getServer().getPluginManager().registerEvents(new listeners.QuestListener(this), this);
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
            DatabaseUtils.shutdown();
        }
        DatabaseManager.close();
        getLogger().info("QuestPlugin disabled.");
    }

    public static QuestPlugin getInstance() {
        return instance;
    }

    public static JDA getJda() {
        return jda;
    }

    public static String getDiscordUserId() {
        return discordUserId;
    }

    public static void setDiscordUserId(String userId) {
        discordUserId = userId;
        // Save to config
        instance.getConfig().set("discord.userId", userId);  // Use instance reference here
        instance.saveConfig();  // Save the updated config
    }

    public static String getBotToken() {
        return botToken;
    }

    public static void setBotToken(String token) {
        botToken = token;
        // Save to config
        instance.getConfig().set("discord.botToken", token);  // Use instance reference here
        instance.saveConfig();  // Save the updated config
    }
}
