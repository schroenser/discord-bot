package de.schroenser.discord;

import java.util.List;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.StatusChangeEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

@Slf4j
@RequiredArgsConstructor
public class TicketCounter extends ListenerAdapter
{
    private final String guildName;
    private final String reportingChannelName;
    private final String waitingChannelName;
    private final String liveChannelName;

    private final WaitingMembers waitingMembers = new WaitingMembers(this::updateMessage);

    private ReusableMessage message;

    @Override
    public void onStatusChange(StatusChangeEvent event)
    {
        switch (event.getNewStatus())
        {
            case CONNECTED:
                message = new ReusableMessage(getReportingChannel(event));
                deleteBotMessages(event);
                addInitialMembers(event);
                break;
            case SHUTTING_DOWN:
                deleteBotMessages(event);
                break;
        }
    }

    @Override
    public void onGuildVoiceJoin(GuildVoiceJoinEvent event)
    {
        if (isWaitingChannel(event.getChannelJoined()))
        {
            waitingMembers.join(event.getMember());
        }
        if (isLiveChannel(event.getChannelJoined()))
        {
            waitingMembers.muster(event.getMember());
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event)
    {
        if (isWaitingChannel(event.getChannelLeft()) || isLiveChannel(event.getChannelLeft()))
        {
            waitingMembers.leave(event.getMember());
        }
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event)
    {
        if (isWaitingChannel(event.getChannelJoined()))
        {
            waitingMembers.join(event.getMember());
        }
        if (isWaitingChannel(event.getChannelLeft()))
        {
            if (isLiveChannel(event.getChannelJoined()))
            {
                waitingMembers.muster(event.getMember());
            }
            else
            {
                waitingMembers.leave(event.getMember());
            }
        }
    }

    private void deleteBotMessages(StatusChangeEvent event)
    {
        MessageHistory messageHistory = getReportingChannel(event).getHistory();

        User bot = event.getEntity().getSelfUser();

        StreamSupport.stream(new MessageHistorySpliterator(messageHistory), false)
            .filter(message -> message.getAuthor().equals(bot))
            .forEach(message -> message.delete().complete());
    }

    private TextChannel getReportingChannel(StatusChangeEvent event)
    {
        return event.getEntity()
            .getGuildsByName(guildName, false)
            .get(0)
            .getTextChannelsByName(reportingChannelName, false)
            .get(0);
    }

    private void addInitialMembers(StatusChangeEvent event)
    {
        JDA jda = event.getEntity();
        Guild guild = jda.getGuildsByName(guildName, false).get(0);
        VoiceChannel waitingChannel = guild.getVoiceChannelsByName(this.waitingChannelName, false).get(0);
        waitingChannel.getMembers().forEach(waitingMembers::join);
    }

    private boolean isWaitingChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(waitingChannelName);
    }

    private boolean isLiveChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(liveChannelName);
    }

    private void updateMessage(List<WaitingMember> waitingMembers)
    {
        String result = "";
        for (int i = 0; i < waitingMembers.size(); i++)
        {
            WaitingMember waitingMember = waitingMembers.get(i);
            if (waitingMember.getLeft() != null)
            {
                result += "~~";
            }
            if (waitingMember.getMustered() != null)
            {
                result += "**";
            }
            result += (i + 1) + ". " + waitingMember.getMember().getEffectiveName();
            if (waitingMember.getMustered() != null)
            {
                result += "**";
            }
            if (waitingMember.getLeft() != null)
            {
                result += "~~";
            }
            result += "\n";
        }

        message.setText(result);
    }
}