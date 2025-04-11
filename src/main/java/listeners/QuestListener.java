package listeners;

import com.shadowlegend.questplugin.*;
import database.DatabaseUtils;
import discord.DiscordDM;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import quest.QuestPlugin;
import net.dv8tion.jda.api.EmbedBuilder;



import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.List;

public class QuestListener implements Listener {
    private final QuestPlugin plugin;

    private static QuestType questType;
    private static String displayTitle = "";
    private static String targetName = "";
    private static int goalAmount;
    private static long expirationTime;
    private static String reward = "";

    private static EntityType mobType;
    private static Material blockType;

    private static final Map<UUID, Integer> progress = new HashMap<>();
    private static final Map<UUID, BossBar> bossBars = new HashMap<>();
    private static final Set<UUID> completed = new HashSet<>();
    private static boolean questExpired = false;

    public QuestListener(QuestPlugin plugin) {
        this.plugin = plugin;
    }

    public static void setGlobalQuest(QuestType type, String target, int amount, long duration, String title, String rewardDescription) {
        questType = type;
        goalAmount = amount;
        displayTitle = title;
        reward = rewardDescription;
        expirationTime = System.currentTimeMillis() + duration;
        progress.clear();
        bossBars.clear();
        completed.clear();
        questExpired = false;
        targetName = target.toLowerCase();

        switch (type) {
            case KILL_MOB -> {
                try {
                    mobType = EntityType.valueOf(target.toUpperCase());
                } catch (IllegalArgumentException e) {
                    Bukkit.broadcastMessage("§cInvalid mob type.");
                    return;
                }
            }
            case BREAK_BLOCK, PLACE_BLOCK -> {
                try {
                    blockType = Material.valueOf(target.toUpperCase());
                } catch (IllegalArgumentException e) {
                    Bukkit.broadcastMessage("§cInvalid block type.");
                    return;
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= expirationTime && !questExpired) {
                    expireQuest();
                    cancel();
                }
            }
        }.runTaskTimer(QuestPlugin.getInstance(), 0L, 20L);
    }

