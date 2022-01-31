create table waiting_room_configuration (
    waiting_channel_id long primary key,
    live_channel_id long not null unique,
    guild_id long not null,
    reporting_channel_id long not null,
    reporting_message_id long not null
);