package de.schroenser.discord.waitingroom;

import java.util.stream.Stream;

import javax.inject.Inject;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import org.jooq.DSLContext;
import org.jooq.generated.tables.records.WaitingRoomConfigurationRecord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

@RequiredArgsConstructor(onConstructor = @__(@Inject), access = AccessLevel.PROTECTED)
class WaitingRoomConfigurationLoader
{
    private final DSLContext dslContext;
    private final JDA jda;

    public Stream<WaitingRoomConfiguration> all()
    {
        return dslContext.selectFrom(org.jooq.generated.tables.WaitingRoomConfiguration.WAITING_ROOM_CONFIGURATION)
            .fetchStream()
            .map(this::toWaitingRoomConfiguration)
            .flatMap(Stream::ofNullable);
    }

    public Stream<WaitingRoomConfiguration> byGuildId(Long id)
    {
        return dslContext.selectFrom(org.jooq.generated.tables.WaitingRoomConfiguration.WAITING_ROOM_CONFIGURATION)
            .where(org.jooq.generated.tables.WaitingRoomConfiguration.WAITING_ROOM_CONFIGURATION.GUILD_ID.eq(id))
            .fetchStream()
            .map(this::toWaitingRoomConfiguration)
            .flatMap(Stream::ofNullable);
    }

    private WaitingRoomConfiguration toWaitingRoomConfiguration(WaitingRoomConfigurationRecord waitingRoomConfigurationRecord)
    {
        Guild guild = jda.getGuildById(waitingRoomConfigurationRecord.getGuildId());

        VoiceChannel waitingChannel = null;
        VoiceChannel liveChannel = null;
        TextChannel reportingChannel = null;

        if (guild != null)
        {
            waitingChannel = guild.getVoiceChannelById(waitingRoomConfigurationRecord.getWaitingChannelId());
            liveChannel = guild.getVoiceChannelById(waitingRoomConfigurationRecord.getLiveChannelId());
            reportingChannel = guild.getTextChannelById(waitingRoomConfigurationRecord.getReportingChannelId());
        }

        WaitingRoomConfiguration result;
        if (guild == null || waitingChannel == null || liveChannel == null || reportingChannel == null)
        {
            waitingRoomConfigurationRecord.delete();
            result = null;
        }
        else
        {
            Message
                reportingMessage
                = reportingChannel.retrieveMessageById(waitingRoomConfigurationRecord.getReportingMessageId())
                .complete();

            if (reportingMessage == null)
            {
                reportingMessage = reportingChannel.sendMessage(waitingChannel.getName())
                    .complete();
                waitingRoomConfigurationRecord.setReportingMessageId(reportingMessage.getIdLong());
                waitingRoomConfigurationRecord.store();
            }

            result = new WaitingRoomConfiguration(waitingChannel,
                liveChannel,
                guild,
                reportingChannel,
                reportingMessage);
        }

        return result;
    }
}