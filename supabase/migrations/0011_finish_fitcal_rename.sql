-- ─────────────────────────────────────────────────────────────────────────────
-- 0011 — Finish the Fitter → FitCal rename
--
-- Problems this fixes (all found live on 2026-10-10):
-- 1. The app's Supabase client targets schema `fitcal`, but the Data API only
--    exposed `fitter, public`. Every direct app call (meal/profile/water sync,
--    quota sync) failed with PGRST106 "Invalid schema: fitcal".
-- 2. `fitcal` was only a layer of `select *` views over `fitter` (0006). Those
--    views froze the column list at creation time and added a second, untested
--    path for upserts.
-- 3. The gateway reads and writes an `entitlements` table that never existed, so a
--    RevenueCat purchase could never unlock Premium server-side.
-- 4. Leftover pre-rename wrappers public.consume_scan / get_scan_quota /
--    grant_bonus_scan sat in the shared `public` schema.
--
-- After this migration the data lives in schema `fitcal` (renamed in place, so
-- rows, RLS policies, grants and indexes are unchanged).
--
-- Order matters, because PostgREST fails for EVERY app on this project (PGRST002)
-- if a schema in its Exposed schemas list does not exist:
--   1. Dashboard → Project Settings → Data API → Exposed schemas: add `fitcal`
--      (it already exists as the old view layer) → "fitter, public, fitcal".
--   2. Apply this migration. It leaves an empty `fitter` placeholder so the list
--      stays valid. (The exposed list can only be changed from the dashboard;
--      `alter role authenticator` is reserved on hosted Supabase.)
--   3. Dashboard: remove `fitter` from Exposed schemas, then `drop schema fitter;`.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Remove the alias layer (6 views + 3 invoker wrappers; nothing else depends on it).
drop schema if exists fitcal cascade;

-- 2. Remove pre-rename wrappers from the shared public schema.
drop function if exists public.consume_scan(uuid, integer);
drop function if exists public.get_scan_quota(integer, uuid);
drop function if exists public.grant_bonus_scan(integer, uuid);

-- 3. Rename the data schema.
alter schema fitter rename to fitcal;

-- 4. Function bodies and search_path settings are stored as text: rewrite them.
--    (delete_account_data is recreated explicitly below.)
do $$
declare
  r record;
  def text;
begin
  for r in
    select p.oid
      from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'fitcal' and p.prokind = 'f'
       and p.proname <> 'delete_account_data'
       and (pg_get_functiondef(p.oid) like '%fitter%')
  loop
    def := replace(pg_get_functiondef(r.oid), 'fitter', 'fitcal');
    execute def;
  end loop;
end $$;

-- 5. Entitlements (written by the RevenueCat webhook through the gateway).
create table if not exists fitcal.entitlements (
  user_id        uuid not null references auth.users(id) on delete cascade,
  entitlement_id text not null,
  status         text not null check (status in ('active', 'expired')),
  updated_at     timestamptz not null default now(),
  primary key (user_id, entitlement_id)
);
alter table fitcal.entitlements enable row level security;
drop policy if exists "read own entitlements" on fitcal.entitlements;
create policy "read own entitlements" on fitcal.entitlements
  for select using (user_id = auth.uid());
revoke all on fitcal.entitlements from anon, authenticated;
grant select on fitcal.entitlements to authenticated;
grant all on fitcal.entitlements to service_role;

-- 6. Account deletion: same logic as 0009, with the schema name updated in the
--    "is this login used by another app?" check (otherwise FitCal's own tables
--    would count as another app and no login would ever be deleted).
create or replace function fitcal.delete_account_data(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = fitcal, public
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

  delete from fitcal.meals where user_id = p_user_id;
  delete from fitcal.water_intake where user_id = p_user_id;
  delete from fitcal.scan_quota where user_id = p_user_id;
  -- analytics_events is ON DELETE SET NULL, so remove it explicitly.
  delete from fitcal.analytics_events where user_id = p_user_id;
  delete from fitcal.entitlements where user_id = p_user_id;
  delete from fitcal.profiles where user_id = p_user_id;
  delete from fitcal.user_meta where user_id = p_user_id;
  if to_regclass('fitcal.feedback') is not null then
    execute 'delete from fitcal.feedback where user_id = $1' using p_user_id;
  end if;

  -- Is the login used by another app on this project?
  for fk in
    select c.conrelid::regclass::text as tbl, a.attname as col
    from pg_constraint c
    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where c.contype = 'f'
      and c.confrelid = 'auth.users'::regclass
      and array_length(c.conkey, 1) = 1
      and n.nspname not in ('auth', 'fitcal', 'storage')
  loop
    if fk.tbl = 'crm.profiles' then
      execute format(
        'select exists (select 1 from crm.profiles where %I = $1
           and not (role = ''member'' and avatar_url is null and updated_at = created_at))',
        fk.col)
        into v_referenced
        using p_user_id;
    else
      execute format('select exists (select 1 from %s where %I = $1)', fk.tbl, fk.col)
        into v_referenced
        using p_user_id;
    end if;
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

revoke all on function fitcal.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function fitcal.delete_account_data(uuid) to service_role;

-- 7. Empty placeholder so a still-exposed `fitter` doesn't break PostgREST (step 3 above).
create schema if not exists fitter;
comment on schema fitter is 'Empty placeholder after the rename to fitcal (0011). Drop once it is no longer an exposed schema.';
notify pgrst, 'reload schema';
