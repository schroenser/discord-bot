package de.schroenser.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
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

    private final List<Member> waitingMembers = new CopyOnWriteArrayList<>();

    private ReusableMessage message;

    @Override
    public void onStatusChange(StatusChangeEvent event)
    {
        switch (event.getNewStatus())
        {
            case CONNECTED:
                message = new ReusableMessage(getReportingChannel(event));
                deleteBotMessages(event);
                randomizeInitialMembers(event);
                message.setText(createMessageContent());
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
            registerJoin(event.getMember());
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event)
    {
        if (isWaitingChannel(event.getChannelLeft()))
        {
            deregisterJoin(event.getMember());
        }
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event)
    {
        if (isWaitingChannel(event.getChannelJoined()))
        {
            registerJoin(event.getMember());
        }
        if (isWaitingChannel(event.getChannelLeft()))
        {
            deregisterJoin(event.getMember());
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

    private void randomizeInitialMembers(StatusChangeEvent event)
    {
        JDA jda = event.getEntity();
        Guild guild = jda.getGuildsByName(guildName, false).get(0);
        VoiceChannel waitingChannel = guild.getVoiceChannelsByName(this.waitingChannelName, false).get(0);
        ArrayList<Member> currentMembers = new ArrayList<>(waitingChannel.getMembers());
        Collections.shuffle(currentMembers);
        waitingMembers.addAll(currentMembers);
    }

    private boolean isWaitingChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(waitingChannelName);
    }

    private void registerJoin(Member member)
    {
        waitingMembers.add(member);
        message.setText(createMessageContent());
    }

    private void deregisterJoin(Member member)
    {
        waitingMembers.remove(member);
        message.setText(createMessageContent());
    }

    private String createMessageContent()
    {
        String result = "";
        for (int i = 0; i < waitingMembers.size(); i++)
        {
            result += (i + 1) + ". " + waitingMembers.get(i).getEffectiveName() + "\n";
        }
        return result;
    }
}