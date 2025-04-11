package com.shadowlegend.questplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static net.kyori.adventure.text.Component.text;

public class StartQuestCommand {
    public static void register(QuestPlugin plugin) {
        plugin.getServer().getCommandMap().register("startquest", new org.bukkit.command.Command("startquest") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can start quests.");
                    return true;
                }

                BossBar bar = Bukkit.createBossBar("Quest Progress: 0/10 spiders", BarColor.GREEN, BarStyle.SOLID);

                bar.setProgress(0.0);
                bar.addPlayer(player);

                QuestListener.startQuest(player, bar);

                return true;
            }
        });
    }
}
