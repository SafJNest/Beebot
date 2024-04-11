package com.safjnest.Utilities.Guild.CustomCommand;

import java.util.ArrayList;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class Task {
    private final int ID;
    private TaskType type;
    private ArrayList<String> values;

    public Task(int ID, TaskType type) {
        this.ID = ID;
        this.type = type;
        this.values = new ArrayList<>();
    }

    public void addValue(String value) {
        values.add(value);
    }

    public String toString() {
        return "Task{" +
                "ID=" + ID +
                ", type=" + type +
                ", values=" + values +
                '}';
    }

    public void execute(CustomCommand command, SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        switch (type) {
            case SEND_MESSAGE:
                event.reply(values.get(0)).queue();
                break;
            case DELETE_CHANNEL:
                for (String value : values) {
                    if (value.startsWith("#")) {
                        command.getOptions().forEach(option -> {
                            if (option.getId().equals(value.substring(1))) {
                                event.getOption(option.getKey()).getAsChannel().delete().queue();
                            }
                        });
                    } else {
                        guild.getGuildChannelById(value).delete().queue();
                    }
                }
                break;
        
            default:
                break;
        }
    }
}
