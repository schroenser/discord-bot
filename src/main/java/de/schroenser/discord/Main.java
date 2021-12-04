package de.schroenser.discord;

import lombok.extern.slf4j.Slf4j;

import com.google.inject.Guice;
import net.dv8tion.jda.api.JDA;

@Slf4j
public class Main
{
    public static void main(String[] args)
    {
        try
        {
            Guice.createInjector(new ArgumentsModule(args), new JDAModule(), new EventListenerModule())
                .getInstance(JDA.class);
        }
        catch (Exception e)
        {
            log.error("Exception", e);
        }
    }
}
