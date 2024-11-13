package com.safjnest.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import com.safjnest.core.Bot;
import com.safjnest.model.guild.AutomatedAction;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.log.BotLogger;

public class AutomatedActionTimer {
    private static final long rescheduleTiming = TimeConstant.HOUR * 2;

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    private LocalDateTime nextReschedule; 

    public AutomatedActionTimer() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduledTasks = new HashMap<>();
        this.scheduler.scheduleAtFixedRate(getAAScheduler(), 0, rescheduleTiming, TimeUnit.MILLISECONDS);
    }

    public void scheduleAATask(LocalDateTime dateTime, String id, int actionId, String userId, String guildId) {
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        long delay = date.getTime() - Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()).getTime();

        if (delay < 0) {
            System.out.println("Task is in the past: " + dateTime);
            return;
        }
        else if (dateTime.isAfter(nextReschedule)) {
            System.out.println("Task is in the future: " + dateTime);
            return;
        }


        AutomatedAction action = Bot.getGuildData(guildId).getAction(actionId);
        if (action == null) {
            BotLogger.error("[AutomatedActionTimer] Action not found: " + actionId);
            return;
        }

        final User user = Bot.getJDA().getUserById(userId);
        final Guild guild = Bot.getJDA().getGuildById(guildId);

        Runnable task = () -> {
            switch (action.getAction()) {
                case 1:
                    guild.mute(user, false).queue(
                        success -> {},
                        failure -> failure.printStackTrace()
                    );
                    guild.removeRoleFromMember(user, guild.getRoleById(action.getRoleId())).queue(
                        success -> {},
                        failure -> failure.printStackTrace()
                    );
                    break;
                case 3:
                    guild.unban(user).queue(
                        success -> {},
                        failure -> failure.printStackTrace()
                    );
                    break;
            
                default:
                    BotLogger.error("[AutomatedActionTimer] Invalid action: " + action.getId());
                    break;
            }
            scheduledTasks.remove(id);
        };

        scheduledTasks.put(id, scheduler.schedule(task, delay, TimeUnit.MILLISECONDS));

        BotLogger.info("[AutomatedActionTimer] Scheduled task: " + id + " for " + dateTime);
    }

    private Runnable getAAScheduler() {
        return () -> {
            try {
                nextReschedule = LocalDateTime.now().plusNanos(rescheduleTiming * 1000 * 1000);
                QueryResult tasks = DatabaseHandler.getAutomatedActionsExpiring();
                BotLogger.info("[AutomatedActionTimer] Rescheduling tasks: " + tasks.size());
                for (ResultRow task : tasks) {
                    LocalDateTime dateTime = task.getAsLocalDateTime("time");
                    if (!scheduledTasks.containsKey(task.get("id"))) {
                        scheduleAATask(dateTime, task.get("id"), task.getAsInt("action_id"), task.get("user_id"), task.get("guild_id"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        };
    }
}
