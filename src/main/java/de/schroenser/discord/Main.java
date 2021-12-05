package de.schroenser.discord;

import java.sql.Connection;
import java.sql.DriverManager;

import lombok.extern.slf4j.Slf4j;

import org.flywaydb.core.Flyway;

import com.google.inject.Guice;
import net.dv8tion.jda.api.JDA;

@Slf4j
public class Main
{
    public static final String DATABASE_URL = "jdbc:h2:./discord-bot";

    public static void main(String[] args)
    {
        Flyway.configure()
            .dataSource(DATABASE_URL, null, null)
            .load()
            .migrate();

        try (Connection connection = DriverManager.getConnection(DATABASE_URL))
        {
            Guice.createInjector(new ArgumentsModule(args),
                new JooqModule(connection),
                new JDAModule(),
                new EventListenerModule())
                .getInstance(JDA.class);
        }
        catch (Exception e)
        {
            log.error("Exception", e);
        }
    }
}
