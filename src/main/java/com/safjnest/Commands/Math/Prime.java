package com.safjnest.Commands.Math;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.safjnest.Utilities.SafJNest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class Prime extends Command {
    private int maxPrime;

    public Prime(int maxPrime){
        this.name = "prime";
        this.aliases = new String[]{"imbimbo","randomprime"};
        this.help = "Consente di generare un numero *primo* randomico a nBit.";
        this.maxPrime = maxPrime;
        this.category = new Category("Matematica");
        this.arguments = " [prime] [nbit]";
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getMessage().getContentRaw().split(" ");
        MessageChannel channel = event.getChannel();
        try {
            if (Integer.parseInt(commandArray[1]) > maxPrime)
                channel.sendMessage("Non puoi richidere un bighi prime maggiore di " + maxPrime + " bits").queue();
            else{
                String primi = SafJNest.getFirstPrime(SafJNest.randomBighi(Integer.parseInt(commandArray[1])));
                if (primi.length() > 2000) {
                    File supp = new File("bighi.txt");
                    FileWriter app;
                    try {
                        app = new FileWriter(supp);
                        app.write(primi);
                        app.flush();
                        app.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                    channel.sendFile(supp).queue();
                } else {
                    channel.sendMessage("Eccoti il tuo bighi primi a " + commandArray[1] + " bit").queue();
                    channel.sendMessage(primi).queue();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            channel.sendMessage(e.getMessage()).queue();
        }
	}
}