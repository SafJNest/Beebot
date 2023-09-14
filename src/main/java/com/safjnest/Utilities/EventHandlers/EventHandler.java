package com.safjnest.Utilities.EventHandlers;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.safjnest.Commands.League.Summoner;
import com.safjnest.SlashCommands.ManageGuild.RewardsSlash;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Guild.GuildData;
import com.safjnest.Utilities.Guild.GuildSettings;
import com.safjnest.Utilities.LOL.Augment;
import com.safjnest.Utilities.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
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
public class EventHandler extends ListenerAdapter {
    private SQL sql;
    private GuildSettings gs;
    private String PREFIX;
    public EventHandler(SQL sql, GuildSettings gs, String PREFIX) {
        this.sql = sql;
        this.gs = gs;
        this.PREFIX = PREFIX;
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

    @Override
    public void onGuildJoin(GuildJoinEvent event){
        String query = "INSERT IGNORE INTO guild_settings(guild_id, bot_id, prefix) VALUES('" + event.getGuild().getId() + "', '" + event.getJDA().getSelfUser().getId() + "', '" + PREFIX + "')";
        sql.runQuery(query);
    }


 

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if(!gs.getServer(event.getGuild().getId()).getCommandStatsRoom(event.getChannel().getIdLong()))
            return;
        String commandName = event.getName() + "Slash";
        String query = "INSERT INTO command_analytic(name, time, user_id, guild_id, bot_id) VALUES ('" + commandName + "', '" + new Timestamp(System.currentTimeMillis()) + "', '" + event.getUser().getId()+ "', '"+ event.getGuild().getId() +"','"+ event.getJDA().getSelfUser().getId() +"');";
        sql.runQuery(query);
    }


    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
        ArrayList<Choice> choices = new ArrayList<>();
        String name = e.getName();
        if(e.getFullCommandName().equals("soundboard create"))
            name = "play";
        
        else if(e.getFocusedOption().getName().equals("sound_add"))
            name = "play";

        else if(e.getFocusedOption().getName().equals("sound_remove"))
            name = "sound_remove";

        else if(e.getFullCommandName().equals("soundboard select") || e.getFullCommandName().equals("soundboard add") || e.getFullCommandName().equals("soundboard remove") || e.getFullCommandName().equals("soundboard delete"))
            name = "soundboard_select";
        
        else if(e.getFullCommandName().equals("customizesound"))
            name = "user_sound";
        else if(e.getFullCommandName().equals("bugsnotifier"))
            name = "help";
        
