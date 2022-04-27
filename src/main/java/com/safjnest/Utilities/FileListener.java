package com.safjnest.Utilities;

import java.io.File;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class FileListener extends ListenerAdapter {
    private String name;

    public FileListener(String diocane){
        this.name = diocane;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        File saveFile = new File("rsc" + File.separator + "Upload" + File.separator + (name +"."+ event.getMessage().getAttachments().get(0).getFileExtension()));
        event.getMessage().getAttachments().get(0).downloadToFile(saveFile)
        .thenAccept(file -> System.out.println("Saved attachment to " + file.getName()))
        .exceptionally(t ->
        { // handle failure
            t.printStackTrace();
            return null;
        });
        event.getJDA().removeEventListener(this);
    }
}