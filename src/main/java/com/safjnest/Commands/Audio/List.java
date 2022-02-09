package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SoundBoard;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

public class List extends Command {

    public List(){
        this.name = "list";
        this.aliases = new String[]{"listoide", "listina","lista"};
        this.help = "Il bot invia la lista di tutti i suoni locali";
    }

	@Override
	protected void execute(CommandEvent event) {
        MessageChannel channel = event.getChannel();
		String[] names = SoundBoard.getAllNamesNoExc();
        Arrays.sort(names);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("SoundBoard");
        String soundNames = "";
        for(String name : names){
            soundNames += name +  " - ";
        }
        eb.setDescription("Lista con tutti i suoni del tier 1 bot\n **" + soundNames + "**");
        eb.setColor(new Color(0, 128, 128));
        eb.setAuthor(event.getSelfUser().getName(), "https://github.com/SafJNest",event.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rythem, questa e' perfezione cit. steve jobs", null);
        File file = new File("img" + File.separator + "mp3.png");
        eb.setThumbnail("attachment://mp3.png");
        channel.sendMessageEmbeds(eb.build())
                    .addFile(file, "mp3.png")
                    .queue();
	}
}
