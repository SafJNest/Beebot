package com.safjnest.model.guild;

import java.util.List;
import java.util.Map;

import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertProblem;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.alert.TwitchData;

public record GuildSettings(String id, String name, String iconUrl, List<Map<String, Object>> channels,
        List<Map<String, Object>> roles, Map<String, Object> settings) {

    public record Error(String field, String code, String message) {}
    public record Result(String status, List<Error> errors, AlertState alert) {}

    public record AlertState(Integer id, boolean exists, boolean enabled, boolean valid,
            String message, String privateMessage, String channelId, String sendType, List<String> roleIds,
            String streamerId, String roleId, Integer level, Boolean temporary, List<AlertProblem> problems) {

        public static AlertState from(AlertData alert, boolean experienceEnabled) {
            if (alert == null)
                return new AlertState(null, false, false, false, null, null, null, null,
                    List.of(), null, null, null, null, List.of());

            List<AlertProblem> problems = alert.getProblems(experienceEnabled);
            return new AlertState(alert.getID(), true, alert.isEnabled(), problems.isEmpty(),
                alert.getMessage(), alert.getPrivateMessage(), alert.getChannelId(), alert.getSendType().name(),
                List.of(alert.getRolesAsArray()),
                alert instanceof TwitchData twitch ? twitch.getStreamer() : null,
                alert instanceof TwitchData twitch ? twitch.getStreamerRole() : null,
                alert instanceof RewardData reward ? reward.getLevel() : null,
                alert instanceof RewardData reward ? reward.isTemporary() : null, problems);
        }
    }
}
