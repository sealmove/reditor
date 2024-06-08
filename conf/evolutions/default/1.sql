-- Initialize

-- !Ups
create table if not exists files (
    id serial primary key,
    file_name varchar(255) not null,
    file_text text,
    user_lock uuid,
    ts timestamp with time zone default current_timestamp
);

-- !Downs
drop table if exists files;