    public static void startQuest(Player player, String questTitle) {
        if (questType == null || goalAmount <= 0 || questExpired) {
            player.sendMessage("§cThere is no active quest.");
            return;
        }

        if (completed.contains(player.getUniqueId())) {
            player.sendMessage("§eYou have already completed the quest.");
            return;
        }

        if (bossBars.containsKey(player.getUniqueId())) {
            player.sendMessage("§eYou already joined the quest.");
            return;
        }

        BossBar bar = Bukkit.createBossBar(displayTitle + ": 0/" + goalAmount + " " + targetName,
                BarColor.GREEN, BarStyle.SOLID, BarFlag.CREATE_FOG);
        bar.setProgress(0.0);
        bar.addPlayer(player);

        progress.put(player.getUniqueId(), 0);
        bossBars.put(player.getUniqueId(), bar);
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (questExpired || questType != QuestType.KILL_MOB) return;

        Player player = event.getEntity().getKiller();
        if (player == null || event.getEntity().getType() != mobType) return;
        handleProgress(player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (questExpired || questType != QuestType.BREAK_BLOCK) return;
        if (event.getBlock().getType() != blockType) return;
        handleProgress(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (questExpired || questType != QuestType.PLACE_BLOCK) return;
        if (event.getBlock().getType() != blockType) return;
        handleProgress(event.getPlayer());
    }

    private void handleProgress(Player player) {
        if (!progress.containsKey(player.getUniqueId())) return;

        UUID uuid = player.getUniqueId();
        int current = progress.get(uuid) + 1;
        progress.put(uuid, current);

        BossBar bar = bossBars.get(uuid);
        bar.setProgress(Math.min(1.0, current / (double) goalAmount));
        bar.setTitle(displayTitle + ": " + current + "/" + goalAmount + " " + targetName);

        if (current >= goalAmount) {
            completeQuest(player, bar);
        }
    }

    private void completeQuest(Player player, BossBar bar) {
        bar.setTitle("§aQuest Complete!");
        bar.setProgress(1.0);
        completed.add(player.getUniqueId());
        progress.remove(player.getUniqueId());
        bossBars.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, 100L);

        Bukkit.broadcastMessage("§6" + player.getName() + " has completed the quest: " + displayTitle);
        player.sendMessage("§bReward: §r" + reward);
        giveReward(player, reward);
        DatabaseUtils.saveQuestCompletionToDatabase(player.getUniqueId().toString(), player.getName(), displayTitle);
    }

    public void giveReward(Player player, String reward) {
        if (reward == null || reward.isEmpty()) {
            player.sendMessage("§cNo reward specified.");
            return;
        }

        String[] parts = reward.split(" ");
        int i = 0;

        while (i < parts.length) {
            try {
                int amount = Integer.parseInt(parts[i]); // First part is the amount
                if (i + 1 >= parts.length) {
                    player.sendMessage("§cExpected item name after amount: " + parts[i]);
                    break;
                }

                String itemName = parts[i + 1].toUpperCase();

                // Handle XP as special case
                if (itemName.equals("XP")) {
                    player.giveExp(amount);
                    player.sendMessage("§aYou received §e" + amount + " XP!");
                }

                // Handle custom item like "NAR" (no in-game item given)
                else if (itemName.equals("NAR")) {
                    player.sendMessage("§aYou will receive: " + amount + " NAR!");
                }

                // Handle vanilla Minecraft items
                else {
                    try {
                        Material material = Material.valueOf(itemName);
                        player.getInventory().addItem(new ItemStack(material, amount));
                        player.sendMessage("§aYou received: §e" + amount + " " + itemName);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§cUnknown item: " + itemName);
                    }
                }

                i += 2; // Move past amount and item name

            } catch (NumberFormatException e) {
                player.sendMessage("§cExpected number but got: " + parts[i]);
                break;
            }
        }
    }

    public static void expireQuest() {
        if (!completed.isEmpty()) {
            List<String> entries = DatabaseUtils.getFormattedCompletionsWithWam(displayTitle);
            StringBuilder completedPlayers = new StringBuilder("The following players completed the quest '" + displayTitle + "': ");
            completedPlayers.append(String.join(", ", entries));

            String finalMessage = completedPlayers + " and will receive " + reward;

            // Send to Minecraft
            Bukkit.broadcastMessage(finalMessage);
            // Create the embed for Discord
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Quest " + displayTitle + " Ended");
            embedBuilder.setColor(Color.RED);
            embedBuilder.setDescription(String.join(", ", entries)); // Combine quest title and message
            embedBuilder.setFooter("Reward: " + reward);
            embedBuilder.setTimestamp(Instant.now());

            // Build the embed
            MessageEmbed embed = embedBuilder.build();


            // Ensure JDA instance is available
            JDA jda = QuestPlugin.getJda();
            String discordUserId = QuestPlugin.getInstance().getConfig().getString("discord.userId");
            DiscordDM discordDM = new DiscordDM(jda);
            // Send to Discord
            discordDM.sendDM(discordUserId, embed);  // Pass the built embed here
            // CSV creation + send
            List<String> csventries = DatabaseUtils.getCSVEntriesForQuest(displayTitle);
            File csvFile = DatabaseUtils.createCSV(csventries, displayTitle);
            discordDM.sendFile(discordUserId, csvFile, "📄 CSV Results for: " + displayTitle);
        }

        // Continue with cleanup...
        Bukkit.broadcastMessage("§cThe quest '" + displayTitle + "' has expired!");
        // Remove the expired quest from the database
        DatabaseUtils.removeExpiredQuestFromDatabase(displayTitle);

        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }

        questType = null;
        targetName = "";
        displayTitle = "";
        mobType = null;
        blockType = null;
        goalAmount = 0;
        reward = "";
        questExpired = true;

        progress.clear();
        bossBars.clear();
        completed.clear();

        DatabaseUtils.clearQuestCompletions(displayTitle); // clear completions for this quest
    }
}
