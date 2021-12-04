package de.schroenser.discord;

import java.util.Set;

import javax.security.auth.login.LoginException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.EventListener;

public class JDAModule extends AbstractModule
{
    @Provides
    public JDA provideJDA(@Named("token") String token, Set<EventListener> eventListeners)
    {
        try
        {
            return JDABuilder.createDefault(token)
                .addEventListeners(eventListeners.toArray())
                .build();
        }
        catch (LoginException e)
        {
            throw new IllegalArgumentException("The provided token is invalid");
        }
    }
}
