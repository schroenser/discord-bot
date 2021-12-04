package de.schroenser.discord;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class ArgumentsModule extends AbstractModule
{
    private final String token;
    private final String guild;

    public ArgumentsModule(String[] args)
    {
        verifyArguments(args);
        token = args[0];
        guild = args[1];
    }

    @Override
    protected void configure()
    {
        bind(String.class).annotatedWith(Names.named("token"))
            .toInstance(token);
        bind(String.class).annotatedWith(Names.named("guild"))
            .toInstance(guild);
    }

    private static void verifyArguments(String[] args)
    {
        if (args.length != 2)
        {
            throw new IllegalArgumentException("Please provide the Discord App token and guild");
        }
    }
}
