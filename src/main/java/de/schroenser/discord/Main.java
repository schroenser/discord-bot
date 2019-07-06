package de.schroenser.discord;

import javax.security.auth.login.LoginException;

import lombok.extern.slf4j.Slf4j;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;

@Slf4j
public class Main
{
    public static void main(String[] args) throws LoginException
    {
        if (args.length == 1)
        {
            String token = args[0];
            new JDABuilder(AccountType.BOT).setToken(token).build();
        }
        else
        {
            log.error("Please provide the Discord App token as first parameter");
        }
    }
}