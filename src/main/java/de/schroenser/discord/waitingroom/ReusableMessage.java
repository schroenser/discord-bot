package de.schroenser.discord.waitingroom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Slf4j
@RequiredArgsConstructor
class ReusableMessage
{
    private final Object semaphore = new Object();

    private final TextChannel channel;

    private Message message;

    void setText(String text)
    {
        synchronized (semaphore)
        {
            if (Strings.isNullOrEmpty(text))
            {
                if (message != null)
                {
                    log.debug("Removing message");
                    message.delete()
                        .complete();
                    message = null;
                }
            }
            else
            {
                if (message == null)
                {
                    log.debug("Creating message with\n{}", text);
                    message = channel.sendMessage(text)
                        .complete();
                }
                else
                {
                    String currentText = message.getContentRaw();
                    if (!text.equals(currentText))
                    {
                        log.debug("Updating message text\n{}\nwith\n{}", currentText, text);
                        message = message.editMessage(text)
                            .complete();
                    }
                }
            }
        }
    }
}
