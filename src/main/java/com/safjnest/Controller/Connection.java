package com.safjnest.Controller;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

public class Connection extends WebSocketServer {
    
    private JDA jda;
    private static int TCP_PORT = 8096;
    
    public Connection(JDA jda){
        super(new InetSocketAddress(TCP_PORT));
        this.jda = jda;
        setupSSL();
    }

    private void setupSSL() {
        try {
            char[] passphrase = App.key.toCharArray(); // Keystore password
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream ksInputStream = new FileInputStream("path/to/keystore.jks"); // Path to your keystore
            ks.load(ksInputStream, passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onStart() {
        System.out.println("Server started on port: " + TCP_PORT);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection to " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        String args[] = message.split("-", 2);
        System.out.println(args[0] + " " + args[1]);
        switch (args[0]){
            case "connected":
                conn.send("connected-"+jda.getUserById(args[1]).getName());
                break;
            case "server_name":
                String serverName = jda.getGuildById(args[1]).getName();
                conn.send(serverName);
                break;
            case "server_list":
                String server ="server_list-";
                for(Guild g : jda.getGuilds()){
                    try {
                        Member theGuy = g.getMemberById(args[1]);
                        if(theGuy.getPermissions().toString().contains("ADMINISTRATOR"))
                            server += g.getId() + "/";
                        
                    } catch (Exception e) {
                        
                    }
                }
                conn.send(server);
                break;
            case "checkBeebot":
                server ="server_list-[";
                String ids = message.split("-", 3)[2];
                System.out.println(ids);
                for(String id : ids.split("/")){
                    try {
                        Guild g = jda.getGuildById(id);                        
                        server += "{\"id\":\"" + g.getId() + "\",\"name\":\"" + g.getName() + "\" ,\"icon\":\"" + g.getIconUrl() + "\"},";
                    } catch (Exception e) {
                       
                    }
                }
                server = server.substring(0, server.length()-1);
                conn.send(server+"]");
                break;
            default:
                conn.send("idk");
                break;
        }
    }

    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        System.out.println("Error from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
        ex.printStackTrace();
    }
    
}
