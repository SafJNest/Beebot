package com.safjnest.Utilities;

import java.io.File;
import java.io.IOException;


import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;

public class SoundBoard {
    private static String path = "rsc" + File.separator + "SoundBoard";
    private static File folder = new File(path);

    public static String containsFile(String nameFile){
        String[] names = getAllNamesNoExc();
        String[] namesEx = getAllNames();
        for(int i = 0; i < names.length; i++)
            if(names[i].equalsIgnoreCase(nameFile))
                return namesEx[i];
        return null;
    }

    public static Mp3File[] getMP3File(){
        Mp3File[] files = new Mp3File[folder.listFiles().length];
        int i = 0;
        for(File file : folder.listFiles()){
            try {
                files[i] = new Mp3File(file);
                i++;
            } catch (UnsupportedTagException | InvalidDataException | IOException e) {e.printStackTrace();}
        }
        return files;
    }

    public static Mp3File getMp3FileByName(String name){
        for(File file : folder.listFiles()){
            if(name.equalsIgnoreCase(file.getName().substring(0, file.getName().indexOf(".")))){
                try {
                    return new Mp3File(file);
                } catch (UnsupportedTagException | InvalidDataException | IOException e) {
                    e.printStackTrace();
                }
            }
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
