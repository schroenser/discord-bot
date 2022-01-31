package de.schroenser.discord.waitingroom;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import org.jooq.generated.tables.records.WaitingMemberRecord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.ResumedEvent;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;

@Slf4j
public class WaitingRoomListener extends ListenerAdapter
{
    private final WaitingRoomConfigurationLoader waitingRoomConfigurationLoader;
    private final ScheduledFuture<?> cleanStaleMembersTask;

    @Inject
    protected WaitingRoomListener(
        WaitingRoomConfigurationLoader waitingRoomConfigurationLoader,
        ScheduledExecutorService scheduledExecutorService,
        JDA jda)
    {
        this.waitingRoomConfigurationLoader = waitingRoomConfigurationLoader;
        cleanStaleMembersTask = scheduledExecutorService.scheduleAtFixedRate(() -> cleanStaleMembers(jda),
            0,
            5,
            TimeUnit.SECONDS);
    }

    private void cleanStaleMembers(JDA jda)
    {
        updateMessage(waitingRoom.cleanStaleMembers());
    }

    @Override
    public void onGuildReady(GuildReadyEvent event)
    {
        waitingRoomConfigurationLoader.byGuildId(event.getGuild()
            .getIdLong())
            .forEach(this::syncMembers);
    }

    @Override
    public void onResumed(ResumedEvent event)
    {
        waitingRoomConfigurationLoader.all()
            .forEach(this::syncMembers);
    }

    private void syncMembers(WaitingRoomConfiguration waitingRoomConfiguration)
    {
        List<Member> currentlyWaitingMembers = waitingRoomConfiguration.getWaitingChannel()
            .getMembers();
        List<Member> currentlyLiveMembers = waitingRoomConfiguration.getLiveChannel()
            .getMembers();
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

    private boolean isWaitingChannel(AudioChannel audioChannel)
    {
        return audioChannel.getIdLong() == WAITING_CHANNEL_ID;
    }

    private boolean isLiveChannel(AudioChannel audioChannel)
    {
        return audioChannel.getIdLong() == LIVE_CHANNEL_ID;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getMessage()
            .getMentionedMembers()
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

    private void updateMessage(List<WaitingMemberRecord> waitingMembers)
    {
        String message = createMessage(guild, waitingChannelId, waitingMembers);

        reusableMessage.setText(message);
    }

    private String createMessage(Guild guild, Long waitingChannelId, List<WaitingMemberRecord> waitingMembers)
    {
        StringBuilder result = new StringBuilder();

        String waitingChannelName = guild.getTextChannelById(waitingChannelId)
            .getName();

        result.append(waitingChannelName)
            .append("\n")
            .append("\n");

        for (int i = 0; i < waitingMembers.size(); i++)
        {
            if (i > 0)
            {
                result.append("\n");
            }
            WaitingMemberRecord waitingMember = waitingMembers.get(i);
            boolean hasLeft = waitingMember.getLeaveTimestamp() != null;
            boolean wasCalled = waitingMember.getCallTimestamp() != null;
            String name = guild.getMemberById(waitingMember.getMemberId())
                .getEffectiveName();
            if (hasLeft)
            {
                result.append("~~");
            }
            if (wasCalled)
            {
                result.append("**");
            }
            result.append(i + 1)
                .append(". ")
                .append(name);
            if (wasCalled)
            {
                result.append("**");
            }
            if (hasLeft)
            {
                result.append("~~");
            }
        }

        return result.toString();
    }
}