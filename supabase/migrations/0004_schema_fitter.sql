-- Migration: 0004_schema_fitter.sql
-- Phase 2b of FITTER-BACKEND-TASK.md (Dedicated fitter schema DB)
-- Moves all app tables out of public into the dedicated 'fitter' schema.

create schema if not exists fitter;

-- ─── 1. Idempotently move existing tables from public to fitter ──────────────
do $$
declare
  tbl text;
begin
  for tbl in select unnest(array['scan_quota', 'meals', 'profiles', 'water_intake', 'analytics_events', 'user_meta'])
  loop
    if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = tbl)
       and not exists (select 1 from information_schema.tables where table_schema = 'fitter' and table_name = tbl) then
      execute format('alter table public.%I set schema fitter;', tbl);
    end if;
  end loop;
end $$;

-- ─── 2. Declarative definitions (in case of fresh schema deployment) ──────────
create table if not exists fitter.scan_quota (
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  day     date not null default current_date,
  used    int  not null default 0,
  bonus   int  not null default 0,
  primary key (user_id, day)
);

create table if not exists fitter.meals (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade default auth.uid(),
  name       text not null,
  calories   int  not null,
  protein_g  real not null,
  carbs_g    real not null,
  fat_g      real not null,
  eaten_at   timestamptz not null default now(),
  photo_url  text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists fitter.profiles (
  user_id            uuid primary key references auth.users(id) on delete cascade default auth.uid(),
  weight             real,
  height             real,
  cal_goal           int,
  protein_goal       int,
  carbs_goal         int,
  fat_goal           int,
  age                int,
  gender             text,
  goal_type          text,
  default_plate_size real,
  updated_at         timestamptz not null default now()
);

create table if not exists fitter.water_intake (
  user_id   uuid not null references auth.users(id) on delete cascade default auth.uid(),
  day       date not null,
  amount_ml int  not null default 0,
  primary key (user_id, day)
);

create table if not exists fitter.analytics_events (
  id         bigint generated always as identity primary key,
  user_id    uuid references auth.users(id) on delete set null default auth.uid(),
  device_id  text,
  event      text not null,
  payload    jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists fitter.user_meta (
  user_id            uuid primary key references auth.users(id) on delete cascade default auth.uid(),
  first_install_date timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

-- Ensure indexes
create index if not exists meals_user_date_idx on fitter.meals (user_id, eaten_at desc);
create index if not exists meals_user_eaten_idx on fitter.meals (user_id, eaten_at desc);
create index if not exists analytics_events_user_idx on fitter.analytics_events (user_id, created_at desc);

-- Ensure auth.uid() defaults exist on all user_id columns
alter table fitter.scan_quota alter column user_id set default auth.uid();
alter table fitter.meals alter column user_id set default auth.uid();
alter table fitter.profiles alter column user_id set default auth.uid();
alter table fitter.water_intake alter column user_id set default auth.uid();
alter table fitter.analytics_events alter column user_id set default auth.uid();
alter table fitter.user_meta alter column user_id set default auth.uid();

-- ─── 3. Require ownership by postgres ─────────────────────────────────────────
alter table fitter.scan_quota owner to postgres;
alter table fitter.meals owner to postgres;
alter table fitter.profiles owner to postgres;
alter table fitter.water_intake owner to postgres;
alter table fitter.analytics_events owner to postgres;
alter table fitter.user_meta owner to postgres;

-- ─── 4. Row Level Security and Policies ───────────────────────────────────────
alter table fitter.scan_quota enable row level security;
alter table fitter.meals enable row level security;
alter table fitter.profiles enable row level security;
alter table fitter.water_intake enable row level security;
alter table fitter.analytics_events enable row level security;
alter table fitter.user_meta enable row level security;

drop policy if exists "own quota" on fitter.scan_quota;
create policy "own quota" on fitter.scan_quota
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own meals" on fitter.meals;
create policy "own meals" on fitter.meals
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own profile" on fitter.profiles;
create policy "own profile" on fitter.profiles
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own water" on fitter.water_intake;
create policy "own water" on fitter.water_intake
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "own user_meta" on fitter.user_meta;
create policy "own user_meta" on fitter.user_meta
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "insert own events" on fitter.analytics_events;
create policy "insert own events" on fitter.analytics_events
  for insert with check (user_id = auth.uid());

-- ─── 5. Minimal Grants ────────────────────────────────────────────────────────
-- Schema usage
grant usage on schema fitter to authenticated, anon, service_role;

-- Authenticated & service_role access to tables
grant select, insert, update, delete on all tables in schema fitter to authenticated, service_role;
grant usage, select on all sequences in schema fitter to authenticated, service_role;

-- Revoke all table, sequence, and function privileges from anon
revoke all on all tables in schema fitter from anon;
revoke all on all sequences in schema fitter from anon;
revoke all on all functions in schema fitter from anon;

-- Default privileges for future objects in schema fitter
alter default privileges in schema fitter grant select, insert, update, delete on tables to authenticated, service_role;
alter default privileges in schema fitter grant usage, select on sequences to authenticated, service_role;
alter default privileges in schema fitter revoke all on tables from anon;
alter default privileges in schema fitter revoke all on sequences from anon;
alter default privileges in schema fitter revoke all on functions from anon;

-- ─── 6. Stored Procedures in schema fitter ───────────────────────────────────

-- fitter.get_scan_quota
create or replace function fitter.get_scan_quota(
  p_allowance int default 3,
  p_user_id uuid default auth.uid()
)
returns jsonb
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_target_user_id uuid;
  v_result jsonb;
begin
  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  -- Ensure user_meta row exists (idempotent insert)
  insert into fitter.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  select jsonb_build_object(
    'used',               coalesce(sq.used, 0),
    'bonus',              coalesce(sq.bonus, 0),
    'remaining',          greatest(0, (p_allowance + coalesce(sq.bonus, 0)) - coalesce(sq.used, 0)),
    'first_install_date', to_char(um.first_install_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  )
  into v_result
  from (select 1) dummy
  left join fitter.scan_quota sq
    on sq.user_id = v_target_user_id and sq.day = current_date
  left join fitter.user_meta um
    on um.user_id = v_target_user_id;

  return v_result;
end;
$$;

revoke all on function fitter.get_scan_quota(int, uuid) from public, anon;
grant execute on function fitter.get_scan_quota(int, uuid) to authenticated, service_role;

-- fitter.consume_scan
create or replace function fitter.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_target_user_id uuid;
  v_result boolean;
begin
  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  -- Ensure user_meta row exists (records first install date)
  insert into fitter.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  -- Atomically consume one scan and return whether within allowance
  insert into fitter.scan_quota (user_id, day, used)
  values (v_target_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = scan_quota.used + 1;

  select (sq.used <= p_allowance + sq.bonus)
  into v_result
  from fitter.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_result, true);
end;
$$;

revoke all on function fitter.consume_scan(uuid, int) from public, anon;
grant execute on function fitter.consume_scan(uuid, int) to authenticated, service_role;

-- fitter.grant_bonus_scan
create or replace function fitter.grant_bonus_scan(
  p_amount int default 1,
  p_user_id uuid default auth.uid()
)
returns int
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_target_user_id uuid;
  v_new_bonus int;
begin
  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitter.scan_quota (user_id, day, bonus)
  values (v_target_user_id, current_date, p_amount)
  on conflict (user_id, day) do update set bonus = scan_quota.bonus + p_amount;

  select sq.bonus
  into v_new_bonus
  from fitter.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_new_bonus, p_amount);
end;
$$;

revoke all on function fitter.grant_bonus_scan(int, uuid) from public, anon;
grant execute on function fitter.grant_bonus_scan(int, uuid) to authenticated, service_role;

-- ─── 7. Public Forwarding Wrappers for Backward Compatibility ────────────────
create or replace function public.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language sql
security definer
set search_path = fitter, public
as $$
  select fitter.consume_scan(p_user_id, p_allowance);
$$;

revoke all on function public.consume_scan(uuid, int) from public, anon;
grant execute on function public.consume_scan(uuid, int) to authenticated, service_role;

create or replace function public.grant_bonus_scan(
  p_amount int default 1,
  p_user_id uuid default auth.uid()
)
returns int
language sql
security definer
set search_path = fitter, public
as $$
  select fitter.grant_bonus_scan(p_amount, p_user_id);
$$;

revoke all on function public.grant_bonus_scan(int, uuid) from public, anon;
grant execute on function public.grant_bonus_scan(int, uuid) to authenticated, service_role;

create or replace function public.get_scan_quota(
  p_allowance int default 3,
  p_user_id uuid default auth.uid()
)
returns jsonb
language sql
security definer
set search_path = fitter, public
as $$
  select fitter.get_scan_quota(p_allowance, p_user_id);
$$;

revoke all on function public.get_scan_quota(int, uuid) from public, anon;
grant execute on function public.get_scan_quota(int, uuid) to authenticated, service_role;
