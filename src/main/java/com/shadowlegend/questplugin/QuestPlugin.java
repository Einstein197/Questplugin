package com.shadowlegend.questplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class QuestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("QuestPlugin enabled!");
        StartQuestCommand.register(this);
        getServer().getPluginManager().registerEvents(new QuestListener(), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("QuestPlugin disabled.");
    }
}
