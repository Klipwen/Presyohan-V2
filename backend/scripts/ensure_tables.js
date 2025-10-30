// Safe server-side provisioning script (CommonJS)
// - Uses DATABASE_URL env var
// - Idempotent: creates extensions and tables if not exist
// - Targets Supabase Postgres (works with standard Postgres)

const { Client } = require('pg');

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.log('[ensure_tables] Skipped: DATABASE_URL is not set.');
    console.log('Set it in PowerShell: $env:DATABASE_URL="postgres://USER:PASS@HOST:5432/postgres"');
    process.exit(0);
  }

  const client = new Client({
    connectionString: databaseUrl,
    ssl: { rejectUnauthorized: false }, // Dev convenience; Supabase requires SSL
  });

  try {
    await client.connect();
    console.log('[ensure_tables] Connected. Running migrations…');

    // Ensure required extension for gen_random_uuid
    await client.query('CREATE EXTENSION IF NOT EXISTS pgcrypto;');

    // Create stores table (based on ERD)
    await client.query(`
      CREATE TABLE IF NOT EXISTS stores (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        name TEXT NOT NULL,
        branch TEXT NULL,
        type TEXT NULL,
        invite_code TEXT NULL UNIQUE,
        invite_code_created_at TIMESTAMP NULL,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
      );
    `);

    // Create products table (minimal subset; category_id left nullable without FK)
    await client.query(`
      CREATE TABLE IF NOT EXISTS products (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
        category_id UUID NULL,
        name TEXT NOT NULL,
        description TEXT NULL,
        price NUMERIC(10,2) NOT NULL,
        unit TEXT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMP NOT NULL DEFAULT NOW()
      );
    `);

    // Updatable updated_at trigger for products
    await client.query(`
      CREATE OR REPLACE FUNCTION set_updated_at()
      RETURNS TRIGGER AS $$
      BEGIN
        NEW.updated_at = NOW();
        RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;
    `);
    await client.query(`
      DO $$
      BEGIN
        IF NOT EXISTS (
          SELECT 1 FROM pg_trigger WHERE tgname = 'products_updated_at'
        ) THEN
          CREATE TRIGGER products_updated_at
          BEFORE UPDATE ON products
          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;
      END $$;
    `);

    console.log('[ensure_tables] Done. Tables ensured: stores, products.');
  } catch (err) {
    console.error('[ensure_tables] Error:', err.message);
    process.exitCode = 1;
  } finally {
    try { await client.end(); } catch {}
  }
}

main();