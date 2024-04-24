package com.safjnest.Utilities.Audio;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;

public class QueueMessage {
    private InteractionHook hook;
    private Message message;
    
    private Guild guild;

    public QueueMessage(InteractionHook hook) {
        this.hook = hook;
        this.guild = hook.getInteraction().getGuild();
    }

    public QueueMessage(Message message) {
        this.message = message;
        this.guild = message.getGuild();
    }

    public void delete() {
        if (hook != null) {
            hook.deleteOriginal().queue();
        } else if (message != null) {
            message.delete().queue();
        }
    }

    public void update() {
        EmbedBuilder eb = QueueHandler.getQueueEmbed(guild);
        List<LayoutComponent> buttons = QueueHandler.getQueueButtons(guild);

        if (hook != null) {
            hook.editOriginalEmbeds(eb.build()).setComponents(buttons).queue();
        } else if (message != null) {
            message.editMessageEmbeds(eb.build()).setComponents(buttons).queue();
        }
    }
}
