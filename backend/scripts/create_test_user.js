/*
  Create a confirmed test user using Supabase Admin API.
  - Requires env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
  - Provide email/password via args or env TEST_EMAIL, TEST_PASSWORD
  - Does NOT commit secrets; run locally only.
*/

const { createClient } = require('@supabase/supabase-js');

function getEnv(name) {
  const val = process.env[name];
  if (!val) {
    console.error(`Missing required env: ${name}`);
    process.exitCode = 1;
  }
  return val;
}

async function main() {
  const supabaseUrl = getEnv('SUPABASE_URL');
  const serviceRoleKey = getEnv('SUPABASE_SERVICE_ROLE_KEY');

  const emailArg = process.argv.find((a) => a.startsWith('--email='));
  const passwordArg = process.argv.find((a) => a.startsWith('--password='));
  const email = (emailArg ? emailArg.split('=')[1] : process.env.TEST_EMAIL) || '';
  const password = (passwordArg ? passwordArg.split('=')[1] : process.env.TEST_PASSWORD) || '';

  if (!email || !password) {
    console.error('Provide email/password via --email= --password= or TEST_EMAIL/TEST_PASSWORD envs');
    process.exit(1);
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey);

  console.log('Creating user (email confirmed)...');
  const { data, error } = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
  });

  if (error) {
    console.error('Failed to create user:', error.message);
    process.exit(1);
  }

  console.log('User created:', {
    id: data.user?.id,
    email: data.user?.email,
    created_at: data.user?.created_at,
  });
  console.log('You can now sign in with this email/password from the app.');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});