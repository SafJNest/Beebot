package com.safjnest.Commands;

import java.io.File;
import java.io.FileWriter;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;

import net.dv8tion.jda.api.entities.MessageChannel;

public class Bighi extends Command {
    private int maxBighi;
    
    public Bighi(int maxBighi){
        this.name = "bighi";
        this.aliases = new String[]{"random",};
        this.help = "il bot ti outplaya con un numero random a tot bit";
        this.maxBighi = maxBighi;
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        MessageChannel channel = event.getChannel();
        try {
            if (Integer.parseInt(commandArray[1]) > maxBighi) {
                channel.sendMessage("Non puoi richidere un bighi maggiore di " + maxBighi + " bits").queue();
                return;
            }
            String bighi = String.valueOf(SafJNest.randomBighi(Integer.parseInt(commandArray[1])));
            if (bighi.length() > 2000) {
                File supp = new File("bighi.txt");
                FileWriter app;
                app = new FileWriter(supp);
                app.write(bighi);
                app.flush();
                app.close();
                channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                channel.sendFile(supp).queue();
            } else {
                channel.sendMessage("Eccoti il tuo bighi primo a " + commandArray[1] + " bit").queue();
                channel.sendMessage(bighi).queue();
            }
        } catch (Exception e) {
            channel.sendMessage(e.getMessage()).queue();
        }
	}
    


}
