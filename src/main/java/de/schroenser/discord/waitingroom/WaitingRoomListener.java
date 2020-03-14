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
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.ResumedEvent;
import net.dv8tion.jda.core.events.StatusChangeEvent;
import net.dv8tion.jda.core.events.guild.GuildReadyEvent;
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
    private static final String WAITING_CHANNEL_NAME = "\uD83C\uDFAC Twitch-Gulag";
    private static final String LIVE_CHANNEL_NAME = "\uD83D\uDD34\uD83C\uDFAC Twitch-Stream";

    private final String guildName;
    private final WaitingRoom waitingRoom = new WaitingRoom();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    private ReusableMessage reusableMessage;
    private ScheduledFuture<?> cleanStaleMembersTask;

    @Override
    public void onGuildReady(GuildReadyEvent event)
    {
        Guild guild = event.getGuild();
        if (guild.getName().equals(guildName))
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
        return guild.getTextChannelsByName(REPORTING_CHANNEL_NAME, false).get(0);
    }

    private void deleteBotMessages(TextChannel reportingChannel)
    {
        MessageHistory messageHistory = reportingChannel.getHistory();

        StreamSupport.stream(MessageHistorySpliterator.split(messageHistory), false)
            .filter(message -> message.getMember().equals(message.getGuild().getSelfMember()))
            .forEach(message -> message.delete().complete());
    }

    private void cleanStaleMembers()
    {
        updateMessage(waitingRoom.cleanStaleMembers());
    }

    @Override
    public void onResume(ResumedEvent event)
    {
        syncMembers(event.getJDA().getGuildsByName(guildName, false).get(0));
    }

    private void syncMembers(Guild guild)
    {
        List<Member> currentlyWaitingMembers = getCurrentlyWaitingMembers(guild);
        List<Member> currentlyLiveMembers = getCurrentlyLiveMembers(guild);
        updateMessage(waitingRoom.sync(currentlyWaitingMembers, currentlyLiveMembers));
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

    private boolean isWaitingChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(WAITING_CHANNEL_NAME);
    }

    private boolean isLiveChannel(VoiceChannel voiceChannel)
    {
        return voiceChannel.getName().equals(LIVE_CHANNEL_NAME);
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

    private List<Member> getCurrentlyWaitingMembers(Guild guild)
    {
        VoiceChannel waitingChannel = guild.getVoiceChannelsByName(WAITING_CHANNEL_NAME, false).get(0);
        return waitingChannel.getMembers();
    }

    private List<Member> getCurrentlyLiveMembers(Guild guild)
    {
        VoiceChannel waitingChannel = guild.getVoiceChannelsByName(LIVE_CHANNEL_NAME, false).get(0);
        return waitingChannel.getMembers();
    }
}