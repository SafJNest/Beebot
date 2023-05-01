package com.safjnest.Commands.Admin;

import java.util.ArrayList;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.PermissionHandler;
import com.vdurmont.emoji.EmojiParser;


public class Query extends Command{
    /**
     * Default constructor for the class.
     */
    public Query(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.hidden = true;
    }


    public void replaceIdsWithNames(String[][] data, CommandEvent e) {
        for(int i = 0; i < data.length; i++){
            for(int j = 0; j < data[i].length; j++){
                if(data[i][j].matches("\\d+")) {
                    if(e.getJDA().getUserById(data[i][j]) != null){
                        data[i][j] = e.getJDA().getUserById(data[i][j]).getName(); 
                        data[i][j] = EmojiParser.removeAllEmojis(data[i][j]);
                    }
                    else if(e.getJDA().getGuildById(data[i][j]) != null){
                        data[i][j] = e.getJDA().getGuildById(data[i][j]).getName();
                        data[i][j] = EmojiParser.removeAllEmojis(data[i][j]);
                    }
                }
            }
        }
    }
    
    public String constructTable(String[][] data, String[] headers, int numColumns, int numRows ) {
        // determinazione della larghezza delle colonne
        int[] colWidths = new int[numColumns];
        for (int col = 0; col < numColumns; col++) {
            colWidths[col] = headers[col].length();
            for (int row = 0; row < numRows; row++) {
                if (data[row][col].length() > colWidths[col]) {
                    colWidths[col] = data[row][col].length();
                }
            }
        }
    
        // costruzione della tabella
        StringBuilder table = new StringBuilder();
        table.append("┌");
        for (int col = 0; col < numColumns; col++) {
            table.append("─".repeat(colWidths[col] + 2));
            if (col < numColumns - 1) {
                table.append("┬");
            }
        }
        table.append("┐\n");
    
        // stampa delle intestazioni delle colonne
        table.append("│ ");
        for (int col = 0; col < numColumns; col++) {
            table.append(String.format("%-" + colWidths[col] + "s", headers[col]));
            if (col < numColumns - 1) {
                table.append(" │ ");
            }
        }
        table.append(" │\n");
    
        // riga divisoria
        table.append("├");
        for (int col = 0; col < numColumns; col++) {
            table.append("─".repeat(colWidths[col] + 2));
            if (col < numColumns - 1) {
                table.append("┼");
            }
        }
        table.append("┤\n");
    
        // stampa dei dati
        for (int row = 0; row < numRows; row++) {
            table.append("│ ");
            for (int col = 0; col < numColumns; col++) {
                table.append(String.format("%-" + colWidths[col] + "s", data[row][col]));
                if (col < numColumns - 1) {
                    table.append(" │ ");
                }
            }
            table.append(" │\n");
        }
    
        // riga inferiore
        table.append("└");
        for (int col = 0; col < numColumns; col++) {
            table.append("─".repeat(colWidths[col] + 2));
            if (col < numColumns - 1) {
                table.append("┴");
            }
        }
        table.append("┘\n");
        return table.toString();
    }

    @Override
    protected void execute(CommandEvent e) {
        if(!PermissionHandler.isUntouchable(e.getAuthor().getId())){
            e.getAuthor().openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessage("figlio di troia non mi fai sql injectohnbjn ").queue());
            return;
        }

        String query = e.getArgs();
        if(query.equals("")){
            e.reply("Please specify a query to execute.");
            return;
        }

        ArrayList<ArrayList<String>> res = DatabaseHandler.getSql().getAllRows(query);
        String[][] data = new String[res.size()-1][res.get(0).size()]; 
        for(int i = 1; i < res.size(); i++)
            data[i-1] = res.get(i).toArray(new String[0]);
        String[] headers = res.get(0).toArray(new String[0]);
        
        replaceIdsWithNames(data, e);

        String table = constructTable(data, headers, headers.length, data.length);

        if(table.length() < 1950) {
            e.reply("```" + table + "```");
            return;
        }
        String[] splittedTable = table.split("\n");
        String temp = "";
        for(int i = 0; i < splittedTable.length; i++){
            if(temp.length() + splittedTable[i].length() < 1950)
                temp += splittedTable[i] + "\n";
            else{
                e.reply("```" + temp + "```");
                temp = splittedTable[i] + "\n";
            }
        }
        e.reply("```" + temp + "```");  
    }
}