create table if not exists user_video (
    id uuid primary key default gen_random_uuid(),
    name text not null default '',
    path text not null default '',
    created_at timestamptz not null default now(),
    created_by varchar(255) not null default 'SYSTEM',
    updated_at timestamptz not null default now(),
    updated_by varchar(255) not null default 'SYSTEM',
    is_active boolean not null default false
)