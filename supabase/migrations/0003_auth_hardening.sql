-- Migration: 0003_auth_hardening.sql
-- Phase 2 of FITTER-BACKEND-TASK.md v2
-- Closes P1 defects: tenant defaults, analytics auth, eaten_at index, week-1 user_meta.

-- ─── 1. water_intake: add auth.uid() default on user_id ────────────────────────
-- (PK already references auth.users; just adding default so INSERT without user_id works)
alter table public.water_intake
  alter column user_id set default auth.uid();

-- ─── 2. profiles: add auth.uid() default on user_id ───────────────────────────
-- profiles.user_id is the PK; add default so upsert without explicit user_id works
alter table public.profiles
  alter column user_id set default auth.uid();

-- ─── 3. analytics_events: add auth.uid() default + harden insert policy ────────
alter table public.analytics_events
  alter column user_id set default auth.uid();

-- Drop the old policy that allowed user_id IS NULL (anonymous escape hatch)
drop policy if exists "insert own events" on public.analytics_events;

-- New policy: authenticated users may only insert rows tied to their own auth.uid()
create policy "insert own events" on public.analytics_events
  for insert
  with check (user_id = auth.uid());

-- ─── 4. meals: additional eaten_at index (v2 spec; complements 0001's meals_user_date_idx) ──
create index if not exists meals_user_eaten_idx
  on public.meals (user_id, eaten_at desc);

-- ─── 5. user_meta: track first_install_date for server-side Week-1 allowance ────────────
-- Auto-inserted on first consume_scan; worker can read to compute allowance server-side.
create table if not exists public.user_meta (
  user_id           uuid primary key references auth.users(id) on delete cascade,
  first_install_date timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

alter table public.user_meta enable row level security;

drop policy if exists "own user_meta" on public.user_meta;
create policy "own user_meta" on public.user_meta
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Allow service_role to read/write (Worker uses service_role to compute week-1 allowance)
-- service_role bypasses RLS by default in Supabase — no explicit grant needed.

-- ─── 6. extend get_scan_quota to accept p_user_id and return first_install_date ─
drop function if exists public.get_scan_quota(int);
drop function if exists public.get_scan_quota(int, uuid);
create or replace function public.get_scan_quota(
  p_allowance int default 3,
  p_user_id uuid default auth.uid()
)
returns jsonb
language plpgsql
security definer
set search_path = public
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
  insert into public.user_meta (user_id)
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
  left join public.scan_quota sq
    on sq.user_id = v_target_user_id and sq.day = current_date
  left join public.user_meta um
    on um.user_id = v_target_user_id;

  return v_result;
end;
$$;

revoke all on function public.get_scan_quota(int, uuid) from public;
grant execute on function public.get_scan_quota(int, uuid) to authenticated;
grant execute on function public.get_scan_quota(int, uuid) to service_role;

-- ─── 7. consume_scan: support p_user_id for service_role worker calls ─────────
drop function if exists public.consume_scan(int);
drop function if exists public.consume_scan(uuid, int);
create or replace function public.consume_scan(
  p_user_id uuid default auth.uid(),
  p_allowance int default 3
)
returns boolean
language plpgsql
security definer
set search_path = public
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
  insert into public.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  -- Atomically consume one scan and return whether within allowance
  insert into public.scan_quota (user_id, day, used)
  values (v_target_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = scan_quota.used + 1;

  select (sq.used <= p_allowance + sq.bonus)
  into v_result
  from public.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_result, true);
end;
$$;

revoke all on function public.consume_scan(uuid, int) from public;
grant execute on function public.consume_scan(uuid, int) to authenticated;
grant execute on function public.consume_scan(uuid, int) to service_role;

-- ─── 8. grant_bonus_scan: support p_user_id for service_role calls ────────────
drop function if exists public.grant_bonus_scan(int);
drop function if exists public.grant_bonus_scan(int, uuid);
create or replace function public.grant_bonus_scan(
  p_amount int default 1,
  p_user_id uuid default auth.uid()
)
returns int
language plpgsql
security definer
set search_path = public
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

  insert into public.scan_quota (user_id, day, bonus)
  values (v_target_user_id, current_date, p_amount)
  on conflict (user_id, day) do update set bonus = scan_quota.bonus + p_amount;

  select sq.bonus
  into v_new_bonus
  from public.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_new_bonus, p_amount);
end;
$$;

revoke all on function public.grant_bonus_scan(int, uuid) from public;
grant execute on function public.grant_bonus_scan(int, uuid) to authenticated;
grant execute on function public.grant_bonus_scan(int, uuid) to service_role;

-- ─── 9. Revoke all function access from anon (belt + suspenders) ──────────────
revoke all on function public.get_scan_quota(int, uuid)   from anon;
revoke all on function public.consume_scan(uuid, int)     from anon;
revoke all on function public.grant_bonus_scan(int, uuid) from anon;
