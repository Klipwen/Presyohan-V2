-- Ensure rename_or_merge_category stores target name in uppercase

create or replace function public.rename_or_merge_category(
  p_store_id uuid,
  p_category_id uuid,
  p_new_name text
)
returns table(category_id uuid, store_id uuid, name text)
language plpgsql
as $$
declare
  v_trimmed text;
  v_upper text;
  v_existing_id uuid;
begin
  v_trimmed := trim(coalesce(p_new_name, ''));
  if v_trimmed = '' then
    raise exception 'Category name is required';
  end if;
  v_upper := upper(v_trimmed);

  select c.id into v_existing_id
  from public.categories c
  where c.store_id = p_store_id
    and lower(c.name) = lower(v_upper)
  limit 1;

  if v_existing_id is not null and v_existing_id <> p_category_id then
    update public.products
      set category_id = v_existing_id
      where store_id = p_store_id
        and category_id = p_category_id;

    delete from public.categories
      where id = p_category_id
        and store_id = p_store_id;

    return query
      select c.id, c.store_id, c.name
      from public.categories c
      where c.id = v_existing_id;
    return;
  end if;

  update public.categories
    set name = v_upper
    where id = p_category_id
      and store_id = p_store_id;

  return query
    select c.id, c.store_id, c.name
    from public.categories c
    where c.id = p_category_id;
end;
$$;