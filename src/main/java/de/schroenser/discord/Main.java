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
        if (args.length == 2)
        {
            String token = args[0];
            String guild = args[1];
            String reportingChannelName = "twitch-ticketschalter";
            String waitingChannelName = "\uD83C\uDFAC Twitch-Wartezimmer";
            TicketCounter ticketCounter = new TicketCounter(guild, reportingChannelName, waitingChannelName);
            new JDABuilder(AccountType.BOT).setToken(token).addEventListener(ticketCounter).build();
        }
        else
        {
            log.error("Please provide the Discord App token and guild");
        }
    }
}