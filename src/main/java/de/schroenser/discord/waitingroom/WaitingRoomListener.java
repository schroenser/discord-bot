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

import de.schroenser.discord.util.MessageHistorySpliterator;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.StatusChangeEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.PermissionUtil;

@Slf4j
@RequiredArgsConstructor
public class WaitingRoomListener extends ListenerAdapter
{
    private static final String REPORTING_CHANNEL_NAME = "twitch-ticketschalter";
    private static final String WAITING_CHANNEL_NAME = "\uD83C\uDFAC Twitch-Wartezimmer";
    private static final String LIVE_CHANNEL_NAME = "\uD83D\uDD34\uD83C\uDFAC Twitch-Stream";

    private final String guildName;
    private final WaitingRoom waitingRoom = new WaitingRoom();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    private ReusableMessage reusableMessage;
    private ScheduledFuture<?> cleanStaleMembersTask;

    @Override
    public void onStatusChange(StatusChangeEvent event)
    {
        JDA jda = event.getEntity();
        JDA.Status newStatus = event.getNewStatus();
        if (newStatus == JDA.Status.CONNECTED)
        {
            reusableMessage = new ReusableMessage(getReportingChannel(jda));
            deleteBotMessages(jda);
            addInitialMembers(event.getEntity());
            cleanStaleMembersTask = scheduledExecutorService.scheduleAtFixedRate(this::cleanStaleMembers,
                0,
                5,
                TimeUnit.SECONDS);
        }
        else if (newStatus == JDA.Status.SHUTTING_DOWN)
        {
            cleanStaleMembersTask.cancel(true);
            updateMessage(Collections.emptyList());
        }
    }

    private void deleteBotMessages(JDA jda)
    {
        MessageHistory messageHistory = getReportingChannel(jda).getHistory();

        StreamSupport.stream(MessageHistorySpliterator.split(messageHistory), false)
            .filter(message -> message.getMember().equals(message.getGuild().getSelfMember()))
            .forEach(message -> message.delete().complete());
    }

    private TextChannel getReportingChannel(JDA jda)
    {
        return jda.getGuildsByName(guildName, false).get(0).getTextChannelsByName(REPORTING_CHANNEL_NAME, false).get(0);
    }

    private void addInitialMembers(JDA jda)
    {
        Guild guild = jda.getGuildsByName(guildName, false).get(0);
        VoiceChannel waitingChannel = guild.getVoiceChannelsByName(WAITING_CHANNEL_NAME, false).get(0);
        waitingChannel.getMembers().forEach(waitingRoom::join);
    }

    private void cleanStaleMembers()
    {
        updateMessage(waitingRoom.cleanStaleMembers());
    }

    @Override
    public void onGuildVoiceJoin(GuildVoiceJoinEvent event)
    {
        if (isWaitingChannel(event.getChannelJoined()))
        {
            updateMessage(waitingRoom.join(event.getMember()));
        }
        if (isLiveChannel(event.getChannelJoined()))
        {
            updateMessage(waitingRoom.call(event.getMember()));
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event)
    {
        if (isWaitingChannel(event.getChannelLeft()) || isLiveChannel(event.getChannelLeft()))
        {
            updateMessage(waitingRoom.leave(event.getMember()));
        }
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event)
    {
        if (isWaitingChannel(event.getChannelJoined()))
        {
            updateMessage(waitingRoom.join(event.getMember()));
        }
        if (isWaitingChannel(event.getChannelLeft()))
        {
            if (isLiveChannel(event.getChannelJoined()))
            {
                updateMessage(waitingRoom.call(event.getMember()));
            }
            else
            {
                updateMessage(waitingRoom.leave(event.getMember()));
            }
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        if (event.getMessage().getMentionedMembers().contains(event.getGuild().getSelfMember()))
        {
            log.debug("Received message {}", event.getMessage().getContentRaw());
            handleBotCommand(event);
        }
    }

    private void handleBotCommand(GuildMessageReceivedEvent event)
    {
        if (event.getMessage().getContentRaw().toLowerCase().contains("wartezimmer mischen"))
        {
            handleShuffleWaitingRoomCommand(event);
        }
        else
        {
            event.getChannel().sendMessage(String.format("Hä, <@%d>?", event.getAuthor().getIdLong())).complete();
        }
    }

    private void handleShuffleWaitingRoomCommand(GuildMessageReceivedEvent event)
    {
        if (PermissionUtil.checkPermission(event.getMember(), Permission.VOICE_MOVE_OTHERS))
        {
            event.getChannel()
                .sendMessage(String.format("Wie du befiehlst, <@%d>!", event.getAuthor().getIdLong()))
                .complete();
            log.debug("Shuffling waiting room");
            updateMessage(waitingRoom.reset());
        }
        else
        {
            event.getChannel()
                .sendMessage(String.format("Hahaha...NEIN! Du hast mir gar nichts zu befehlen, <@%d>.",
                    event.getAuthor().getIdLong()))
                .complete();
            log.debug("Insufficient permissions");
        }
    }

    private boolean isWaitingChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(WAITING_CHANNEL_NAME);
    }

    private boolean isLiveChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(LIVE_CHANNEL_NAME);
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
            result.append(i + 1).append(". ").append(waitingMember.getName());
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
}