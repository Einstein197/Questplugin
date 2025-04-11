package discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.File;

public class DiscordDM {
    private final JDA jda;

    public DiscordDM(JDA jda) {
        this.jda = jda;
    }

    // Modify to accept a MessageEmbed instead of String
    public void sendDM(String userId, MessageEmbed embed) {
        jda.retrieveUserById(userId).queue(
                user -> {
                    if (user != null) {
                        user.openPrivateChannel().queue(channel -> {
                            channel.sendMessageEmbeds(embed).queue();  // Send the embed message
                        });
                    } else {
                        System.out.println("User not found: " + userId);
                    }
                },
                throwable -> {
                    System.out.println("Error retrieving user: " + throwable.getMessage());
                }
        );
    }
    public void sendFile(String userId, File file, String message) {
        jda.retrieveUserById(userId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message)
                        .addFiles(FileUpload.fromData(file))
                        .queue();
            });
        });
    }

}
