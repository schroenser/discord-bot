package de.schroenser.discord.bot;

import de.schroenser.discord.waitingroom.WaitingRoomListener;
import net.dv8tion.jda.api.JDABuilder;

public class Bot
{
    public Bot(String token, String guild)
    {
        JDABuilder.createDefault(token)
            .addEventListeners(new WaitingRoomListener(guild))
            .build();
    }
}
