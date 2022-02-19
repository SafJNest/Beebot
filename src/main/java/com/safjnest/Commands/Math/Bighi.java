package com.safjnest.Commands.Math;

import java.io.File;
import java.io.FileWriter;

import com.safjnest.Utilities.SafJNest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since Alpha
 */
public class Bighi extends Command {
    private int maxBighi;
    
    public Bighi(int maxBighi){
        this.name = "bighi";
        this.aliases = new String[]{"random"};
        this.help ="Consente di generare un numero randomico a nBit.";
        this.maxBighi = maxBighi;
        this.category = new Category("Matematica");
        this.arguments = "[bighi] [numero]";
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
                channel.sendMessage("Eccoti il tuo bighi a " + commandArray[1] + " bit").queue();
                channel.sendMessage(bighi).queue();
            }
        } catch (Exception e) {
            channel.sendMessage(e.getMessage()).queue();
        }
	}
}
