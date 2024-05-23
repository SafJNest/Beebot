package com.safjnest.Utilities.Audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.voicerss.tts.Voice.Voices;

public class TTSVoices {
    private static final HashMap<String, Set<String>> voices = new HashMap<String, Set<String>>();
    
    static {
        voices.put(Voices.Arabic_Egypt.id, Set.of(Voices.Arabic_Egypt.array));
        voices.put(Voices.Chinese_China.id, Set.of(Voices.Chinese_China.array));
        voices.put(Voices.Dutch_Netherlands.id, Set.of(Voices.Dutch_Netherlands.array));
        voices.put(Voices.English_GreatBritain.id, Set.of(Voices.English_GreatBritain.array));
        voices.put(Voices.English_India.id, Set.of(Voices.English_India.array));
        voices.put(Voices.English_UnitedStates.id, Set.of(Voices.English_UnitedStates.array));
        voices.put(Voices.French_France.id, Set.of(Voices.French_France.array));
        voices.put(Voices.German_Germany.id, Set.of(Voices.German_Germany.array));
        voices.put(Voices.Greek.id, Set.of(Voices.Greek.array));
        voices.put(Voices.Italian.id, Set.of(Voices.Italian.array));
        voices.put(Voices.Japanese.id, Set.of(Voices.Japanese.array));
        voices.put(Voices.Korean.id, Set.of(Voices.Korean.array));
        voices.put(Voices.Polish.id, Set.of(Voices.Polish.array));
        voices.put(Voices.Portuguese_Portugal.id, Set.of(Voices.Portuguese_Portugal.array));
        voices.put(Voices.Romanian.id, Set.of(Voices.Romanian.array));
        voices.put(Voices.Russian.id, Set.of(Voices.Russian.array));
        voices.put(Voices.Swedish.id, Set.of(Voices.Swedish.array));
        voices.put(Voices.Spanish_Spain.id, Set.of(Voices.Spanish_Spain.array));
    }

    public static HashMap<String, Set<String>> getVoices() {
        return voices;
    }

    public static List<String> getVoiceList() {
        List<String> voiceList = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : voices.entrySet()) {
            String language = entry.getKey();
            Set<String> voiceNames = entry.getValue();
            for (String voiceName : voiceNames) {
                voiceList.add(language + " - " + voiceName);
            }
        }
        return voiceList;
    }

    public static List<String[]> getVoiceArray() {
        List<String[]> voiceArray = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : voices.entrySet()) {
            String language = entry.getKey();
            Set<String> voiceNames = entry.getValue();
            for (String voiceName : voiceNames) {
                voiceArray.add(new String[] {language, voiceName});
            }
        }
        return voiceArray;
    }

    public static String getFormattedVoices() {
        StringBuilder formattedVoices = new StringBuilder();
        for(String voice : getVoiceList())
            formattedVoices.append(voice).append("\n");
        return formattedVoices.toString();
    }

}
