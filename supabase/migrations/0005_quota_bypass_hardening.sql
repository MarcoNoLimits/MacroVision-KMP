-- ─────────────────────────────────────────────────────────────────────────────
-- 0005_quota_bypass_hardening.sql
--
-- SECURITY FIX — authenticated clients could grant themselves unlimited bonus
-- scans, completely defeating the daily scan quota and the Premium upgrade.
--
-- Root cause: grant_bonus_scan() was granted EXECUTE to `authenticated`. Inside
-- the function, any authenticated caller took the branch
--     v_target_user_id := auth.uid();
-- and then wrote an arbitrary p_amount into scan_quota.bonus. Any user could call
--     rpc('grant_bonus_scan', { p_amount: 999999 })
-- and then scan without limit — a direct revenue/business-logic bypass that also
-- drove unbounded VLM spend through the gateway.
--
-- Fix: a bonus grant is an AUTHORIZATION decision that only the trusted server may
-- make (revenue events, promotional campaigns, support compensation). This
-- migration:
--   1. Re-defines the functions with an explicit service_role guard (defence in
--      depth: even if a grant is re-added later, the function itself refuses).
--   2. Revokes EXECUTE from `authenticated`/`anon`/`public` on every schema copy.
--   3. Caps the amount so a compromised service path cannot inflate quota wildly.
--   4. Pins search_path to defeat SECURITY DEFINER search_path hijacking.
--
-- consume_scan() is NOT privileged: consuming your own quota is the normal client
-- operation and stays available to `authenticated`.
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── 1. consume_scan: keep client access, harden the amount bound ─────────────
-- Only change needed: bound p_allowance so a modified client cannot inflate its
-- own allowance argument to an arbitrarily large number.
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
  v_effective_allowance int;
begin
  -- Server-side ceiling. The Worker already clamps to 5; this makes the clamp
  -- authoritative in the database so a tampered client cannot exceed it.
  v_effective_allowance := least(greatest(coalesce(p_allowance, 0), 0), 5);

  if auth.role() = 'authenticated' then
    v_target_user_id := auth.uid();
  else
    v_target_user_id := coalesce(p_user_id, auth.uid());
  end if;

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitter.user_meta (user_id)
  values (v_target_user_id)
  on conflict (user_id) do nothing;

  insert into fitter.scan_quota (user_id, day, used)
  values (v_target_user_id, current_date, 1)
  on conflict (user_id, day) do update set used = fitter.scan_quota.used + 1;

  select (sq.used <= v_effective_allowance + sq.bonus)
  into v_result
  from fitter.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_result, true);
end;
$$;

revoke all on function fitter.consume_scan(uuid, int) from public, anon;
grant execute on function fitter.consume_scan(uuid, int) to authenticated, service_role;

-- ─── 2. grant_bonus_scan: service_role ONLY ───────────────────────────────────
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
  v_capped_amount int;
begin
  -- Hard authorization gate. There is no legitimate reason for a client-held JWT
  -- to mint quota. Refusing non-service roles here means the function is safe
  -- even if a future migration re-grants EXECUTE by mistake.
  if auth.role() is distinct from 'service_role' then
    raise exception 'grant_bonus_scan is restricted to service_role'
      using errcode = '42501';
  end if;

  -- Cap the grant so a bug or abuse in a server path cannot create absurd quota.
  v_capped_amount := least(greatest(coalesce(p_amount, 0), 0), 50);

  v_target_user_id := coalesce(p_user_id, auth.uid());

  if v_target_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  insert into fitter.scan_quota (user_id, day, bonus)
  values (v_target_user_id, current_date, v_capped_amount)
  on conflict (user_id, day)
  do update set bonus = fitter.scan_quota.bonus + v_capped_amount;

  select sq.bonus
  into v_new_bonus
  from fitter.scan_quota sq
  where sq.user_id = v_target_user_id and sq.day = current_date;

  return coalesce(v_new_bonus, v_capped_amount);
end;
$$;

revoke all on function fitter.grant_bonus_scan(int, uuid) from public, anon, authenticated;
grant execute on function fitter.grant_bonus_scan(int, uuid) to service_role;

-- ─── 3. Revoke the authenticated grant on the public-schema forwarders ────────
-- 0004 created public.* wrappers; these must match the same restriction or they
-- remain a live bypass path.
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

revoke all on function public.grant_bonus_scan(int, uuid) from public, anon, authenticated;
grant execute on function public.grant_bonus_scan(int, uuid) to service_role;

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

-- ─── 4. get_scan_quota: read-only, but ensure anon cannot call it ────────────
revoke all on function fitter.get_scan_quota(int, uuid) from public, anon;
revoke all on function public.get_scan_quota(int, uuid) from public, anon;
grant execute on function fitter.get_scan_quota(int, uuid) to authenticated, service_role;
grant execute on function public.get_scan_quota(int, uuid) to authenticated, service_role;

-- ─── 5. Defence in depth: no table should be writable by anon ─────────────────
revoke all on all tables in schema fitter from anon;
grant usage on schema fitter to authenticated, service_role;

-- Verification queries (run these after applying):
--
-- 1. Confirm grant_bonus_scan is NOT executable by authenticated:
--    select proname, proacl from pg_proc
--     where proname = 'grant_bonus_scan';
--    -- expect: service_role only in proacl, no authenticated
--
-- 2. Confirm anon has no table privileges:
--    select table_name, privilege_type from information_schema.role_table_grants
--     where grantee = 'anon' and table_schema = 'fitter';
--    -- expect: zero rows
