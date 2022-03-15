package com.safjnest.Commands.Audio;

import java.io.File;

//import javax.xml.crypto.dsig.keyinfo.KeyName;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.FileListener;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class Upload extends Command{

    private static String bucketName = "thebeebox";
    private static String keyName = "tre.mp3";

    public Upload(){
        this.name = "upload";
        this.aliases = new String[]{"up", "add"};
        this.help = "Il comando consente di poter caricare facilmente dei suoni nel database del bot.\nSe carichi dei file mp3 ci fai un piacere.\n"
        + "Se carichi dei .opus ti sgozzo.";
        this.category = new Category("Audio");
        this.arguments = "[upload] [nome del suono, senza specificare il formato]";
    }
    
	@Override
	protected void execute(CommandEvent event) {
        event.reply("operativo e pronto a listenare");
        FileListener listino = new FileListener(event.getArgs());
        event.getJDA().addEventListener(listino);
        
        AWSCredentials credentials = new BasicAWSCredentials("AKIASJG3D4LSZMKR7L4R", "zufmhZG5m8QhDZCeBYALs2S1wOu/x9zgoYxjbZIV");
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");

        AmazonS3 s3Client = AmazonS3ClientBuilder
            .standard()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("s3.us-east-1.amazonaws.com", "us-east-1"))
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(clientConfiguration)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
            
        try {
            System.out.println("Uploading a new object to S3 from a file\n");
            File file = new File("Upload\\eee.mp3");
            // Upload file
            PutObjectRequest request = new PutObjectRequest(bucketName, keyName, file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("audio/mpeg");
            metadata.addUserMetadata("name", "eee");
            metadata.addUserMetadata("category", "dio");
            request.setMetadata(metadata);
            s3Client.putObject(request);
        }catch(AmazonClientException ace){
            ace.printStackTrace();
        }
	}
}
