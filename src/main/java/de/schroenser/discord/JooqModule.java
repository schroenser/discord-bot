package de.schroenser.discord;

import java.sql.Connection;

import lombok.RequiredArgsConstructor;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import com.google.inject.AbstractModule;

@RequiredArgsConstructor
public class JooqModule extends AbstractModule
{
    private final Connection connection;

    @Override
    protected void configure()
    {
        System.getProperties()
            .setProperty("org.jooq.no-logo", "true");
        System.getProperties()
            .setProperty("org.jooq.no-tips", "true");
        bind(DSLContext.class).toInstance(DSL.using(connection, SQLDialect.H2));
    }
}
