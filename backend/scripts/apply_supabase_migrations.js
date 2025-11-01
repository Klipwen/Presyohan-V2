// Apply Supabase SQL migrations in order using DATABASE_URL
// Usage (PowerShell):
//   $env:DATABASE_URL="postgres://USER:PASS@HOST:5432/postgres"
//   node backend\scripts\apply_supabase_migrations.js

const { Client } = require('pg');
const fs = require('fs');
const path = require('path');

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error('[apply_migrations] Missing env DATABASE_URL');
    process.exit(1);
  }

  const migrationsDir = path.resolve(__dirname, '..', '..', 'supabase', 'migrations');
  const files = fs.readdirSync(migrationsDir)
    .filter((f) => f.endsWith('.sql'))
    .sort();

  if (files.length === 0) {
    console.log('[apply_migrations] No SQL files found.');
    return;
  }

  const client = new Client({
    connectionString: databaseUrl,
    ssl: { rejectUnauthorized: false },
  });

  await client.connect();
  console.log(`[apply_migrations] Connected. Applying ${files.length} SQL files…`);

  try {
    for (const file of files) {
      const full = path.join(migrationsDir, file);
      const sql = fs.readFileSync(full, 'utf8');
      console.log(`[apply_migrations] Executing ${file}…`);
      await client.query(sql);
    }
    console.log('[apply_migrations] All migrations applied successfully.');
  } catch (e) {
    console.error('[apply_migrations] Failed:', e.message);
    process.exitCode = 1;
  } finally {
    await client.end();
  }
}

main();