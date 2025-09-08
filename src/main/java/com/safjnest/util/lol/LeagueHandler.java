package com.safjnest.util.lol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * This class contains League of Legends related utilities converted to use curl
 *
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */
public class LeagueHandler {

    /**
     * Get bravery build JSON using curl for HTTP requests
     * 
     * @param level the level for the build
     * @param roles the roles array
     * @param champions the champions array
     * @return JSON response from Ultimate Bravery API
     */
    public static String getBraveryBuildJSON(int level, String[] roles, String[] champions) {
        try {
            String jsonInputString = "{\"map\": 11,\"level\": " + level + ",\"roles\": [" + String.join(",", roles) +"],\"language\": \"en\",\"champions\": [" + String.join(",", champions) + "]}";
            
            // Create a temporary file for the JSON payload
            File tempFile = File.createTempFile("bravery_request", ".json");
            tempFile.deleteOnExit();
            
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(jsonInputString);
            }

            // Build curl command
            ProcessBuilder pb = new ProcessBuilder(
                "curl",
                "-X", "POST",
                "-H", "Content-Type: application/json",
                "-H", "Accept: application/json",
                "--data-binary", "@" + tempFile.getAbsolutePath(),
                "https://api2.ultimate-bravery.net/bo/api/ultimate-bravery/v1/classic/dataset"
            );

            Process process = pb.start();
            
            // Read the response
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return response.toString();
            } else {
                // Read error stream if there's an error
                StringBuilder error = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        error.append(line);
                    }
                }
                System.err.println("Curl error: " + error.toString());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get bravery build JSON with default parameters using curl
     * 
     * @return JSON response from Ultimate Bravery API
     */
    public static String getBraveryBuildJSON() {
        int lvl = 20;
        String[] roles = {"0", "1", "2", "3", "4"};
        // Note: In real implementation, this would use champion data from Riot API
        // For now, using placeholder champion IDs
        String[] champs = {"1", "2", "3", "4", "5"}; // Placeholder champion IDs
        return getBraveryBuildJSON(lvl, roles, champs);
    }
}