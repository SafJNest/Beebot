package com.safjnest.model.guild;

import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryRecord;

public class AutomatedAction {

    private int id;
    private int action;
    private String roleId;
    private int actionTime;
    private int infractions;
    private int infractionsTime;

    public static final int MUTE = 1;
    public static final int KICK = 2;
    public static final int BAN = 3;

    public AutomatedAction(int id, int action, String roleId, int actionTime, int infractions, int infractionsTime) {
        this.id = id;
        this.action = action;
        this.roleId = roleId;
        this.actionTime = actionTime;
        this.infractions = infractions;
        this.infractionsTime = infractionsTime;
    }

    public AutomatedAction(QueryRecord result) {
        this.id = result.getAsInt("id");
        this.action = result.getAsInt("action");
        this.roleId = result.get("action_role");
        this.actionTime = result.getAsInt("action_time");
        this.infractions = result.getAsInt("infractions");
        this.infractionsTime = result.getAsInt("infractions_time");
    }

    public static AutomatedAction create(String guildId, int action, String roleId, int actionTime, int infractions, int infractionsTime) {
        int id = BotDB.createAutomatedAction(guildId, action, roleId, actionTime, infractions, infractionsTime);
        return new AutomatedAction(id, action, roleId, actionTime, infractions, infractionsTime);
    }

    public int getId() {
        return id;
    }

    public int getAction() {
        return action;
    }

    public int getActionTime() {
        return actionTime;
    }

    public int getInfractions() {
        return infractions;
    }

    public int getInfractionsTime() {
        return infractionsTime;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public void setActionTime(int actionTime) {
        this.actionTime = actionTime;
    }

    public void setInfractions(int infractions) {
        this.infractions = infractions;
    }

    public void setInfractionsTime(int infractionsTime) {
        this.infractionsTime = infractionsTime;
    }

    public boolean canExecute(String memberId) {
        int memberInfractions = 0;
        if (infractionsTime == 0)  memberInfractions = BotDB.getMemberWarnings(memberId);
        else memberInfractions = BotDB.getMemberWarnings(memberId, infractionsTime);
        
        return memberInfractions >= infractions;
    }

    public String getActionMessage() {
        String action = "";
        switch (this.action) {
            case 1:
                action = "muted";
                break;
            case 2:
                action = "kicked";
                break;
            case 3:
                action = "banned";
                break;
        }
        String time = actionTime == 0 ? "permanently (until an admin unpunishes them)" : "for " + actionTime + " seconds";
        return "User got " + action + " " + time;
    }

    @Override
    public String toString() {
        return "Warning{" +
                "id=" + id +
                ", action=" + action +
                ", actionTime=" + actionTime +
                ", infractions=" + infractions +
                ", infractionsTime=" + infractionsTime +
                '}';
    }

}
