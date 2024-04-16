package com.safjnest.Utilities.EventHandlers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.CommandListener;
import com.safjnest.Utilities.Functions;

public class CommandEventHandler implements CommandListener{
    @Override
    public void onCommand(CommandEvent event, Command command){
        Functions.updateCommandStatitics(event, command);
    }
}
