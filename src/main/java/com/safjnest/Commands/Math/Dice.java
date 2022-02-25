package com.safjnest.Commands.Math;

import java.util.List;
import com.safjnest.Utilities.PermissionHandler;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Dice extends Command {

    public Dice(){
        this.name = "dice";
        this.aliases = new String[]{"dado", "lanciadado", "roll"};
        this.help = "Il bot lancia uno o più dadi.";
        this.category = new Category("Matematica");
        this.arguments = "[dice] (n dadi)";
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] dice = new String[]{"1️⃣","2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣"};
        String msg = "";
        int ndice = (event.getArgs().equals("")) ? 1 : Integer.parseInt(event.getArgs());
        int sum = 0;
        for(int i = 0; i < ndice; i++){
            int rand = (int)(Math.random() * 5);
            sum+=rand+1;
            msg+=dice[rand]+" ";
        }
        
        event.reply((ndice < 3)?msg:String.valueOf(sum));
	}
}