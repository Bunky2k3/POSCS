# Database migrations

This folder holds **incremental** database changes, applied on top of
[`db/schema.sql`](../schema.sql). Use this folder — not `schema.sql` —
whenever you're changing the schema of a database that already has data
you want to keep. `schema.sql` drops and recreates every table, so
re-running it wipes existing data; a migration file only adds the one
change it describes.

## Naming convention

```
V{version}__{change_description}__{github_username}.sql
```

- **`V{version}`** — a plain incrementing integer, always the next
  number after the highest one already in this folder (`V1`, `V2`,
  `V3`, ...). Check the folder before naming yours.
- **`{change_description}`** — short, `snake_case`, describes *what*
  changes (not why). No spaces, no version number repeated here.
- **`{github_username}`** — your GitHub username, so it's obvious at a
  glance who authored the change.
- Separator between the three parts is a **double underscore** (`__`).

**Examples:**
```
V1__add_total_value_to_contracts__ndat2003.sql
V2__add_csat_rating_to_technicalrequests__Huong.sql
V3__add_index_on_enterprise_name__ndat2003.sql
```

**Not okay:**
```
add_total_value.sql                 (missing version + username)
V1_add_total_value__ndat2003.sql    (single underscore after V1)
V1__Add Total Value__ndat2003.sql   (spaces, not snake_case)
v1__add_total_value__ndat2003.sql   (lowercase v)
```

## Workflow for a schema change

1. Look in this folder for the highest existing `V{n}`, pick `n+1`.
2. Create the new file following the naming convention above. Write
   **only** the incremental change (`ALTER TABLE`, `CREATE TABLE`,
   etc.) — never `DROP TABLE` / recreate existing tables here.
3. At the top of the file's SQL, record it in `schema_migrations` so
   it's never accidentally re-applied:
   ```sql
   INSERT INTO schema_migrations (version) VALUES ('V1__add_total_value_to_contracts__ndat2003');
   ALTER TABLE contracts ADD COLUMN total_value DECIMAL(15,2) NULL;
   ```
4. Before running it, check it hasn't already been applied:
   ```sql
   SELECT * FROM schema_migrations WHERE version = 'V1__add_total_value_to_contracts__ndat2003';
   ```
   If that returns a row, skip — it's already applied on this database.
5. Run the file against your local database. If it contains any non-ASCII
   text (Vietnamese, etc.), pass `--default-character-set=utf8mb4` to the
   `mysql` client -- without it, some client setups silently mis-encode
   the file on the way in and you end up with mojibake stored in the DB
   (looks fine in some terminals, breaks everywhere else, including the
   app itself):
   ```bash
   mysql --default-character-set=utf8mb4 -u root -p poscs_db < db/migrations/V3__....sql
   ```
6. **Also apply the same change to `db/schema.sql`** (edit the relevant
   `CREATE TABLE` directly) so a brand-new setup via `schema.sql` still
   ends up matching every applied migration. `schema.sql` and this
   folder must never drift apart.
7. Commit both the new migration file and the updated `schema.sql` in
   the same PR.

## Why both `schema.sql` and `migrations/`?

- **`schema.sql`** — fastest path for a brand-new database (new
  teammate, CI, fresh dev machine): one file, one command, done.
- **`migrations/`** — safe path for a database that already has data:
  applies only the new change, never touches existing rows.

They describe the same end state; `schema.sql` is just a convenience
snapshot that must be kept in sync by hand whenever a migration is
added (see step 6 above).
