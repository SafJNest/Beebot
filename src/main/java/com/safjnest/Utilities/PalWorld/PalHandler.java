package com.safjnest.Utilities.PalWorld;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

import org.apache.commons.collections4.bag.HashBag;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.safjnest.Utilities.LOL.AugmentData;

public class PalHandler {
    
    private String path = "rsc" + File.separator + "Testing" + File.separator + "palworld" + File.separator + "pals.json";

    private HashMap<String, Pal> pals = new HashMap<String, Pal>();

    public PalHandler() {
        loadPals();
    }

    private void loadPals() {
         try {
            FileReader reader = new FileReader(path);
            JSONParser parser = new JSONParser();
            JSONArray file = (JSONArray) parser.parse(reader);
            for (int i = 0; i < file.size(); i++) {
                JSONObject obj = (JSONObject) file.get(i);
                String name = (String) obj.get("name");
                String icon = (String) obj.get("image");
                String description = (String) obj.get("description");

                JSONArray types = (JSONArray) obj.get("types");
                String[] typesArr = new String[types.size()];
                for (int j = 0; j < types.size(); j++) {
                    typesArr[j] = (String) types.get(j);
                }

                HashMap<String, HashMap<String, String>> suitability = new HashMap<String, HashMap<String, String>>();
                JSONArray suit = (JSONArray) obj.get("suitability");
                for (int j = 0; j < suit.size(); j++) {
                    JSONObject suitObj = (JSONObject) suit.get(j);
                    String type = (String) suitObj.get("type");
                    HashMap<String, String> suitMap = new HashMap<String, String>();
                    JSONArray suitArr = (JSONArray) suitObj.get("suitability");
                    for (int k = 0; k < suitArr.size(); k++) {
                        JSONObject suitArrObj = (JSONObject) suitArr.get(k);
                        String key = (String) suitArrObj.get("key");
                        String value = (String) suitArrObj.get("value");
                        suitMap.put(key, value);
                    }
                    suitability.put(type, suitMap);
                }
                System.out.println(suitability.toString());

                String aura = (String) obj.get("aura");
                String auraDescription = (String) obj.get("auraDescription");
                
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

}
