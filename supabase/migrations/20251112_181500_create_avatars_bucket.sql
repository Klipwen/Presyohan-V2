-- Create a public storage bucket for user avatars and necessary policies
do $$
begin
  if not exists (select 1 from storage.buckets where name = 'avatars') then
    insert into storage.buckets (name, public)
    values ('avatars', true);
  else
    update storage.buckets set public = true where name = 'avatars';
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Public read avatars'
  ) then
    create policy "Public read avatars"
      on storage.objects
      for select
      to public
      using (bucket_id = 'avatars');
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Authenticated insert avatars'
  ) then
    create policy "Authenticated insert avatars"
      on storage.objects
      for insert
      to authenticated
      with check (bucket_id = 'avatars');
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Authenticated update avatars'
  ) then
    create policy "Authenticated update avatars"
      on storage.objects
      for update
      to authenticated
      using (bucket_id = 'avatars')
      with check (bucket_id = 'avatars');
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Authenticated delete avatars'
  ) then
    create policy "Authenticated delete avatars"
      on storage.objects
      for delete
      to authenticated
      using (bucket_id = 'avatars');
  end if;
end $$;