        switch (name){
            
            case "play":
                if(e.getFocusedOption().getValue().equals("")){
                    String query = "SELECT name, id FROM sound WHERE guild_id = '" + e.getGuild().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }else{
                    String query = "SELECT name, id FROM sound WHERE name LIKE '"+e.getFocusedOption().getValue()+"%' AND guild_id = '" + e.getGuild().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }
                break;
             case "user_sound":
                if(e.getFocusedOption().getValue().equals("")){
                    String query = "SELECT name, id FROM sound WHERE user_id = '" + e.getMember().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0) + " (" + arr.get(1) + ")", arr.get(1)));
                }else{
                    String query = "SELECT name, id FROM sound WHERE name LIKE '"+e.getFocusedOption().getValue()+"%' OR id LIKE '"+e.getFocusedOption().getValue()+ "%' AND user_id = '" + e.getMember().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0) + " (" + arr.get(1) + ")", arr.get(1)));
                }
                break;

            case "help":

                List<Command> allCommands = e.getJDA().retrieveCommands().complete();
                if(e.getFocusedOption().getValue().equals("")){
                    Collections.shuffle(allCommands);
                    for(int i = 0; i < 10; i++)
                        choices.add(new Choice(allCommands.get(i).getName(), allCommands.get(i).getName()));
                }else{
                    for(Command c : allCommands){
                        if(c.getName().startsWith(e.getFocusedOption().getValue()))
                            choices.add(new Choice(c.getName(), c.getName()));
                    }
                }
                break;

            case "champion":
                List<String> champions = Arrays.asList(RiotHandler.getChampions());
                if(e.getFocusedOption().getValue().equals("")){
                    Collections.shuffle(champions);
                    for(int i = 0; i < 10; i++)
                        choices.add(new Choice(champions.get(i), champions.get(i)));
                }else{
                    int max = 0;
                    for(int i = 0; i < champions.size() && max < 10; i++){
                        if(champions.get(i).toLowerCase().startsWith(e.getFocusedOption().getValue().toLowerCase())){
                            choices.add(new Choice(champions.get(i), champions.get(i)));
                            max++;
                        }
                    }
                }
                break; 
            
            case "infoaugment":
                List<Augment> augments = RiotHandler.getAugments();
                if(e.getFocusedOption().getValue().equals("")){
                    Collections.shuffle(augments);
                    for(int i = 0; i < 10; i++)
                        choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                }else{
                    int max = 0;
                    if(e.getFocusedOption().getValue().matches("\\d+")){
                        for(int i = 0; i < augments.size() && max < 10; i++){
                            if(augments.get(i).getId().startsWith(e.getFocusedOption().getValue())){
                                choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                                max++;
                            }
                        }
                        }else{
                            for(int i = 0; i < augments.size() && max < 10; i++){
                                if(augments.get(i).getName().toLowerCase().startsWith(e.getFocusedOption().getValue().toLowerCase())){
                                    choices.add(new Choice(augments.get(i).getName(), augments.get(i).getId()));
                                    max++;
                                }
                            }
                    }
                }
            break; 

            case "soundboard_select":
                if(e.getFocusedOption().getValue().equals("")){
                    String query = "SELECT name, id FROM soundboard WHERE guild_id = '" + e.getGuild().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }else{
                    String query = "SELECT name, id FROM soundboard WHERE name LIKE '"+e.getFocusedOption().getValue()+"%' AND guild_id = '" + e.getGuild().getId() + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }
                break;
            case "sound_remove":
                if(e.getOption("name") == null)
                    return;
                String soundboardId = e.getOption("name").getAsString();
                if(e.getFocusedOption().getValue().equals("")){
                    String query = "SELECT s.name, s.id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE ss.id = '" + soundboardId + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }else{
                    String query = "SELECT s.name, s.id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE s.name LIKE '"+e.getFocusedOption().getValue()+"%' AND ss.id = '" + soundboardId + "' ORDER BY RAND() LIMIT 25;";
                    for(ArrayList<String> arr : DatabaseHandler.getSql().getAllRows(query, 2))
                        choices.add(new Choice(arr.get(0), arr.get(1)));
                }
                break;
        }
        
        e.replyChoices(choices).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if(event.getModalId().startsWith("rewards")){
            String role = event.getValue("rewards-role").getAsString();
            String lvl = event.getValue("rewards-lvl").getAsString();
            String msg = event.getValue("rewards-message").getAsString();

            if(msg.equals("//")) 
                msg = null;
            try {
                role = event.getGuild().getRolesByName(role.substring(1), true).get(0).getId();
            } catch (Exception e) {
                event.reply("Role not found").queue();
                return;
            }
            String query = "INSERT INTO rewards_table (guild_id, role_id, level, message_text) VALUES ('" + event.getGuild().getId() + "', '" + role + "', '" + lvl + "', '" + msg + "');";
            DatabaseHandler.getSql().runQuery(query);
            event.deferEdit().queue();
            RewardsSlash.createEmbed(event.getMessage(), event.getGuild()).queue();
        }
    }

    /**
     * On join of a user (to make the bot welcome the new member)
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        /**
         * Welcome message 
         */
        MessageChannel channel = null;
        User newGuy = event.getUser();
        String query = "SELECT channel_id FROM welcome_message WHERE guild_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String notNullPls = sql.getString(query, "channel_id");
        if (notNullPls != null){
            channel = event.getGuild().getTextChannelById(notNullPls);
            query = "SELECT message_text FROM welcome_message WHERE guild_id = '" + event.getGuild().getId()
                    + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
            String message = sql.getString(query, "message_text");
            message = message.replace("#user", newGuy.getAsMention());
            channel.sendMessage(message).queue();
            query = "SELECT role_id FROM welcome_roles WHERE guild_id = '" + event.getGuild().getId() + "' AND bot_id = '"
                    + event.getJDA().getSelfUser().getId() + "';";
            ArrayList<String> roles = sql.getAllRowsSpecifiedColumn(query, "role_id");
            if (roles.size() > 0) {
                for (String role : roles) {
                    event.getGuild().addRoleToMember(newGuy, event.getGuild().getRoleById(role)).queue();
                }
            }
        }

        /**
         * Blacklist
         */
        int threshold = gs.getServer(event.getGuild().getId()).getThreshold();
        if(threshold == 0)
            return;
        
        int times = 0;
        query = "SELECT count(user_id) as times from blacklist WHERE user_id = '" + newGuy.getId() + "'";
        String timesSql = DatabaseHandler.getSql().getString(query, "times");
        times = times + ((timesSql != null) ? Integer.valueOf(timesSql) : 0);

        if(gs.getServer(event.getGuild().getId()).getThreshold() == 0 || gs.getServer(event.getGuild().getId()).getThreshold() > times)
            return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getJDA().getSelfUser().getName());
        eb.setThumbnail(newGuy.getAvatarUrl());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The new member " + newGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");

        channel = event.getGuild().getTextChannelById(gs.getServer(event.getGuild().getId()).getBlackChannelId());

        Button kick = Button.primary("kick-" + newGuy.getId(), "Kick");
        Button ban = Button.primary("ban-" + newGuy.getId(), "Ban");
        Button ignore = Button.primary("ignore-" + newGuy.getId(), "Ignore");


        kick = kick.withStyle(ButtonStyle.PRIMARY);
        ban = ban.withStyle(ButtonStyle.PRIMARY);
        ignore = ignore.withStyle(ButtonStyle.SUCCESS);
        channel.sendMessageEmbeds(eb.build()).addActionRow(ignore, kick, ban).queue();
        
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event){
        
        MessageChannel channel = null;
        String query = "SELECT channel_id FROM left_message WHERE guild_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String notNullPls = sql.getString(query, "channel_id");
        if (notNullPls == null)
            return;
        channel = event.getGuild().getTextChannelById(notNullPls);
        query = "SELECT message_text FROM left_message WHERE guild_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String message = sql.getString(query, "message_text");
        message = message.replace("#user", event.getUser().getAsMention());
        channel.sendMessage(message).queue();
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        User theGuy = event.getUser();
        int threshold = gs.getServer(event.getGuild().getId()).getThreshold();
        String query = "";

        if(threshold == 0)
            return;
        
        query = "INSERT INTO blacklist VALUES('" + theGuy.getId() + "', '" + event.getGuild().getId() + "') ";
        DatabaseHandler.getSql().runQuery(query);
        int times = 0;
        query = "SELECT count(user_id) as times from blacklist WHERE user_id = '" + theGuy.getId() + "'";
        String timesSql = DatabaseHandler.getSql().getString(query, "times");
        times = times + ((timesSql != null) ? Integer.valueOf(timesSql) : 0);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getJDA().getSelfUser().getName());
        eb.setThumbnail(theGuy.getAvatarUrl());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The member " + theGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");
        for(GuildData g : gs.cache.values()){
            if(g.getThreshold() == 0 || g.getThreshold() > times || g.getId() == event.getGuild().getIdLong())
                continue;
            
            Guild gg = event.getJDA().getGuildById(g.getId());
            if(gg.getMemberById(theGuy.getId()) == null)
                continue;

            TextChannel channel = gg.getTextChannelById(g.getBlackChannelId());

            Button kick = Button.primary("kick-" + theGuy.getId(), "Kick");
            Button ban = Button.primary("ban-" + theGuy.getId(), "Ban");
            Button ignore = Button.primary("ignore-" + theGuy.getId(), "Ignore");


            kick = kick.withStyle(ButtonStyle.PRIMARY);
            ban = ban.withStyle(ButtonStyle.PRIMARY);
            ignore = ignore.withStyle(ButtonStyle.SUCCESS);
            channel.sendMessageEmbeds(eb.build()).addActionRow(ignore, kick, ban).queue();
            //channel.sendMessage("THIS PIECE OF SHIT DOGSHIT RANDOM " + theGuy.getName() + " HAS BEEN BANNED " + times + " TIMES").queue();
        }
        
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        User theGuy = event.getUser();
        String query = "DELETE FROM blacklist WHERE guild_id = '" + event.getGuild().getId() + "' AND user_id = '" + theGuy.getId() + "'";
        DatabaseHandler.getSql().runQuery(query);
    }

    /**
     * On update of a user's boost time (to make the bot praise the user)
     */
    public void onGuildMemberUpdateBoostTime(GuildMemberUpdateBoostTimeEvent event) {
        MessageChannel channel = null;
        String query = "SELECT channel_id FROM boost_message WHERE guild_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String notNullPls = sql.getString(query, "channel_id");
        if (notNullPls == null)
            return;
        channel = event.getGuild().getTextChannelById(notNullPls);
        query = "SELECT message_text FROM boost_message WHERE guild_id = '" + event.getGuild().getId()
                + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String message = sql.getString(query, "message_text");
        message = message.replace("#user", event.getUser().getAsMention());
        channel.sendMessage(message).queue();
    }


    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("rank-select")) {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = RiotHandler.getSummonerBySummonerId(event.getValues().get(0));
            event.deferReply().addEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(), s).build()).queue();
        }
    }

}