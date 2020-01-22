package de.schroenser.discord.waitingroom;

import java.time.Instant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import net.dv8tion.jda.core.entities.Member;

@Value
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class WaitingMember
{
    @EqualsAndHashCode.Include
    private final Member member;

    private final int graceLeaves;
    private final Instant joined;
    private final Instant left;
    private final Instant called;

    boolean hasLeft()
    {
        return getLeft() != null;
    }

    boolean wasCalled()
    {
        return getCalled() != null;
    }

    String getName()
    {
        return getMember().getEffectiveName();
    }

    @Override
    public String toString()
    {
        String result = getName();
        if (hasLeft())
        {
            result += ":left";
        }
        if (wasCalled())
        {
            result += ":called";
        }
        result += ":" + graceLeaves;
        return result;
    }
}