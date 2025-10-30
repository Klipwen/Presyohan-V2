# Presyohan Backend Scripts (Provisioning)

Safe server-side provisioning and migrations for Supabase/Postgres.

## Prerequisites
- Postgres connection string available as an environment variable.
- Node.js 18+.

## Set `DATABASE_URL` (PowerShell)
```powershell
$env:DATABASE_URL='postgres://USER:PASS@HOST:5432/postgres'
```

Do NOT commit secrets. Use local env vars or CI secrets.

## Install Dependencies
```powershell
cd backend
npm install
```

## Run Provisioning Script
```powershell
node scripts/ensure_tables.js
```

The script is idempotent and will:
- Ensure `pgcrypto` extension
- Create `stores` and `products` tables if they do not exist
- Add a trigger to keep `products.updated_at` current

## Migration SQL
Timestamped migration files are under `supabase/migrations/`.
Apply them with your preferred workflow (Supabase SQL editor or `psql`).

## Safety Note
Run only in trusted environments. For production, use migrations and review changes.

## Optional CI (Ask First)
If your repo uses CI/CD (e.g., GitHub Actions), you can run the script post-deploy using a `DATABASE_URL` secret. Example (do not add without confirmation):
```yaml
jobs:
  provision:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm ci
        working-directory: backend
      - run: node scripts/ensure_tables.js
        working-directory: backend
        env:
          DATABASE_URL: ${{ secrets.DATABASE_URL }}
```
 
## Apply Supabase Migrations (Best Practice)
Prefer using migrations as the single source of truth.

Option A — Supabase CLI (recommended):
```powershell
# Install CLI (user scope)
npm i -g supabase

# Apply all migrations to a target database via env URL
$env:DATABASE_URL='postgres://USER:PASS@HOST:5432/postgres'
supabase db push --db-url $env:DATABASE_URL --dry-run  # preview
supabase db push --db-url $env:DATABASE_URL           # apply
```

Option B — psql directly:
```powershell
# Apply a specific migration file
$env:DATABASE_URL='postgres://USER:PASS@HOST:5432/postgres'
psql $env:DATABASE_URL -f .\supabase\migrations\20251029_120000_create_stores_products.sql
psql $env:DATABASE_URL -f .\supabase\migrations\20251029_121500_create_core_tables_and_rls.sql
```

Notes:
- Keep secrets out of the repo. Use local env vars or CI secrets.
- RLS is enabled; client writes are limited. Use service role for privileged operations.

## Create a confirmed test user (bypass email verification)
Use the Admin API with your service role key. Do NOT commit secrets.

Prerequisites:
- Set env vars in PowerShell:
```powershell
$env:SUPABASE_URL='https://<your-project-ref>.supabase.co'
$env:SUPABASE_SERVICE_ROLE_KEY='<your-service-role-key>'
```

Run the script:
```powershell
cd backend
node scripts/create_test_user.js --email=test@example.com --password=StrongP@ssw0rd!
```

Notes:
- The user is created with `email_confirm: true` so you can sign in immediately.
- Use this only for development/testing. Rotate secrets if shared.