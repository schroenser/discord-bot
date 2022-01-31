create table waiting_member (
    member_id long primary key,
    grace_leaves int not null,
    join_timestamp timestamp not null,
    leave_timestamp timestamp,
    call_timestamp timestamp
);