package com.safjnest.lol.model.record;

public enum RecordMetric {
    KILLS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    DEATHS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    ASSISTS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    FIRST_KILL_TIME(RecordOwner.PARTICIPANT, RecordOrder.LOWEST, true),
    FIRST_BLOOD_TIME(RecordOwner.PARTICIPANT, RecordOrder.LOWEST, true),
    PENTAKILLS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    CS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    DAMAGE_DEALT(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    DAMAGE_TAKEN(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, false),
    BARON_KILLS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, true),
    ELDER_KILLS(RecordOwner.PARTICIPANT, RecordOrder.HIGHEST, true),
    FIRST_DRAKE_TIME(RecordOwner.TEAM, RecordOrder.LOWEST, true),
    FIRST_BARON_TIME(RecordOwner.TEAM, RecordOrder.LOWEST, true),
    FIRST_ELDER_TIME(RecordOwner.TEAM, RecordOrder.LOWEST, true),
    BARONS_TAKEN(RecordOwner.TEAM, RecordOrder.HIGHEST, true),
    ELDERS_TAKEN(RecordOwner.TEAM, RecordOrder.HIGHEST, true),
    LONGEST_GAME(RecordOwner.MATCH, RecordOrder.HIGHEST, false);

    private final RecordOwner owner;
    private final RecordOrder order;
    private final boolean requiresEvents;

    RecordMetric(RecordOwner owner, RecordOrder order, boolean requiresEvents) {
        this.owner = owner;
        this.order = order;
        this.requiresEvents = requiresEvents;
    }

    public RecordOwner owner() {
        return owner;
    }

    public RecordOrder order() {
        return order;
    }

    public boolean requiresEvents() {
        return requiresEvents;
    }

    public boolean gameShared() {
        return owner != RecordOwner.PARTICIPANT;
    }
}
