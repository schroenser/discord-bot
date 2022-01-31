package de.schroenser.discord.waitingroom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;

@Slf4j
@RequiredArgsConstructor
class ReusableMessage
{
    private final Object semaphore = new Object();

    private final long channelId;

    private Long messageId;

    void setText(String text)
    {
        synchronized (semaphore)
        {
            if (Strings.isNullOrEmpty(text))
            {
                if (messageId != null)
                {
                    log.debug("Removing message");
                    messageId.delete()
                        .complete();
                    messageId = null;
                }
            }
            else
            {
                if (messageId == null)
                {
                    log.debug("Creating message with\n{}", text);
                    messageId = channelId.sendMessage(text)
                        .complete();
                }
                else
                {
                    String currentText = messageId.getContentRaw();
                    if (!text.equals(currentText))
                    {
                        log.debug("Updating message text\n{}\nwith\n{}", currentText, text);
                        messageId = messageId.editMessage(text)
                            .complete();
                    }
                }
            }
        }
    }
}