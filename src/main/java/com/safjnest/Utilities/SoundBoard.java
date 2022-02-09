package com.safjnest.Utilities;

import java.io.File;

public class SoundBoard {
    private static String path = "SoundBoard";
    private static File folder = new File(path);

    public static String containsFile(String nameFile){
        String[] names = getAllNamesNoExc();
        String[] namesEx = getAllNames();
        for(int i = 0; i < names.length; i++)
            if(names[i].equalsIgnoreCase(nameFile))
                return namesEx[i];
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
