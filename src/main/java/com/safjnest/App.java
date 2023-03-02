package com.safjnest;

public class App {
    
    public static void main(String args[]) throws InterruptedException{
        BotSettingsHandler bs = new BotSettingsHandler();
        Thread b1 = new Thread(new Bot(bs));
        b1.setName("beebot");
        b1.start();
        Thread b2 = new Thread(new Bot(bs));
        b2.setName("beebot 2");
        b2.start();
        Thread b3 = new Thread(new Bot(bs));
        b3.setName("beebot 3");
        b3.start();
        Thread bc = new Thread(new Bot(bs));
        bc.setName("canary");
        bc.start();

    }

   
}
