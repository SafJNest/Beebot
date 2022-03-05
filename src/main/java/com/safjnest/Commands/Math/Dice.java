package com.safjnest.Commands.Math;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

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
        for(int i = 0; i < ndice; i++) 
            msg+=dice[(int)(Math.random() * 5)]+" ";
        
        event.reply(msg);
	}
}