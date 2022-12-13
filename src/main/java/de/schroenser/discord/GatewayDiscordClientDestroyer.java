package de.schroenser.discord;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import discord4j.core.GatewayDiscordClient;

@Component
@RequiredArgsConstructor
public class GatewayDiscordClientDestroyer implements DisposableBean
{
    private final GatewayDiscordClient gatewayDiscordClient;

    @Override
    public void destroy()
    {
        gatewayDiscordClient.logout()
            .block();
    }
}