package com.safjnest.Commands.Math;

import java.io.File;
import java.io.FileWriter;

import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SafJNest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * The command create a random number with n-bits.
 * <p>If the number is longer than 2000 characters, it will be sent as file.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since Alpha
 */
public class Bighi extends Command {
    /**The max number of bits */
    private int maxBighi;
    
    /**
     * Constructor
     * @param maxBighi
     */
    public Bighi(int maxBighi){
        this.maxBighi = maxBighi;
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
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
                File supp = new File("rsc"+File.separator + "bighi.txt");
                FileWriter app;
                app = new FileWriter(supp);
                app.write(bighi);
                app.flush();
                app.close();
                channel.sendMessage("Il bighi era troppo insano per Discord, eccoti un bel file.").queue();
                channel.sendFiles(FileUpload.fromData(supp)).queue();
            } else {
                channel.sendMessage("Eccoti il tuo bighi a " + commandArray[1] + " bit").queue();
                channel.sendMessage(bighi).queue();
            }
        } catch (Exception e) {
            channel.sendMessage(e.getMessage()).queue();
        }
	}
}
