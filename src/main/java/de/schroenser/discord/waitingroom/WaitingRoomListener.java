package de.schroenser.discord.waitingroom;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import de.schroenser.discord.util.MessageHistorySpliterator;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;

@Slf4j
@RequiredArgsConstructor
public class WaitingRoomListener extends ListenerAdapter
{
    private static final long REPORTING_CHANNEL_ID = 597038690321039360L;
    private static final long WAITING_CHANNEL_ID = 330853284564959232L;
    private static final long LIVE_CHANNEL_ID = 334733504103579649L;

    private final String guildName;
    private final WaitingRoom waitingRoom = new WaitingRoom();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    private ReusableMessage reusableMessage;
    private ScheduledFuture<?> cleanStaleMembersTask;

    @Override
    public void onGuildReady(GuildReadyEvent event)
    {
        Guild guild = event.getGuild();
        if (guild.getName()
            .equals(guildName))
        {
            TextChannel reportingChannel = getReportingChannel(guild);
            reusableMessage = new ReusableMessage(reportingChannel);
            deleteBotMessages(reportingChannel);
            syncMembers(guild);
            cleanStaleMembersTask = scheduledExecutorService.scheduleAtFixedRate(this::cleanStaleMembers,
                0,
                5,
                TimeUnit.SECONDS);
        }
    }

    private TextChannel getReportingChannel(Guild guild)
    {
        return guild.getTextChannelById(REPORTING_CHANNEL_ID);
    }

    private void deleteBotMessages(TextChannel reportingChannel)
    {
        MessageHistory messageHistory = reportingChannel.getHistory();

        StreamSupport.stream(MessageHistorySpliterator.split(messageHistory), false)
            .filter(message -> message.getGuild()
                .getSelfMember()
                .equals(message.getMember()))
            .forEach(message -> message.delete()
                .complete());
    }

    private void cleanStaleMembers()
    {
        updateMessage(waitingRoom.cleanStaleMembers());
    }

    @Override
    public void onSessionResume(@NotNull SessionResumeEvent event)
    {
        syncMembers(event.getJDA()
            .getGuildsByName(guildName, false)
            .getFirst());
    }

    private void syncMembers(Guild guild)
    {
        List<Member> currentlyWaitingMembers = getCurrentlyWaitingMembers(guild);
        List<Member> currentlyLiveMembers = getCurrentlyLiveMembers(guild);
        updateMessage(waitingRoom.sync(currentlyWaitingMembers, currentlyLiveMembers));
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event)
    {
        AudioChannel channelJoined = event.getChannelJoined();
        AudioChannel channelLeft = event.getChannelLeft();
        Member member = event.getMember();

        if (isWaitingChannel(channelJoined))
        {
            updateMessage(waitingRoom.join(member));
        }

        if (isLiveChannel(channelJoined))
        {
            updateMessage(waitingRoom.call(member));
        }

        if (isWaitingChannel(channelLeft) && !isLiveChannel(channelJoined) ||
            isLiveChannel(channelLeft) && !isWaitingChannel(channelJoined))
        {
            updateMessage(waitingRoom.leave(member));
        }
    }

    private boolean isWaitingChannel(AudioChannel audioChannel)
    {
        return audioChannel != null && audioChannel.getIdLong() == WAITING_CHANNEL_ID;
    }

    private boolean isLiveChannel(AudioChannel audioChannel)
    {
        return audioChannel != null && audioChannel.getIdLong() == LIVE_CHANNEL_ID;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getMessage()
            .getMentions()
            .getMembers()
            .contains(event.getGuild()
                .getSelfMember()))
        {
            log.debug("Received message {}",
                event.getMessage()
                    .getContentRaw());
            handleBotCommand(event);
        }
    }

    private void handleBotCommand(MessageReceivedEvent event)
    {
        if (event.getMessage()
            .getContentRaw()
            .toLowerCase()
            .contains("wartezimmer mischen"))
        {
            handleShuffleWaitingRoomCommand(event);
        }
        else
        {
            event.getChannel()
                .sendMessage(String.format("Hä, <@%d>?",
                    event.getAuthor()
                        .getIdLong()))
                .complete();
        }
    }

    private void handleShuffleWaitingRoomCommand(MessageReceivedEvent event)
    {
        if (PermissionUtil.checkPermission(event.getMember(), Permission.VOICE_MOVE_OTHERS))
        {
            event.getChannel()
                .sendMessage(String.format("Wie du befiehlst, <@%d>!",
                    event.getAuthor()
                        .getIdLong()))
                .complete();
            log.debug("Shuffling waiting room");
            updateMessage(waitingRoom.reset());
        }
        else
        {
            event.getChannel()
                .sendMessage(String.format("Hahaha...NEIN! Du hast mir gar nichts zu befehlen, <@%d>.",
                    event.getAuthor()
                        .getIdLong()))
                .complete();
            log.debug("Insufficient permissions");
        }
    }

    @Override
    public void onStatusChange(StatusChangeEvent event)
    {
        if (event.getOldStatus() == JDA.Status.CONNECTED && event.getNewStatus() == JDA.Status.SHUTTING_DOWN)
        {
            cleanStaleMembersTask.cancel(true);
            updateMessage(Collections.emptyList());
        }
    }

    private void updateMessage(List<WaitingMember> waitingMembers)
    {
        reusableMessage.setText(createMessage(waitingMembers));
    }

    private String createMessage(List<WaitingMember> waitingMembers)
    {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < waitingMembers.size(); i++)
        {
            if (i > 0)
            {
                result.append("\n");
            }
            WaitingMember waitingMember = waitingMembers.get(i);
            if (waitingMember.hasLeft())
            {
                result.append("~~");
            }
            if (waitingMember.wasCalled())
            {
                result.append("**");
            }
            result.append(i + 1)
                .append(". ")
                .append(waitingMember.getName());
            if (waitingMember.wasCalled())
            {
                result.append("**");
            }
            if (waitingMember.hasLeft())
            {
                result.append("~~");
            }
        }

        return result.toString();
    }

    private List<Member> getCurrentlyWaitingMembers(Guild guild)
    {
        AudioChannel channel = guild.getVoiceChannelById(WAITING_CHANNEL_ID);
        return channel.getMembers();
    }

    private List<Member> getCurrentlyLiveMembers(Guild guild)
    {
        AudioChannel channel = guild.getVoiceChannelById(LIVE_CHANNEL_ID);
        return channel.getMembers();
    }
}
