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
        this.aliases = new String[]{"dado", "lanciadado", "roll", "dice"};
        this.help = "Il bot lancia uno o più dadi.";
        this.category = new Category("Matematica");
        this.arguments = "[dice] (n dadi) (n n facce)";
    }

	@Override
	protected void execute(CommandEvent event) {
        int ndice = 1, nface = 6, sum = 0;
        if(!event.getArgs().equals("")){
            ndice = Integer.parseInt(event.getArgs().split(" ")[0]);
            if(event.getArgs().split(" ").length > 1 && !event.getArgs().split(" ")[1].equals(""))
                nface = Integer.parseInt(event.getArgs().split(" ")[1]);
        }
        for(int i = 0; i < ndice; i++)
            sum+=(int)(Math.random() * nface) + 1;
        
        event.reply((ndice == 1)
                    ? "Lanciato un dado con " + nface + " facce: " + sum        
                    : "Lanciati " + ndice +  " dadi con " + nface + " facce: " + sum);
	}
}