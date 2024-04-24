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
        if (hook != null) {
            hook.deleteOriginal().queue();
        } else if (message != null) {
            message.delete().queue();
        }
    }

    public void update() {
        EmbedBuilder eb = QueueHandler.getEmbed(guild);
        List<LayoutComponent> buttons = QueueHandler.getButtons(guild);

        if (hook != null) {
            hook.editOriginalEmbeds(eb.build()).setComponents(buttons).queue();
        } else if (message != null) {
            message.editMessageEmbeds(eb.build()).setComponents(buttons).queue();
        }
    }
}
