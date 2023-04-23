package com.safjnest.Utilities.Listeners;

import java.awt.Color;
import java.util.ArrayList;

import com.safjnest.Commands.LOL.Summoner;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.LOL.LOLHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * This class handles all events that could occur during the listening:
 * <ul>
 * <li>On update of a voice channel (to make the bot leave an empty voice
 * channel)</li>
 * <li>On join of a user (to make the bot welcome the new member)</li>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class TheListener extends ListenerAdapter {
    private SQL sql;

    public TheListener(SQL sql) {
        this.sql = sql;
    }

    /**
     * On update of a voice channel (to make the bot leave an empty voice channel)
     */
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e) {
        if((e.getGuild().getAudioManager().isConnected() && e.getChannelLeft() != null) &&
            (e.getGuild().getAudioManager().getConnectedChannel().getId().equals(e.getChannelLeft().getId())) &&
            (e.getChannelLeft().getMembers().size() == 1)){
            e.getGuild().getAudioManager().closeAudioConnection();
        }
    }

    /**
     * On join of a user (to make the bot welcome the new member)
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        MessageChannel channel = null;
        User newGuy = event.getUser();
        String query = "SELECT channel_id FROM welcome_message WHERE discord_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String notNullPls = sql.getString(query, "channel_id");
        if (notNullPls == null)
            return;
        channel = event.getGuild().getTextChannelById(notNullPls);
        query = "SELECT message_text FROM welcome_message WHERE discord_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String message = sql.getString(query, "message_text");
        message = message.replace("#user", newGuy.getAsMention());
        channel.sendMessage(message).queue();
        query = "SELECT role_id FROM welcome_roles WHERE discord_id = '" + event.getGuild().getId() + "' AND bot_id = '"
                + event.getJDA().getSelfUser().getId() + "';";
        ArrayList<String> roles = sql.getAllRowsSpecifiedColumn(query, "role_id");
        if (roles.size() > 0) {
            for (String role : roles) {
                event.getGuild().addRoleToMember(newGuy, event.getGuild().getRoleById(role)).queue();
            }
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event){
        MessageChannel channel = null;
        String query = "SELECT channel_id FROM left_message WHERE discord_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String notNullPls = sql.getString(query, "channel_id");
        if (notNullPls == null)
            return;
        channel = event.getGuild().getTextChannelById(notNullPls);
        query = "SELECT message_text FROM left_message WHERE discord_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String message = sql.getString(query, "message_text");
        message = message.replace("#user", event.getUser().getAsMention());
        channel.sendMessage(message).queue();
    }

    /**
     * On update of a user's boost time (to make the bot praise the user)
     */
    public void onGuildMemberUpdateBoostTime​(GuildMemberUpdateBoostTimeEvent event) {
        User newguy = event.getUser();
        TextChannel welcome = event.getGuild().getSystemChannel();
        welcome.sendMessage("NO FUCKING WAY " + newguy.getAsMention() + " HA BOOSTATO IL SERVER!!\n"
                + event.getGuild().getBoostCount()).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        if (event.getButton().getId().startsWith("lol-")) {
            event.deferEdit().queue();

            String args = event.getButton().getId().substring(event.getButton().getId().indexOf("-") + 1);
            Button left = Button.primary("lol-left", "<-");
            Button right = Button.primary("lol-right", "->");
            Button center = null;
            String nameSum = "";
            int index = 0;

            for (Button b : event.getMessage().getButtons()) {
                if (!b.getLabel().equals("->") && !b.getLabel().equals("<-"))
                    nameSum = b.getLabel();
            }
            String query = "SELECT discord_id FROM lol_user WHERE account_id = '" + LOLHandler.getAccountIdByName(nameSum) + "';";
            query = "SELECT summoner_id FROM lol_user WHERE discord_id = '" + DatabaseHandler.getSql().getString(query, "discord_id") + "';";
            ArrayList<ArrayList<String>> accounts = DatabaseHandler.getSql().getAllRows(query, 1);
            switch (args) {

                case "right":

                    for (int i = 0; i < accounts.size(); i++) {
                        if (LOLHandler.getSummonerById(accounts.get(i).get(0)).getName().equals(nameSum))
                            index = i;
                    }

                    if ((index + 1) == accounts.size())
                        index = 0;
                    else
                        index += 1;

                    center = Button.primary("lol-center",
                            LOLHandler.getSummonerById(accounts.get(index).get(0)).getName());
                    event.getMessage()
                            .editMessageEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(),
                                    LOLHandler.getSummonerById(accounts.get(index).get(0))).build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;

                case "left":

                    for (int i = 0; i < accounts.size(); i++) {
                        if (LOLHandler.getSummonerById(accounts.get(i).get(0)).getName().equals(nameSum))
                            index = i;

                    }

                    if (index == 0)
                        index = accounts.size() - 1;
                    else
                        index -= 1;

                    center = Button.primary("lol-center",
                            LOLHandler.getSummonerById(accounts.get(index).get(0)).getName());
                    event.getMessage()
                            .editMessageEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(),
                                    LOLHandler.getSummonerById(accounts.get(index).get(0))).build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;
            }
        } else if (event.getButton().getId().startsWith("list-")) {
            event.deferEdit().queue();

            String args = event.getButton().getId().substring(event.getButton().getId().indexOf("-") + 1);

            int page = 1;
            int cont = 0;
            String query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE guild_id = '"
                    + event.getGuild().getId() + "' ORDER BY name ASC;";
            ArrayList<ArrayList<String>> sounds = DatabaseHandler.getSql().getAllRows(query, 2);

            EmbedBuilder eb = new EmbedBuilder();
            eb.setAuthor(event.getUser().getName(), "https://github.com/SafJNest",
                    event.getUser().getAvatarUrl());
            eb.setTitle("List of " + event.getGuild().getName());
            eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
            eb.setColor(Color.decode(
                BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
            ));
            eb.setDescription("Total Sound: " + sounds.size());
            Button left = Button.primary("list-left", "<-");
            Button right = Button.primary("list-right", "->");
            Button center = null;

            switch (args) {

                case "right":
                    for (Button b : event.getMessage().getButtons()) {
                        if (b.getLabel().startsWith("Page"))
                            page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                    }
                    
                    cont = 24 * page;
                    while(cont < (24*(page+1)) && cont < sounds.size()){
                        eb.addField("**"+sounds.get(cont).get(1)+"**", "ID: " + sounds.get(cont).get(0), true);
                        cont++;
                    }
                    
                    if (24 * (page + 1) >= sounds.size()){
                        right = right.asDisabled();
                        right = right.withStyle(ButtonStyle.DANGER);
                    }
                    center = Button.primary("center", "Page: " + (page + 1));
                    center = center.withStyle(ButtonStyle.SUCCESS);
                    center = center.asDisabled();
                    event.getMessage().editMessageEmbeds(eb.build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;

                case "left":
                    
                    for (Button b : event.getMessage().getButtons()) {
                        if (b.getLabel().startsWith("Page")) 
                            page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                    }
                    cont = (24 * (page - 2) < 0) ? 0 : 24 * (page - 2);
                
                    while(cont < (24*(page-1)) && cont < sounds.size()){
                        eb.addField("**"+sounds.get(cont).get(1)+"**", "ID: " + sounds.get(cont).get(0), true);
                        cont++;
                    }


                    
                    if ((page - 1) == 1){
                        left = left.asDisabled();
                        left = left.withStyle(ButtonStyle.DANGER);
                    }
                    
                    center = Button.primary("center", "Page: " + (page - 1));
                    center = center.withStyle(ButtonStyle.SUCCESS);
                    center = center.asDisabled();
                    event.getMessage().editMessageEmbeds(eb.build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;
            }
        }else if(event.getButton().getId().startsWith("listuser-")){
            event.deferEdit().queue();
            String args = event.getButton().getId().substring(event.getButton().getId().indexOf("-") + 1);

            int page = 1;
            int cont = 0;
            String userId = "";

            for (Button b : event.getMessage().getButtons()) {
                if (b.getLabel().startsWith("Page")){
                    page = Integer.valueOf(String.valueOf(b.getLabel().charAt(b.getLabel().indexOf(":") + 2)));
                    userId = b.getId().split("-")[2];
                }
            }
            
            String query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE user_id = '"
                    + userId + "' ORDER BY name ASC;";
            ArrayList<ArrayList<String>> sounds = DatabaseHandler.getSql().getAllRows(query, 2);

            EmbedBuilder eb = new EmbedBuilder();
            eb.setAuthor(event.getUser().getName(), "https://github.com/SafJNest",
                    event.getUser().getAvatarUrl());
            eb.setTitle("List of " + event.getJDA().getUserById(userId).getName());
            eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
            eb.setColor(Color.decode(
                BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
            ));
            eb.setDescription("Total Sound: " + sounds.size());
            Button left = Button.primary("listuser-left", "<-");
            Button right = Button.primary("listuser-right", "->");
            Button center = null;

            switch (args) {

                case "right":
                    cont = 24 * page;
                    while(cont < (24*(page+1)) && cont < sounds.size()){
                        eb.addField("**"+sounds.get(cont).get(1)+"**", "ID: " + sounds.get(cont).get(0), true);
                        cont++;
                    }
                    
                    if (24 * (page + 1) >= sounds.size()){
                        right = right.asDisabled();
                        right = right.withStyle(ButtonStyle.DANGER);
                    }
                    center = Button.primary("listuser-center-" + userId, "Page: " + (page + 1));
                    center = center.withStyle(ButtonStyle.SUCCESS);
                    center = center.asDisabled();
                    event.getMessage().editMessageEmbeds(eb.build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;

                case "left":
                    cont = (24 * (page - 2) < 0) ? 0 : 24 * (page - 2);
                
                    while(cont < (24*(page-1)) && cont < sounds.size()){
                        eb.addField("**"+sounds.get(cont).get(1)+"**", "ID: " + sounds.get(cont).get(0), true);
                        cont++;
                    }


                    
                    if ((page - 1) == 1){
                        left = left.asDisabled();
                        left = left.withStyle(ButtonStyle.DANGER);
                    }
                    
                    center = Button.primary("listuser-center-" + userId, "Page: " + (page - 1));
                    center = center.withStyle(ButtonStyle.SUCCESS);
                    center = center.asDisabled();
                    event.getMessage().editMessageEmbeds(eb.build())
                            .setActionRow(left, center, right)
                            .queue();
                    break;
            }
        }
    }

}