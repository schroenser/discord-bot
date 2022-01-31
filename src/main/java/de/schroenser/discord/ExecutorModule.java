package de.schroenser.discord;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.RequiredArgsConstructor;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;

@RequiredArgsConstructor
public class ExecutorModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        ListeningScheduledExecutorService executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(
            10));
        bind(Executor.class).toInstance(executor);
        bind(ExecutorService.class).toInstance(executor);
        bind(ScheduledExecutorService.class).toInstance(executor);
        bind(ListeningExecutorService.class).toInstance(executor);
        bind(ListeningScheduledExecutorService.class).toInstance(executor);
    }
}
