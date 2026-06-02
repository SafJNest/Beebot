package com.safjnest.lol.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.safjnest.core.events.EventButtonHandler;
import com.safjnest.core.events.EventUtils;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.utils.SafJNest;
import com.safjnest.core.cache.managers.UserCache;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public class LeagueEventHandler extends EventButtonHandler {

    private record LeagueContext(String puuid, String region, String user_id, LeagueMessageParameter parameter, boolean userIdFallback) {
        LeagueContext with(String puuid, String region) {
            return new LeagueContext(puuid, region, user_id, parameter, userIdFallback);
        }
    }

    @Override
    public void onGenericComponentInteractionCreate(GenericComponentInteractionCreateEvent event) {
        if (!event.getComponentId().startsWith(LeagueMessage.BUTTON_ID_PREFIX)) return;

        List<Button> buttons = EventUtils.getButtons(event);
        LeagueContext context = getContext(buttons, event.getUser().getId());

        LeagueContext result = switch (event.getComponentType()) {
            case BUTTON -> button((ButtonInteractionEvent) event, buttons, context);
            case STRING_SELECT -> stringSelect((StringSelectInteractionEvent) event, buttons, context);
            default -> context;
        };

        if (result == null) return;
        event.deferEdit().queue();
        dispatch(event.getHook(), buttons, result);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        List<Button> buttons = EventUtils.getButtons(event.getMessage().getComponents());
        LeagueContext context = getContext(buttons, event.getUser().getId());

        LeagueContext result = switch (event.getModalId().split("-", 2)[0]) {
            case "champion" -> champion(event, context);
            default -> null;
        };

        if (result == null) return;
        event.deferEdit().queue();
        dispatch(event.getHook(), buttons, result);
    }

    // ---- core ----

    private LeagueContext getContext(List<Button> buttons, String fallbackUserId) {
        LeagueMessageParameter parameter = new LeagueMessageParameter(buttons);

        ButtonData lolCenterData = buttons.stream()
            .filter(b -> b.getCustomId().startsWith(LeagueMessage.BUTTON_ID_PREFIX + "-center-"))
            .findFirst()
            .map(b -> getButtonData(b, 3))
            .orElse(new ButtonData("#", "1#EUW1", false));

        if (lolCenterData.value2().isEmpty()) lolCenterData = new ButtonData("#", "1#EUW1", false);

        String puuid = lolCenterData.value2().trim().split("#")[0];
        String region = lolCenterData.value2().trim().split("#")[1];

        String user_id = LeagueService.getUserIdByLOLAccountId(puuid, LeagueShard.valueOf(region));
        if (user_id == null || user_id.isEmpty()) user_id = fallbackUserId;

        return new LeagueContext(puuid, region, user_id, parameter, lolCenterData.active());
    }

    private void dispatch(InteractionHook hook, List<Button> buttons, LeagueContext context) {
        boolean hasLeft = buttons.stream().anyMatch(b -> (LeagueMessage.BUTTON_ID_PREFIX + "-left").equals(b.getCustomId()));
        String user_id = (hasLeft || context.userIdFallback()) ? context.user_id() : "";
        Summoner s = LeagueService.getSummonerByPuuid(context.puuid(), LeagueShard.valueOf(context.region()));
        int summonerId = s != null ? LeagueService.getSummonerIdByPuuid(s.getPUUID(), s.getPlatform()) : 0;
        LeagueMessage.send(hook, user_id, s, summonerId, context.parameter());
    }

    // ---- handlers ----

    private LeagueContext button(ButtonInteractionEvent event, List<Button> buttons, LeagueContext context) {
        ButtonData data = getButtonData(event.getButton());
        String args = data.value1().trim();
        String content = data.value2().trim();
        boolean active = data.active();
        String puuid = context.puuid();
        String region = context.region();
        LeagueMessageParameter parameter = context.parameter();

        HashMap<String, String> accounts = UserCache.getUser(context.user_id()).getRiotAccounts();
        int index = 0;
        for (String k : accounts.keySet()) {
            if (k.equals(puuid)) break;
            index++;
        }

        switch (args) {
            case "center", "right" -> {
                index = (index + 1) == accounts.size() ? 0 : index + 1;
                puuid = (String) accounts.keySet().toArray()[index];
                region = accounts.get(puuid);
            }
            case "left" -> {
                index = index == 0 ? accounts.size() - 1 : index - 1;
                puuid = (String) accounts.keySet().toArray()[index];
                region = accounts.get(puuid);
            }
            case "queue" -> {
                parameter.setQueueType(!active ? GameQueueType.valueOf(content) : null);
                if (parameter.getQueueType() != null && !GameQueueTypeUtils.hasLane(parameter.getQueueType()))
                    parameter.setLaneType(null);
                parameter.setOffset(0);
            }
            case "lane" -> {
                parameter.setLaneType(!active ? LaneType.valueOf(content) : null);
                parameter.setOffset(0);
            }
            case "type" -> {
                parameter.setMessageType(LeagueMessageType.valueOf(content.toUpperCase()));
                if (parameter.getMessageType() == LeagueMessageType.OVERVIEW_CHAMPIONS) parameter.setShowChampion(false);
                parameter.setOffset(0);
            }
            case "season" -> {
                long[] time = switch (content) {
                    case "current" -> LeagueHandler.getCurrentSplitRange();
                    case "previous" -> LeagueHandler.getPreviousSplitRange();
                    default -> new long[] {0, 0};
                };
                parameter.setPeriod(time);
                parameter.setOffset(0);
            }
            case "champion" -> {
                parameter.setShowChampion(!active);
                parameter.setOffset(0);
            }
            case "change" -> {
                TextInput subject = TextInput.create("champion-change", TextInputStyle.SHORT)
                    .setPlaceholder("Champion name")
                    .setMaxLength(100)
                    .build();
                event.replyModal(
                    Modal.create("champion-change", "Select a champion")
                        .addComponents(Label.of("Select a champion", subject))
                        .build()
                ).queue();
                return null;
            }
            case "leftpage" -> parameter.setOffset(Math.max(0, parameter.getOffset() - parameter.getMessageType().getPageItem()));
            case "rightpage" -> parameter.setOffset(parameter.getOffset() + parameter.getMessageType().getPageItem());
            case "refresh" -> {
                LeagueHandler.clearSummonerCache(LeagueService.getSummonerByPuuid(puuid, LeagueShard.valueOf(region)));
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        return context.with(puuid, region);
    }

    private LeagueContext stringSelect(StringSelectInteractionEvent event, List<Button> buttons, LeagueContext context) {
        String args = event.getComponentId().split("-")[1];
        String value = event.getValues().isEmpty() ? null : event.getValues().get(0);
        LeagueMessageParameter parameter = context.parameter();

        switch (args) {
            case "shard" -> {
                parameter.setRegion(value == null ? null : LeagueShard.valueOf(value.toUpperCase()));
                parameter.setOffset(0);
            }
            case "tier" -> {
                parameter.setRank(value == null || value.equals("ALL") ? null : TierType.valueOf(value.toUpperCase()));
                parameter.setOffset(0);
            }
            case "opggselect" -> parameter.setMatch(
                LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI()
                    .getMatch(LeagueShard.valueOf(context.region()).toRegionShard(), value)
            );
            case "rankselect" -> {
                context = context.with(value.split("#")[0], value.split("#")[1]);
                context.parameter().setMessageType(LeagueMessageType.PROFILE);
            }
        }

        parameter.withComponents(EventUtils.getStringSelectMneu(event.getMessage().getComponents()));
        return context;
    }

    private LeagueContext champion(ModalInteractionEvent event, LeagueContext context) {
        String champoString = SafJNest.findSimilarWord(
            event.getValue("champion-change").getAsString(),
            new ArrayList<>(ChampionUtils.getChampionsNames())
        );
        StaticChampion newChampion = ChampionUtils.getChampion(champoString);

        if (newChampion == null) {
            event.deferReply().setEphemeral(true).addContent("Cannot find the champion you are looking for").queue();
            return null;
        }

        context.parameter().setChampion(newChampion);
        context.parameter().setShowChampion(true);
        return context;
    }
}
