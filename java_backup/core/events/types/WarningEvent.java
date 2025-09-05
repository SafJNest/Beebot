package com.safjnest.core.events.types;

import javax.annotation.Nonnull;

import com.safjnest.model.guild.MemberData;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;

public class WarningEvent extends GenericChannelEvent {

    private final MemberData memberData;
    private final int warningId;
    private final String reason;

    public WarningEvent(@Nonnull JDA api, long responseNumber, @Nonnull Channel channel, MemberData memberData, int warningId, String reason) {
        super(api, responseNumber, channel);
        
        this.memberData = memberData;
        
        this.reason = reason;
        this.warningId = warningId;
    }

    public MemberData getMemberData() {
        return memberData;
    }

    public int getWarningId() {
        return warningId;
    }

    public String getReason() {
        return reason;
    }
    
}
