package de.schroenser.discord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;

@Configuration
public class Discord4JConfiguration
{
    @Bean
    public <T extends Event> GatewayDiscordClient gatewayDiscordClient(@Value("${token}") String token)
    {
        return DiscordClientBuilder.create(token)
            .build()
            .login()
            .block();
    }
}