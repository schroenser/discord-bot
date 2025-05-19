package de.schroenser.discord.util;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageHistorySpliterator implements Spliterator<Message>
{
    private static final int BATCH_RETRIEVE_AMOUNT = 10;

    public static Spliterator<Message> split(MessageHistory messageHistory)
    {
        return new MessageHistorySpliterator(messageHistory);
    }

    private final MessageHistory messageHistory;

    private Iterator<Message> messages;

    @Override
    public boolean tryAdvance(Consumer<? super Message> action)
    {
        if (messages == null || !messages.hasNext())
        {
            messages = messageHistory.retrievePast(BATCH_RETRIEVE_AMOUNT)
                .complete()
                .iterator();
        }
        if (messages.hasNext())
        {
            action.accept(messages.next());
            return true;
        }
        else
        {
            return false;
        }
    }

    @Override
    public Spliterator<Message> trySplit()
    {
        return null;
    }

    @Override
    public long estimateSize()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics()
    {
        return Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE;
    }
}
