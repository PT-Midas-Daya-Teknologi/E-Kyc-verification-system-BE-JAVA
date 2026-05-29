create table if not exists public.admin_user (
    id uuid primary key default gen_random_uuid(),
    name varchar(255) not null,
    email varchar(255) not null unique,
    password varchar(255) not null,
    created_at timestamptz not null default now(),
    created_by varchar(255) not null default 'SYSTEM',
    updated_at timestamptz not null default now(),
    updated_by varchar(255) not null default 'SYSTEM',
    is_active boolean not null default false
);

insert into admin_user
(name, email, password )
values
('admin', 'admin@admin.com', '$2a$12$8S7xdMZPmw5g0oMs5Q6r5O9IAB0L/QoJLXwF1lUQmBdCzvB5zQrjy');