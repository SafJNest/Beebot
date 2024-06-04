package com.safjnest.core.audio;

import java.util.List;

import com.safjnest.core.audio.types.EmbedType;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;

public class QueueMessage {
    private InteractionHook hook;
    private Message message;
    
    private EmbedType type;
    
    private Guild guild;

    public QueueMessage(InteractionHook hook, EmbedType type) {
        this.hook = hook;
        this.guild = hook.getInteraction().getGuild();
        this.type = type;
    }

    public QueueMessage(Message message, EmbedType type) {
        this.message = message;
        this.guild = message.getGuild();
        this.type = type;
    }

    public void setType(EmbedType type) {
        this.type = type;
    }

    public EmbedType getType() {
        return type;
    }

    public void delete() {
        if (hook != null && hook.isExpired()) {
            hook.deleteOriginal().queue(
                null,
                error -> BotLogger.error("Error deleting message: " + error.getMessage())
            );
        } else if (message != null) {
            message.delete().queue(
                null,
                error -> BotLogger.error("Error deleting message: " + error.getMessage())
            );
        }
    }

    public void update() {
        EmbedBuilder eb = QueueHandler.getEmbed(guild, type);
        List<LayoutComponent> buttons = QueueHandler.getButtons(guild, type);

        if (hook != null) {
            hook.editOriginalEmbeds(eb.build()).setComponents(buttons).queue();
        } else if (message != null) {
            message.editMessageEmbeds(eb.build()).setComponents(buttons).queue();
        }
    }
}
