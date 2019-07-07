package de.schroenser.discord;

import lombok.RequiredArgsConstructor;

import com.google.common.base.Strings;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

@RequiredArgsConstructor
public class ReusableMessage
{
    private final Object semaphore = new Object();

    private final TextChannel channel;

    private Message message;

    public void setText(String text)
    {
        synchronized (semaphore)
        {
            if (Strings.isNullOrEmpty(text))
            {
                if (message != null)
                {
                    message.delete().complete();
                    message = null;
                }
            }
            else
            {
                if (message == null)
                {
                    message = channel.sendMessage(text).complete();
                }
                else
                {
                    message = message.editMessage(text).complete();
                }
            }
        }
    }
}