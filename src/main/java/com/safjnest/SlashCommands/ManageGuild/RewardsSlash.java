package com.safjnest.SlashCommands.ManageGuild;

import java.awt.Color;
import java.util.ArrayList;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class RewardsSlash extends SlashCommand {

    public RewardsSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).and(createEmbed(event.getHook())).queue();
	}

    public static WebhookMessageEditAction<Message> createEmbed(InteractionHook hook){
        Guild g = hook.getInteraction().getGuild();
        Button add = Button.success("rewards-add", "+");
        ArrayList<Button> buttons = new ArrayList<>();
        buttons.add(add);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Rewards");
        eb.setThumbnail(g.getIconUrl());
        
        eb.setColor(Color.decode(BotSettingsHandler.map.get(hook.getJDA().getSelfUser().getId()).color));
        eb.setDescription("Create custom rewards for your guild.\n" 
            + "Just prepare a role that you want to give to people that reach a certain level in this guild and click the \"+\" button\n" 
            + "You have to have the Exp System enabled to be able to level up. You can enable it with /levelup toggle:true.");

        String query = "SELECT role_id, level FROM rewards_table WHERE guild_id = '" + g.getId() + "' ORDER BY level DESC;";

        ArrayList<ArrayList<String>> rewards = DatabaseHandler.getSql().getAllRows(query, 2);
        if(rewards.size() == 0) {
            eb.addField("No rewards", "There are no rewards set up for this guild yet.", false);
        }
        else {
            for(ArrayList<String> reward : rewards) {
                buttons.add(Button.primary("rewards-role-" + reward.get(0), reward.get(1)));
                eb.addField(g.getRoleById(reward.get(0)).getName(), "Level: " + reward.get(1), false);
            }
        }

        eb.setFooter("You have to click on a reward twice to delete it!");

        return hook.editOriginalEmbeds(eb.build()).setActionRow(buttons);
    }

    public static MessageEditAction createEmbed(Message message) {
        Guild g = message.getGuild();
        Button add = Button.success("rewards-add", "+");
        ArrayList<Button> buttons = new ArrayList<>();
        buttons.add(add);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Rewards");
        eb.setThumbnail(g.getIconUrl());
        eb.setColor(Color.decode(BotSettingsHandler.map.get(message.getJDA().getSelfUser().getId()).color));
        eb.setDescription("Create custom rewards for your guild.\n" 
            + "Just prepare a role that you want to give to people that reach a certain level in this guild and click the \"+\" button\n" 
            + "You have to have the Exp System enabled to be able to level up. You can enable it with /levelup toggle:true.");

        String query = "SELECT role_id, level FROM rewards_table WHERE guild_id = '" + g.getId() + "' ORDER BY level DESC;";

        ArrayList<ArrayList<String>> rewards = DatabaseHandler.getSql().getAllRows(query, 2);
        if(rewards.size() == 0) {
            eb.addField("No rewards", "There are no rewards set up for this guild yet.", false);
        }
        else{
            for(ArrayList<String> reward : rewards) {
                buttons.add(Button.primary("rewards-role-" + reward.get(0), reward.get(1)));
                eb.addField(g.getRoleById(reward.get(0)).getName(), "Level: " + reward.get(1), false);
            }
        }
        eb.setFooter("You have to click on a reward twice to delete it!");  
        return message.editMessageEmbeds(eb.build()).setActionRow(buttons);
    }
}