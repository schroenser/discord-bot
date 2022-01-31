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

@Slf4j
@RequiredArgsConstructor
class WaitingRoom
{
    private static final Duration CALL_GRACE_DURATION = Duration.ofSeconds(15);
    private static final Duration LEAVE_GRACE_DURATION = Duration.ofMinutes(15);
    private static final int GRACE_LEAVES = 1;

    private final Object semaphore = new Object();
    private final Map<Long, WaitingMember> waitingMembers = new HashMap<>();

    List<WaitingMember> join(Long memberId)
    {
        synchronized (semaphore)
        {
            waitingMembers.compute(memberId, (key, value) -> {
                WaitingMember result;
                if (value == null)
                {
                    result = WaitingMember.builder()
                        .memberId(memberId)
                        .joined(Instant.now())
                        .graceLeaves(GRACE_LEAVES)
                        .build();
                    log.debug("Added new member {}", result.getMemberId());
                }
                else
                {
                    int graceLeaves = value.getGraceLeaves();
                    if (value.getLeft() != null)
                    {
                        graceLeaves--;
                    }
                    result = value.toBuilder()
                        .left(null)
                        .called(null)
                        .graceLeaves(graceLeaves)
                        .build();
                    log.debug("Removed flags for member {}", result.getMemberId());
                }
                return result;
            });
            List<WaitingMember> result = getSortedMembers();
            log.debug("Current waiting list: {}", result);
            return result;
        }
    }

    List<WaitingMember> call(Long memberId)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(memberId, (key, value) -> {
                WaitingMember result = value.toBuilder()
                    .left(null)
                    .called(Instant.now())
                    .build();
                log.debug("Called member {}", result.getMemberId());
                return result;
            });
            List<WaitingMember> result = getSortedMembers();
            log.debug("Current waiting list: {}", result);
            return result;
        }
    }

    List<WaitingMember> leave(Long memberId)
    {
        synchronized (semaphore)
        {
            waitingMembers.computeIfPresent(memberId, (key, value) -> {
                WaitingMember newValue = null;
                if (value.getGraceLeaves() > 0)
                {
                    newValue = value.toBuilder()
                        .left(Instant.now())
                        .called(null)
                        .build();
                    log.debug("Member {} left with grace", newValue.getMemberId());
                }
                else
                {
                    log.debug("Member {} left without grace", value.getMemberId());
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
            Set<Long> memberIds = ImmutableSet.copyOf(waitingMembers.keySet());
            memberIds.forEach(id -> waitingMembers.computeIfPresent(id, (key, value) -> {
                WaitingMember result = value;
                if (isCallGraceDurationExpired(value) || isLeaveGraceDurationExpired(value))
                {
                    log.debug("Member {} exceeded grace period and is removed", value.getMemberId());
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
            List<Long> memberIds = new ArrayList<>(waitingMembers.keySet());
            Collections.shuffle(memberIds);
            Instant now = Instant.now();
            waitingMembers.replaceAll((key, value) -> value.toBuilder()
                .joined(now.minus(memberIds.indexOf(key), ChronoUnit.SECONDS))
                .graceLeaves(GRACE_LEAVES)
                .build());
            log.debug("Reset complete");
            return getSortedMembers();
        }
    }

    List<WaitingMember> sync(List<Long> currentlyWaitingMemberIds, List<Long> currentlyLiveMemberIds)
    {
        synchronized (semaphore)
        {
            log.debug("Sync initialized");
            currentlyWaitingMemberIds.forEach(this::join);
            currentlyLiveMemberIds.forEach(this::call);
            waitingMembers.keySet()
                .stream()
                .filter(memberId -> !currentlyWaitingMemberIds.contains(memberId))
                .filter(memberId -> !currentlyLiveMemberIds.contains(memberId))
                .forEach(this::leave);
            log.debug("Sync complete");
            return getSortedMembers();
        }
    }

    private boolean isCallGraceDurationExpired(WaitingMember value)
    {
        return value.getCalled() != null &&
            Instant.now()
                .isAfter(value.getCalled()
                    .plus(CALL_GRACE_DURATION));
    }

    private boolean isLeaveGraceDurationExpired(WaitingMember value)
    {
        return value.getLeft() != null &&
            Instant.now()
                .isAfter(value.getLeft()
                    .plus(LEAVE_GRACE_DURATION));
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