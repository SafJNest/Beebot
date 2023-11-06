package com.safjnest.Utilities.EXPSystem;

import java.util.HashMap;
import java.util.Random;

import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.ResultRow;


/**
 * This class is used to manage the experience system of the bot.
 * <p>
 * The experience system is used to give experience to the users of the bot when they send a message in a server every one minute.
 */
public class ExpSystem {

    /**
     * This HashMap is used to store the users and the time they sent a message.
     * The key is {@code userId-guildId} and the value is {@link com.safjnest.Utilities.EXPSystem.UserTime UserTime.
     */
    private HashMap<String, UserTime> users;

    public static final int NOT_LEVELED_UP = -1;

    
    /**
     * Constructor for the ExpSystem class.
     */
    public ExpSystem() {
        users = new HashMap<>();
    }


    public HashMap<String, UserTime> getUsers() {
        return users;
    }

    /**
     * This method is used to check if the user can receive experience.
     * <p>If the user is not cached, it will be added to the cache and will return true.
     * @param userId
     * @param guildId
     * @return
     */
    public synchronized int receiveMessage(String userId, String guildId, double modifier) {
        if(!users.containsKey(userId + "-" + guildId)) {
            users.put(userId + "-" + guildId, new UserTime());
            return addExp(userId, guildId, modifier);
        }

        UserTime user = users.get(userId + "-" + guildId);
        if (user.canReceiveExperience()) {
           return addExp(userId, guildId, modifier);
        }
        
        return NOT_LEVELED_UP;
    }


    /**
     * This method is used to calculate the experience that the user will receive.
     * <p> The experience is calculated randomly between 15 and 25.
     * @return
     */
    public int getRandomExp(){
        return new Random().nextInt((25 - 15) + 1) + 15;
    }


    /**
     * This method is used to calculate the total experience that the user needs to
     * get to be level {@code lvl} from zero.
     * <p>
     * The experience is calculated using the following formula:
     * {@code (5/6) * (lvl) * (2 * (lvl) * (lvl) + 27 * (lvl) + 91)}.
     * <table border="2">
     * <tr>
     * <td>LVL EXP</td>
     * </tr>
     * <tr>
     * <td>1 100</td>
     * </tr>
     * <tr>
     * <td>2 255</td>
     * </tr>
     * <tr>
     * <td>3 475</td>
     * </table>
     * @param lvl
     * @return
     */
    public static int getExpToReachLvlFromZero(int lvl){
        return (int) ((5.0/6.0) * (lvl) * (2 * (lvl) * (lvl) + 27 * (lvl) + 91));
    }

    public static int getExpToReachLvl(int lvl){
        return ExpSystem.getExpToReachLvlFromZero(lvl + 1) - ExpSystem.getExpToReachLvlFromZero(lvl);
    }


    /**
     * This method is used to calculate the level you would be with a given quantity of exp.
     * @param exp
     * @return 
     */
    public static double getLevelFromExp(double exp) {
        double epsilon = 1e-6;
        double lvl = 0.0;
        while (true) {
            double equationValue = (5.0 / 3.0) * Math.pow(lvl, 3) + (45.0 / 2.0) * Math.pow(lvl, 2) + (455.0 / 6.0) * lvl;

            if (Math.abs(equationValue - exp) < epsilon) {
                return lvl;
            }

            double derivativeValue = (5.0) * Math.pow(lvl, 2) + (45.0) * lvl + (455.0 / 6.0);
            lvl -= (equationValue - exp) / derivativeValue;
        }
    }


    /**
     * This method is used to calculate the experience that the user needs to get level up.
     * <p>
     * So if the user is level 1 with 175 exp and to get level 2 needs a total of 255 experience, this method will return 80.
     * @param lvl
     * @param exp
     * @return 
     */
    public static int getExpToLvlUp(int lvl, int exp){
        if(lvl == 1 && exp < 100)
            lvl = 0;
        return (exp - getExpToReachLvlFromZero(lvl));
    }

    public static int getLvlUpPercentage(int lvl, int exp) {
        return Math.round((float)ExpSystem.getExpToLvlUp(lvl, exp)/(float)(getExpToReachLvl(lvl))*100);
    }

    /**
     * This method is used to add the experience to the user.
     * <p> If the user is not in the database, it will be added. If the user has enough experience to level up, it will be leveled up and the method will
     * return the new level. If the user is not leveled up, it will return -1.
     * @param userId
     * @param guildId
     * @return
     * int
     */
    public int addExp(String userId, String guildId, double modifer) {
        ResultRow expData = DatabaseHandler.getExp(guildId, userId);
        if (expData.emptyValues() && !DatabaseHandler.addExpData(guildId, userId)) 
            return NOT_LEVELED_UP;
        
        int exp = expData.getAsInt("exp") + Math.round((float) ((double) getRandomExp() * modifer));
        int lvl = expData.getAsInt("level");
        int msg = expData.getAsInt("messages") + 1;

        int newLvl = (int) getLevelFromExp(exp);

        if (newLvl > lvl) {
            DatabaseHandler.updateExp(guildId, userId, exp, newLvl, msg);
            return newLvl;
        }
        DatabaseHandler.updateExp(guildId, userId, exp,  msg);
        return NOT_LEVELED_UP;
    }
}