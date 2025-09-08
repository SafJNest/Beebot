package com.safjnest.util.lol;

/**
 * Simple test class to verify the curl-based LoL handlers compile
 */
public class CurlTestExample {
    
    public static void main(String[] args) {
        // Test MobalyticsHandler
        System.out.println("Testing MobalyticsHandler with curl...");
        String mobalyticsResult = MobalyticsHandler.getChampioStats("akali", "TOP");
        System.out.println("Mobalytics result: " + (mobalyticsResult != null ? "Success" : "Failed"));
        
        // Test LeagueHandler
        System.out.println("Testing LeagueHandler with curl...");
        String braveryResult = LeagueHandler.getBraveryBuildJSON();
        System.out.println("Bravery result: " + (braveryResult != null ? "Success" : "Failed"));
        
        // Test with custom parameters
        String[] roles = {"0", "1", "2"};
        String[] champs = {"1", "2", "3"};
        String customBraveryResult = LeagueHandler.getBraveryBuildJSON(18, roles, champs);
        System.out.println("Custom Bravery result: " + (customBraveryResult != null ? "Success" : "Failed"));
    }
}