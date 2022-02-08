package com.safjnest.Utilities;

import java.io.File;

public class SoundBoard {
    private static String path = "SoundBoard";
    private static File folder = new File(path);

    public static String containsFile(String nameFile){
        File[] arr = folder.listFiles();
            for(File eee : arr){
                if(eee.getName().startsWith(nameFile))
                    return eee.getName();
            }
            return null;
    }

    public static String[] getAllNames(){
        File[] arr = folder.listFiles();
        String[] names = new String[arr.length];
        for(int i = 0; i < arr.length; i++){
            names[i] = arr[i].getName();
        }
        return names;
    }

    public static String[] getAllNamesNoExc(){
        File[] arr = folder.listFiles();
        String[] names = new String[arr.length];
        for(int i = 0; i < arr.length; i++){
            names[i] = arr[i].getName().replaceFirst("[.][^.]+$", "");
        }
        return names;
    }
}
