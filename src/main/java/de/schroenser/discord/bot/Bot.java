package de.schroenser.discord.bot;

import javax.security.auth.login.LoginException;

import de.schroenser.discord.waitingroom.WaitingRoomListener;
import net.dv8tion.jda.api.JDABuilder;

public class Bot
{
    public Bot(String token, String guild)
    {
        try
        {
            JDABuilder.createDefault(token)
                .addEventListeners(new WaitingRoomListener(guild))
                .build();
        }
        catch (LoginException e)
        {
            throw new IllegalArgumentException("The provided token is invalid");
        }
    }
}