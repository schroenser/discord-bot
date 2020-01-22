package de.schroenser.discord.waitingroom;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.dv8tion.jda.core.entities.Member;

@Slf4j
@RequiredArgsConstructor
class WaitingRoom
{
    private static final Duration CALL_GRACE_DURATION = Duration.ofSeconds(15);
    private static final Duration LEAVE_GRACE_DURATION = Duration.ofMinutes(15);
    private static final int GRACE_LEAVES = 1;

    private final Object semaphore = new Object();
    private final Map<Member, WaitingMember> waitingMembers = new HashMap<>();

    List<WaitingMember> join(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.compute(member, (key, value) -> {
                WaitingMember result;
                if (value == null)
                {
                    result = WaitingMember.builder()
                        .member(member)
                        .joined(Instant.now())
                        .graceLeaves(GRACE_LEAVES)
                        .build();
                    log.debug("Added new member {}", result.getName());
                }
                else
                {
                    result = value.toBuilder().left(null).called(null).build();
                    log.debug("Removed flags for member {}", result.getName());
                }
                return result;
            });
            List<WaitingMember> result = getSortedMembers();
            log.debug("Current waiting list: {}", result);
            return result;
        }
    }

    List<WaitingMember> call(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(member, (key, value) -> {
                WaitingMember result = value.toBuilder().left(null).called(Instant.now()).build();
                log.debug("Called member {}", result.getName());
                return result;
            });
            List<WaitingMember> result = getSortedMembers();
            log.debug("Current waiting list: {}", result);
            return result;
        }
    }

    List<WaitingMember> leave(Member member)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(member, (key, value) -> {
                WaitingMember newValue = null;
                int graceLeaves = value.getGraceLeaves();
                if (graceLeaves > 0)
                {
                    newValue = value.toBuilder().left(Instant.now()).called(null).graceLeaves(graceLeaves - 1).build();
                    log.debug("Member {} left with grace", newValue.getName());
                }
                else
                {
                    log.debug("Member {} left without grace", value.getName());
                }
                return newValue;
            });
            List<WaitingMember> result = getSortedMembers();
            log.debug("Current waiting list: {}", result);
            return result;
        }
    }

    List<WaitingMember> cleanStaleMembers()
    {
        synchronized (semaphore)
        {
            Set<Member> members = ImmutableSet.copyOf(waitingMembers.keySet());
            members.forEach(member -> waitingMembers.computeIfPresent(member, (key, value) -> {
                WaitingMember result = value;
                if (isCallGraceDurationExpired(value) || isLeaveGraceDurationExpired(value))
                {
                    log.debug("Member {} exceeded grace period and is removed", value.getName());
                    result = null;
                }
                return result;
            }));
            return getSortedMembers();
        }
    }

    List<WaitingMember> reset()
    {
        synchronized (semaphore)
        {
            log.debug("Reset initialized");
            List<Member> members = new ArrayList<>(waitingMembers.keySet());
            Collections.shuffle(members);
            Instant now = Instant.now();
            waitingMembers.replaceAll((key, value) -> value.toBuilder()
                .joined(now.minus(members.indexOf(key), ChronoUnit.SECONDS))
                .graceLeaves(GRACE_LEAVES)
                .build());
            log.debug("Reset complete");
            return getSortedMembers();
        }
    }

    private boolean isCallGraceDurationExpired(WaitingMember value)
    {
        return value.getCalled() != null && Instant.now().isAfter(value.getCalled().plus(CALL_GRACE_DURATION));
    }

    private boolean isLeaveGraceDurationExpired(WaitingMember value)
    {
        return value.getLeft() != null && Instant.now().isAfter(value.getLeft().plus(LEAVE_GRACE_DURATION));
    }

    private List<WaitingMember> getSortedMembers()
    {
        synchronized (semaphore)
        {
            return waitingMembers.values()
                .stream()
                .sorted(Comparator.comparing(WaitingMember::getJoined))
                .collect(ImmutableList.toImmutableList());
        }
    }
}