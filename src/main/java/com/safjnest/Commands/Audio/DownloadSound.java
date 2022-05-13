package com.safjnest.Commands.Audio;

import java.io.File;

import com.amazonaws.services.s3.AmazonS3;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SoundBoard;



public class DownloadSound extends Command{
    AmazonS3 s3Client;
    String nameFile;

    public DownloadSound(AmazonS3 s3Client){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.s3Client = s3Client;
    }

    @Override
    protected void execute(CommandEvent event) {
        nameFile = event.getArgs();
        if(nameFile.isEmpty()){
            event.reply("Il nome iedocrop");
            return;
        }
        AwsS3 aw = new AwsS3(s3Client, "thebeebox");
        String newNameFile = event.getAuthor().getName()+"_"+nameFile;
        aw.downloadFile(nameFile, event, newNameFile);
        File toSend = new File("rsc/SoundBoard/"+newNameFile+"."+SoundBoard.getExtension(newNameFile));
        event.reply(toSend, newNameFile+"."+SoundBoard.getExtension(newNameFile));

    }
}
