package com.safjnest.Utilities.Controller;

import java.net.InetSocketAddress;
import net.dv8tion.jda.api.JDA;

import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.Guild.GuildSettings;

public class Connection extends WebSocketServer {
    
    private JDA jda;
    private static int TCP_PORT = 8096;
    private Postman postman;
    private GuildSettings gs;
    private BotSettingsHandler bs;
    
    public Connection(JDA jda, GuildSettings gs, BotSettingsHandler bs){
        super(new InetSocketAddress(TCP_PORT));
        this.jda = jda;
        this.gs = gs;
        this.bs = bs;
        this.postman = new Postman(jda, gs, bs);


    }

    public void willBeRemovedSoon(){
        /**
       * Smurfing
       */
      jda.getClass();
      gs.doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed();
      bs.doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed();
  }
    
    @Override
    public void onStart() {
        System.out.println("[Beebot] INFO Connection thread started -> " + TCP_PORT);
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
        //System.out.println("New connection from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
        //System.out.println("Closed connection to " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        System.out.println("RECEIVED: " + message);
        String args[] = message.split("-", 2);
        String server = "";
        switch (args[0]){
            case "checkBeebot":
                server = "server_list-" + postman.getServerList(message.split("-", 3)[1], message.split("-", 3)[2]);
                break;
            case "getHomeStats":
                server = "getHomeStats-" + postman.getHomeStats(args[1]);
                break;
            case "getPrefix":
                server = "getPrefix-" + postman.getPrefix(message.split("-", 3)[1], message.split("-", 3)[2]);
                break;
            case "newPrefix":
                try {
                    System.out.println(message.split("-", 4)[1] + " " + message.split("-", 4)[2]);
                    System.out.println(gs.getServer(message.split("-", 4)[1]).getPrefix());
                    gs.getServer(message.split("-", 4)[1]).setPrefix(message.split("-", 4)[2]);
                    System.out.println(gs.getServer(message.split("-", 4)[1]).getPrefix());
                    server = "newPrefix-ok"; 
                } catch (Exception e) {
                    server = "newPrefix-!ok"; 
                }
                break;
            default:
                server = "UNKNOWN COMMAND";
                break;
        }
        System.out.println("SENT: " + server);
        conn.send(server);
    }

    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        System.out.println("Error from " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
        ex.printStackTrace();
    }
    
}
