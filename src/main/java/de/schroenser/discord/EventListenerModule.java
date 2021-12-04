package de.schroenser.discord;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import de.schroenser.discord.waitingroom.WaitingRoomListener;
import net.dv8tion.jda.api.hooks.EventListener;

public class EventListenerModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        Multibinder<EventListener> eventListenerBinder = Multibinder.newSetBinder(binder(), EventListener.class);
        eventListenerBinder.addBinding()
            .to(WaitingRoomListener.class);
    }
}
