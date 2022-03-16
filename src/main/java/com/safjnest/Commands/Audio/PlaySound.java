package com.safjnest.Commands.Audio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.FileListener;


public class PlaySound extends Command{
    AmazonS3 s3Client;
    S3Object fullObject = null;
    String name;

    public PlaySound(AmazonS3 s3Client){
        this.name = "playsound";
        this.aliases = new String[]{"ps", "playsos"};
        this.category = new Category("Audio");
        this.arguments = "[playsound] [nome del suono, senza specificare il formato]";
        this.s3Client = s3Client;
    }

    @Override
    protected void execute(CommandEvent event) {
        if((name = event.getArgs()) == ""){
            event.reply("il nome idiota");
            return;
        }
        
        try {
            System.out.println("Downloading an object");
            fullObject = s3Client.getObject(new GetObjectRequest("thebeebox", name));
            System.out.println("Content-Type: " + fullObject.getObjectMetadata().getContentType());
            S3ObjectInputStream s3is = fullObject.getObjectContent();
            FileOutputStream fos = new FileOutputStream(new File("Download"+ File.separator + name + ".mp3"));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
        } catch (AmazonClientException | IOException ace) {
            ace.printStackTrace();
        }
    }
}
