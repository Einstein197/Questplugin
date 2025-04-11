package com.shadowlegend.questplugin;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.UUID;

public class QuestListener implements Listener {

    // Track kills and progress
    private static final HashMap<UUID, Integer> killCount = new HashMap<>();
    private static final HashMap<UUID, BossBar> activeBars = new HashMap<>();

    public static void startQuest(Player player, BossBar bar) {
        killCount.put(player.getUniqueId(), 0);
        activeBars.put(player.getUniqueId(), bar);
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;

        if (event.getEntity().getType() != EntityType.SPIDER) return; // Only track spiders

        UUID uuid = player.getUniqueId();
        if (!killCount.containsKey(uuid)) return;

        int current = killCount.get(uuid) + 1;
        killCount.put(uuid, current);

        BossBar bar = activeBars.get(uuid);
        bar.setProgress(current / 10.0);
        bar.setTitle("Quest Progress: " + current + "/10 spiders");

        if (current >= 10) {
            bar.setTitle("Quest Complete!");
            bar.setProgress(1.0);
            killCount.remove(uuid);
            activeBars.remove(uuid);
            Bukkit.getScheduler().runTaskLater(QuestPlugin.getPlugin(QuestPlugin.class), bar::removeAll, 100L);
        }
    }
}
