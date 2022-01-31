package de.schroenser.discord.waitingroom;

import lombok.Value;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

@Value
class WaitingRoomConfiguration
{
    VoiceChannel waitingChannel;
    VoiceChannel liveChannel;
    Guild guild;
    TextChannel reportingChannel;
    Message reportingMessage;
}