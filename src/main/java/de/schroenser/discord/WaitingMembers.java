package de.schroenser.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.dv8tion.jda.core.entities.Member;

@Slf4j
@RequiredArgsConstructor
public class WaitingMembers
{
    private static final Duration MUSTER_GRACE_DURATION = Duration.ofSeconds(15);
    private static final Duration LEAVE_GRACE_DURATION = Duration.ofMinutes(15);

    private final Object semaphore = new Object();
    private final Map<Member, WaitingMember> waitingMembers = new HashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);

    private final Consumer<List<WaitingMember>> onChange;

    public void join(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.compute(member, (key, value) -> {
                WaitingMember result;
                if (value == null)
                {
                    result = WaitingMember.builder().member(member).joined(Instant.now()).build();
                }
                else
                {
                    result = value.toBuilder().left(null).mustered(null).build();
                }
                return result;
            });
            raiseOnChange();
        }
    }

    public void muster(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(member,
                (key, value) -> value.toBuilder().left(null).mustered(Instant.now()).build());
            raiseOnChange();
            scheduledExecutorService.schedule(this::cleanStaleMembers,
                MUSTER_GRACE_DURATION.getSeconds() + 1,
                TimeUnit.SECONDS);
        }
    }

    public void leave(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(member,
                (key, value) -> value.toBuilder().left(Instant.now()).mustered(null).build());
            raiseOnChange();
            scheduledExecutorService.schedule(this::cleanStaleMembers,
                LEAVE_GRACE_DURATION.getSeconds() + 1,
                TimeUnit.SECONDS);
        }
    }

    private void cleanStaleMembers()
    {
        synchronized (semaphore)
        {
            Set<Member> members = ImmutableSet.copyOf(waitingMembers.keySet());
            members.forEach(member -> waitingMembers.compute(member, (key, value) -> {
                WaitingMember result = value;
                if (value.getMustered() != null &&
                    Instant.now().isAfter(value.getMustered().plus(MUSTER_GRACE_DURATION)) ||
                    value.getLeft() != null && Instant.now().isAfter(value.getLeft().plus(LEAVE_GRACE_DURATION)))
                {
                    result = null;
                }
                return result;
            }));
            raiseOnChange();
        }
    }

    private void raiseOnChange()
    {
        synchronized (semaphore)
        {
            onChange.accept(waitingMembers.values().stream().sorted().collect(ImmutableList.toImmutableList()));
        }
    }
}