package commands;

import database.DatabaseUtils;
import listeners.QuestListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import quest.QuestPlugin;
import com.shadowlegend.questplugin.QuestType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static quest.QuestPlugin.jda;


public class SetQuestCommand {
    public static void register(QuestPlugin plugin) {
        try {
            Field f = plugin.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(plugin.getServer());
            commandMap.register("setquest", new Command("setquest") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (args.length < 6) {
                        sender.sendMessage("§cUsage: /setquest <title> <type> <amount> <target> <duration> <reward>");
                        return true;
                    }

                    // Adjust the argument parsing to reflect the new order
                    String title = args[0].replace("_", " ");
                    String typeStr = args[1].toLowerCase();
                    QuestType type;

                    switch (typeStr) {
                        case "kill" -> type = QuestType.KILL_MOB;
                        case "mine" -> type = QuestType.BREAK_BLOCK;
                        case "place" -> type = QuestType.PLACE_BLOCK;
                        default -> {
                            sender.sendMessage("§cInvalid quest type. Use 'kill', 'mine', or 'place'.");
                            return true;
                        }
                    }

                    int amount;
                    String target = args[3];
                    long durationMillis;

                    try {
                        amount = Integer.parseInt(args[2]);
                        durationMillis = parseDuration(args[4]);
                    } catch (Exception e) {
                        sender.sendMessage("§cInvalid amount or duration.");
                        return true;
                    }

                    String reward = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
                    String[] rewardParts = reward.split(" ", 2);  // Split into two parts (amount and item name)
                    String item = rewardParts.length > 1 ? rewardParts[1] : "";  // "goldcoin"

                    QuestListener.setGlobalQuest(type, target, amount, durationMillis, title, reward);
                    DatabaseUtils.saveQuestToDatabase(title, type.name(), target, amount, item, durationMillis);


                    String action = "";
                    switch (type) {
                        case PLACE_BLOCK:
                            action = "place";
                            break;
                        case KILL_MOB:
                            action = "kill";
                            break;
                        case BREAK_BLOCK:
                            action = "mine";
                            break;
                        // Add more cases if needed
                        default:
                            action = "unknown";
                            break;
                    }

                    // Now use `action` in your message
                    String message = "New Quest: " + title + ": " + action + " " + amount + " " + target + " in " + args[4] + ". Reward: " + reward;
                    String embedmessage = action + " " + amount + " " + target + " in " + args[4];
                    String channelId = QuestPlugin.getInstance().getConfig().getString("discord.channelID");
                    String roleId = QuestPlugin.getInstance().getConfig().getString("discord.roleID");
                    TextChannel channel = jda.getTextChannelById(channelId);

                    // Create the embed for Discord
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("NEW QUEST: " + title);
                    embedBuilder.setColor(Color.GREEN);
                    embedBuilder.setDescription(embedmessage); // Combine quest title and message
                    embedBuilder.setFooter("Reward: " + reward);
                    embedBuilder.setTimestamp(Instant.now());

                    // Build the embed
                    MessageEmbed embed = embedBuilder.build();

                    if (channel != null) {
                        channel.sendMessage("<@&"+roleId+">").queue();
                        channel.sendMessageEmbeds(embed).queue();
                    } else {
                        System.out.println("Channel not found: " + channelId);
                    }
                        Bukkit.broadcastMessage(message);


                    return true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static long parseDuration(String input) {
        long totalMillis = 0;
        Matcher matcher = Pattern.compile("(\\d+)([dhm])").matcher(input);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            switch (matcher.group(2)) {
                case "d" -> totalMillis += value * 86400000L;
                case "h" -> totalMillis += value * 3600000L;
                case "m" -> totalMillis += value * 60000L;
            }
        }
        return totalMillis;
    }
}


