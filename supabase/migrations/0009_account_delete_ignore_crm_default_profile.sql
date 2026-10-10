-- ─────────────────────────────────────────────────────────────────────────────
-- 0009 — Account deletion: ignore the CRM profile the auth trigger auto-creates
--
-- The trigger on_auth_user_created (crm.handle_new_user) inserts a crm.profiles row
-- for EVERY new login, FitCal guests included. 0008 counted that row as "this login
-- is used by another app", so no FitCal login was ever deleted (found by the
-- end-to-end test). A crm.profiles row now counts only if someone actually uses the
-- CRM: a role other than the default 'member', an avatar, or a later edit.
-- Untouched default rows are removed by the auth.users cascade.
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
    select c.conrelid::regclass::text as tbl, a.attname as col
    from pg_constraint c
    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where c.contype = 'f'
      and c.confrelid = 'auth.users'::regclass
      and array_length(c.conkey, 1) = 1
      and n.nspname not in ('auth', 'fitter', 'storage')
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

revoke all on function fitter.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function fitter.delete_account_data(uuid) to service_role;
