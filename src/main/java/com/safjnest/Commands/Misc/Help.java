package com.safjnest.Commands.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.01
 */
public class Help extends Command {

    public Help() {
        this.name = "help";
        this.aliases = new String[]{"command", "commands", "info"};
        this.help = "Consente di visualizzare l'elenco di tutti i comandi.\n"
        + "Se viene usata la sintassi [help][nomeComando] si otterrà una descrizione specifica del comando richiesto.";
        this.category = new Category("Misc");
        this.arguments = "[help](nome comando)";
    }

    @Override
    protected void execute(CommandEvent event) {
        String command = event.getArgs();
        EmbedBuilder eb = new EmbedBuilder();
        HashMap<String, ArrayList<Command>> commands = new HashMap<>();

        for (Command e : event.getClient().getCommands()) {
            if(!commands.containsKey(e.getCategory().getName()))
                commands.put(e.getCategory().getName(), new ArrayList<Command>());
            commands.get(e.getCategory().getName()).add(e);
        }
        eb.setTitle("📒INFORMAZIONI E COMANDI📒", null);
        eb.setDescription("Il prefisso corrente è: **"+event.getClient().getPrefix()+"**"
        + ", puoi avere maggiori informazioni tramite il comando: ```"+event.getClient().getPrefix()+"<nomecomando>.");
        eb.setColor(new Color(255, 196, 0));
        if(command.equals("")){
            /*
            for (Command e : event.getClient().getCommands()) {
                eb.addField(e.getName(), e.getHelp(), true);
                
            }
            */
            String ss = "```\n";
            for(String k : commands.keySet()){
                for(Command c : commands.get(k)){
                    ss+= c.getName() + "\n";
                }
                ss+="```";
                eb.addField(k, ss, true);
                ss = "```\n";
            }
            eb.setFooter("Il bot è sempre in costante aggiornamento ;D", null);
        }else{
            Command e = null;
            for(String k : commands.keySet()){
                for(Command c : commands.get(k)){
                   if(c.getName().equalsIgnoreCase(command) || Arrays.asList(c.getAliases()).contains(command) ){
                       e = c;
                       break;
                   }
                }
            }
            eb.setDescription("**COMANDO " + e.getName().toUpperCase() + "**");
            eb.addField("**DESCRIZIONE**","```"+e.getHelp()+"```", false);
            eb.addField("**CATEGORIA**","```"+e.getCategory().getName()+"```", false);
            eb.addField("**ARG**","```"+e.getArguments()+"```", true);
            eb.addField("**COOLDOWN**","```"+e.getCooldown()+"```", true);
            if(e.getAliases().length > 0){
                String aliases = "";
                for(String a : e.getAliases())
                    aliases+=a+" - ";
                eb.addField("**ALIASES**","```"+aliases+"```", false);
            }else{
                eb.addField("**ALIASES**","```"+"NULL"+"```", false);
            }

            
            eb.setFooter("SE L'ARGS E' NULL BASTA DIGITARE IL COMANDO, LE QUADRE INDICANO CAMPO OBBLIGATORIO E LE TONDE FACOLTATIVO", null);
        }
        eb.addField("**ALTRE INFORMAZIONI**", "Il bot è stato sviluppato da sole due persone quindi in caso di bug o problemi non esitate a contattarli.", false);
        for(String id : PermissionHandler.getUntouchables()){
            User guy = event.getJDA().getUserById(id);
            eb.addField("**"+guy.getName()+"**","```"+guy.getId()+"```", false);
        }
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",
                event.getJDA().getSelfUser().getAvatarUrl());

        event.getChannel().sendMessageEmbeds(eb.build())
                .queue();

    }

}
