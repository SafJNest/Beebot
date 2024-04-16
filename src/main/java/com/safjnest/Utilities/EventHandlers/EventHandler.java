package com.safjnest.Utilities.EventHandlers;

import com.safjnest.Bot;
import com.safjnest.Commands.League.Summoner;
import com.safjnest.Utilities.Functions;
import com.safjnest.Utilities.Guild.Alert.AlertType;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e) {
        Guild guild = e.getGuild();
        User self = e.getJDA().getSelfUser();

        AudioChannel channelJoined = e.getChannelJoined();
        AudioChannel channelLeft = e.getChannelLeft();
        AudioChannel connectChannel = guild.getAudioManager().getConnectedChannel();

        if(e.getMember().getId().equals(self.getId()) && channelJoined == null)
            Functions.handleBotLeave(guild);

        if (Functions.isBotAlone(connectChannel, channelLeft))
            Functions.handleBotLeave(guild);
        
        if (channelJoined != null && 
            (connectChannel == null || channelJoined.getId().equals(connectChannel.getId()))) {
            Functions.handleGreetSound(channelJoined, self, guild);
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event){
        System.out.println("[CACHE] Pushing new Guild into Database=> " + event.getGuild().getId());
        DatabaseHandler.insertGuild(event.getGuild().getId(), Bot.getPrefix());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        Functions.handleCustomCommand(commandName, event);
        
        if(!Bot.getGuildSettings().getServer(event.getGuild().getId()).getCommandStatsRoom(event.getChannel().getIdLong()))
            return;
        commandName = event.getName() + "Slash";
        String args = event.getOptions().toString();
        DatabaseHandler.insertCommand(event.getGuild().getId(), event.getMember().getId(), commandName, args);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) { }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User newGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(newGuy, guild, AlertType.WELCOME);
        Functions.handleBlacklist(newGuy, guild);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event){
        User oldGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(oldGuy, guild, AlertType.LEAVE);
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        User theGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleBlacklistAlert(theGuy, guild);
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        User theGuy = event.getUser();
        DatabaseHandler.deleteBlacklist(event.getGuild().getId(), theGuy.getId());
    }

    @Override
    public void onGuildMemberUpdateBoostTime(GuildMemberUpdateBoostTimeEvent event) {
        User theGuy = event.getUser();
        Guild guild = event.getGuild();

        Functions.handleAlert(theGuy, guild, AlertType.BOOST);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event){
        if(!event.getChannelType().isAudio()){
            Functions.handleChannelDeleteAlert(event.getGuild(), event.getChannel().getId());
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("rank-select")) {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = RiotHandler.getSummonerBySummonerId(event.getValues().get(0));
            event.deferReply().addEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(), s).build()).queue();
        }
    }
}
