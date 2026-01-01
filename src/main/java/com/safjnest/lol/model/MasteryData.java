package com.safjnest.lol.model;

import java.util.Date;

public class MasteryData {

    private int champion_id;
    private int champion_points;
    private int champion_level;
    private Date last_play_time;

    public int getChampion_id() { return champion_id; }
    public void setChampion_id(int champion_id) { this.champion_id = champion_id; }

    public int getChampion_points() { return champion_points; }
    public void setChampion_points(int champion_points) { this.champion_points = champion_points; }

    public int getChampion_level() { return champion_level; }
    public void setChampion_level(int champion_level) { this.champion_level = champion_level; }

    public Date getLast_play_time() { return last_play_time; }
    public void setLast_play_time(Date last_play_time) { this.last_play_time = last_play_time; }
}
