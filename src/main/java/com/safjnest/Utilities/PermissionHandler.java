package com.safjnest.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class PermissionHandler {
    private static Set<String> untouchables = Set.of("383358222972616705", "440489230968553472");
    private static String epria = "707479292644163604";

    public static Set<String> getUntouchables() {
        return untouchables;
    }

    //public static void addUntouchable(String id){

    //}

    public static boolean isUntouchable(String id) {
        if (untouchables.contains(id))
            return true;
        return false;
    }

    public static boolean isEpria(String id) {
        if (epria.equals(id))
            return true;
        return false;
    }

    public static String getEpria(){
        return epria;
    }

    public static List<String> getPermissionNames(Member member){
        List<String> finalPermissions= new ArrayList<String>();
        for (Permission permission : member.getPermissions())
            finalPermissions.add(permission.getName());
        return finalPermissions;
    }

    public static List<String> getFilteredPermissionNames(Member member) {
        List<String> finalPermissions= new ArrayList<String>();
        for (Permission permission : member.getPermissions())
            if(Permission.getPermissions(getImportantPermissionsValue()).contains(permission))
                finalPermissions.add(permission.getName());
        return finalPermissions;
    }

    public static long getImportantPermissionsValue(){ 
        return Permission.getRaw(Permission.MANAGE_CHANNEL, Permission.CREATE_INSTANT_INVITE, 
        Permission.NICKNAME_CHANGE, Permission.NICKNAME_MANAGE, Permission.MANAGE_SERVER, 
        Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_WEBHOOKS, 
        Permission.VIEW_CHANNEL, Permission.MANAGE_EMOTES_AND_STICKERS, Permission.MESSAGE_SEND, 
        Permission.MODERATE_MEMBERS, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS, 
        Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_HISTORY, Permission.MESSAGE_EXT_EMOJI, 
        Permission.MESSAGE_MENTION_EVERYONE, Permission.MESSAGE_ADD_REACTION, Permission.MANAGE_THREADS, 
        Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.VOICE_USE_VAD, Permission.PRIORITY_SPEAKER, 
        Permission.VOICE_STREAM, Permission.ADMINISTRATOR);
    }

    public static List<String> getMaxFieldableRoleNames(List<Role> roles) {
        return getMaxFieldableRoleNames(roles, 1024);
    }

    public static List<String> getMaxFieldableRoleNames(List<Role> roles, int charNumber) {
        if(charNumber > 1024)
            throw new IllegalArgumentException("il numero dei caratteri non puo' essere maggiore di 1024");
        List<String> finalRoles= new ArrayList<String>();
        int rolesLenght = 0;
        for (int i = 0; i < roles.size(); i++) {
            rolesLenght += roles.get(i).getName().length() + 2;
            if(rolesLenght >= charNumber)
                break;
            finalRoles.add(roles.get(i).getName());
        }
        return finalRoles;
    }

    public static boolean hasPermission(Member theGuy, Permission permission) {
        if (theGuy.hasPermission(permission) || untouchables.contains(theGuy.getId()))
            return true;
        return false;
    }

}
