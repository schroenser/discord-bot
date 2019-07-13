package de.schroenser.discord;

import lombok.extern.slf4j.Slf4j;

import de.schroenser.discord.bot.Bot;

@Slf4j
public class Main
{
    public static void main(String[] args)
    {
        try
        {
            verifyArguments(args);
            String token = args[0];
            String guild = args[1];
            new Bot(token, guild);
        }
        catch (Exception e)
        {
            log.error("Exception", e);
        }
    }

    private static void verifyArguments(String[] args)
    {
        if (args.length != 2)
        {
            throw new IllegalArgumentException("Please provide the Discord App token and guild");
        }
    }
}