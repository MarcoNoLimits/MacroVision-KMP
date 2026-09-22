create table if not exists public.analytics_events (
  id         bigint generated always as identity primary key,
  user_id    uuid references auth.users(id) on delete set null,
  device_id  text,
  event      text not null,
  payload    jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists analytics_events_user_idx on public.analytics_events (user_id, created_at desc);

alter table public.analytics_events enable row level security;

-- insert-only for clients; select restricted to service_role
drop policy if exists "insert own events" on public.analytics_events;
create policy "insert own events" on public.analytics_events for insert
  with check (user_id is null or user_id = auth.uid());
