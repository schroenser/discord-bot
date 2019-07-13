package de.schroenser.discord.bot;

import javax.security.auth.login.LoginException;

import de.schroenser.discord.waitingroom.WaitingRoomListener;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;

public class Bot
{
    public Bot(String token, String guild)
    {
        JDABuilder jdaBuilder = new JDABuilder(AccountType.BOT).setToken(token);
        jdaBuilder.addEventListener(new WaitingRoomListener(guild));
        try
        {
            jdaBuilder.build();
        }
        catch (LoginException e)
        {
            throw new IllegalArgumentException("The provided token is invalid");
        }
    }
}