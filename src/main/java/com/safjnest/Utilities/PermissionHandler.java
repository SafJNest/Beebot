package com.safjnest.Utilities;

import java.util.Set;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class PermissionHandler {
    private static Set<String> untouchables = Set.of("383358222972616705", "440489230968553472");
    private static String epria = "707479292644163604";

    public static Set<String> getUntouchables() {
        return untouchables;
    }

    //public static void addUntouchable(String id){

    //}

    public static boolean isUntouchable(String id) {
        if(untouchables.contains(id))
            return true;
        return false;
    }

    public static boolean isEpria(String id) {
        if(epria.equals(id))
            return true;
        return false;
    }

    public static String getEpria(){
        return epria;
    }

    public static boolean hasPermission(Member theGuy, Permission permission) {
        if (theGuy.hasPermission(permission) || untouchables.contains(theGuy.getId()))
            return true;
        return false;
    }

}
