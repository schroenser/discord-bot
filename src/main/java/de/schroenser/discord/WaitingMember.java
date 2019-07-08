package de.schroenser.discord;

import java.time.Instant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.core.entities.Member;

@Value
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WaitingMember implements Comparable<WaitingMember>
{
    @EqualsAndHashCode.Include
    private final Member member;

    private final int graceLeaves;
    private final Instant joined;
    private final Instant left;
    private final Instant mustered;

    @Override
    public int compareTo(@NotNull WaitingMember o)
    {
        return getJoined().compareTo(o.getJoined());
    }
}