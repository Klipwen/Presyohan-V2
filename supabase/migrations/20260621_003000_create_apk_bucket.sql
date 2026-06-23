-- Create a public storage bucket for APK releases and necessary policies
do $$
begin
  if not exists (select 1 from storage.buckets where name = 'presyohan.apk') then
    insert into storage.buckets (name, public)
    values ('presyohan.apk', true);
  else
    update storage.buckets set public = true where name = 'presyohan.apk';
  end if;
end $$;

-- Policies for public download read
do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Public read apk'
  ) then
    create policy "Public read apk"
      on storage.objects
      for select
      to public
      using (bucket_id = 'presyohan.apk');
  end if;
end $$;

-- Policies for administrative write (INSERT, UPDATE, DELETE)
do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects' and policyname = 'Admin all apk'
  ) then
    create policy "Admin all apk"
      on storage.objects
      for all
      to authenticated
      using (
        bucket_id = 'presyohan.apk' 
        and exists (
          select 1 from public.app_users 
          where id = auth.uid() and role = 'admin'
        )
      )
      with check (
        bucket_id = 'presyohan.apk' 
        and exists (
          select 1 from public.app_users 
          where id = auth.uid() and role = 'admin'
        )
      );
  end if;
end $$;
