-- ─────────────────────────────────────────────────────────────────────────────
-- 0008 — Server-side account deletion and rewarded-ad claims
--
-- The analyze-meal edge function calls both functions with the service_role key,
-- passing the user id it took from the verified access token.
--
-- 1. fitter.delete_account_data(p_user_id)
--    Deletes every FitCal row for the user in one transaction (Play account-deletion
--    requirement, GDPR Art. 17), then reports whether the auth user can also be
--    deleted. This Supabase project hosts other apps whose tables cascade from
--    auth.users, so deleting the login of someone who also uses another app would
--    erase that app's data. The function returns false when any non-FitCal table
--    (or a storage object) still references the user; the gateway then keeps the
--    login and only the FitCal data is gone.
--
-- 2. fitter.claim_reward_bonus(p_user_id, p_amount, p_daily_cap)
--    Adds rewarded-ad bonus scans for today, atomically capped. Reading the bonus and
--    then calling grant_bonus_scan would let parallel requests overshoot the cap.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function fitter.delete_account_data(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  fk record;
  v_referenced boolean;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'delete_account_data is restricted to service_role'
      using errcode = '42501';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  delete from fitter.meals where user_id = p_user_id;
  delete from fitter.water_intake where user_id = p_user_id;
  delete from fitter.scan_quota where user_id = p_user_id;
  -- analytics_events is ON DELETE SET NULL, so remove it explicitly.
  delete from fitter.analytics_events where user_id = p_user_id;
  delete from fitter.profiles where user_id = p_user_id;
  delete from fitter.user_meta where user_id = p_user_id;

  -- Is the login used by another app on this project?
  for fk in
    select c.conrelid::regclass as tbl, a.attname as col
    from pg_constraint c
    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where c.contype = 'f'
      and c.confrelid = 'auth.users'::regclass
      and array_length(c.conkey, 1) = 1
      and n.nspname not in ('auth', 'fitter', 'storage')
  loop
    execute format('select exists (select 1 from %s where %I = $1)', fk.tbl, fk.col)
      into v_referenced
      using p_user_id;
    if v_referenced then
      return false;
    end if;
  end loop;

  if exists (select 1 from storage.objects where owner_id = p_user_id::text) then
    return false;
  end if;

  return true;
end;
$$;

revoke all on function fitter.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function fitter.delete_account_data(uuid) to service_role;

create or replace function fitter.claim_reward_bonus(
  p_user_id uuid,
  p_amount int,
  p_daily_cap int
)
returns int
language plpgsql
security definer
set search_path = fitter, public
as $$
declare
  v_amount int;
  v_cap int;
  v_new_bonus int;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'claim_reward_bonus is restricted to service_role'
      using errcode = '42501';
  end if;
  if p_user_id is null then
    raise exception 'User ID must not be null';
  end if;

  v_amount := least(greatest(coalesce(p_amount, 0), 0), 5);
  v_cap := least(greatest(coalesce(p_daily_cap, 0), 0), 50);
  if v_amount = 0 then
    return null;
  end if;

  -- The row lock taken by ON CONFLICT serialises concurrent claims for the same day.
  insert into fitter.scan_quota (user_id, day, bonus)
  values (p_user_id, current_date, least(v_amount, v_cap))
  on conflict (user_id, day)
  do update set bonus = least(fitter.scan_quota.bonus + v_amount, v_cap)
    where fitter.scan_quota.bonus < v_cap
  returning bonus into v_new_bonus;

  -- null = cap already reached, nothing granted.
  return v_new_bonus;
end;
$$;

revoke all on function fitter.claim_reward_bonus(uuid, int, int) from public, anon, authenticated;
grant execute on function fitter.claim_reward_bonus(uuid, int, int) to service_role;
