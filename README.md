# POSCS

POSCS is a Java web application (Jakarta EE / Servlets + JSP) built as a
NetBeans Ant project, backed by a MySQL database.

## Tech Stack

- **Language:** Java 21
- **Platform:** Jakarta EE 11 (Servlets + JSP), deployed as a WAR to a
  servlet container such as Apache Tomcat
- **Build tool:** Apache Ant (NetBeans project — `build.xml`)
- **Database:** MySQL (via `mysql-connector-j` and `commons-dbutils`,
  connection pooling with `HikariCP`)
- **Notable libraries:** `jbcrypt` (password hashing), `jakarta.mail`
  (email), `commons-fileupload`, `poi` (Excel), `pdfbox` (PDF), `jackson-databind` (JSON)

## Project Structure

```
src/java/poscs/
  controller/   Servlets handling HTTP requests
  dao/          Data access objects for database operations
  model/        Domain entities (User, Role, Address, ...)
  common/       Shared utility classes
web/            JSP pages and static web resources
nbproject/      NetBeans/Ant project configuration
build.xml       Ant build script
```

## Database Setup

The database schema is not created automatically — run `db/schema.sql`
once against a fresh MySQL database (`utf8mb4`):

```bash
mysql -u root -p -e "CREATE DATABASE poscs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -u root -p poscs_db < db/schema.sql
```

`db/schema.sql` (re)creates every table the app expects, from scratch,
in one shot — including the core tables (users, enterprises, contracts,
products, technical support tickets, ...) and the customer evaluation
tables (`contract_payments`, `customer_evaluation_rules`,
`customer_lifecycle_events`). Re-running it drops and recreates all of
these tables, so only run it on a fresh/dev database, not one with data
you want to keep.

### Changing the schema later

Don't edit a live database by hand. Add a file under
[`db/migrations/`](db/migrations/README.md) instead — that folder
documents the naming convention and full workflow (including keeping
`db/schema.sql` in sync so new setups stay up to date).

## Building and Running

This project is set up as a NetBeans Ant-based web application:

1. Open the project folder in NetBeans (or run Ant directly with
   `ant build` / `ant run` from the repository root).
2. Set up the database (see **Database Setup** above).
3. Configure connection settings via the `DB_URL` / `DB_USER` /
   `DB_PASSWORD` environment variables, read by `poscs.dao.DBContext`
   (falls back to `jdbc:mysql://localhost:3306/poscs_db` / `root` / `1234`
   if unset).
4. Deploy the resulting WAR to a Jakarta EE 11–compatible servlet
   container (e.g. Apache Tomcat).

For deploying to a real server (staging/production), see
[DEPLOY.md](DEPLOY.md).

## Roles and permissions

See [PERMISSIONS.md](PERMISSIONS.md) for the role-based access matrix
(who can do what per feature). Login/session enforcement is wired up
in `AuthenticationController`/`AuthenticationFilter`, and the matrix
is enforced server-side for Customer/Contract/Ticket via
`poscs.common.AccessControl` — see PERMISSIONS.md for what's still
pending (Product/Employee).

## Security

- **CSRF protection:** every state-changing `POST` (including login)
  must carry a valid per-session token, checked in
  `AuthenticationFilter` before the request reaches a servlet — see
  `poscs.common.CsrfUtil`.
- **Session fixation:** login regenerates the session instead of
  reusing whatever session ID the request arrived with (see
  `AuthenticationController#handleLogin`).
- **OTP rate limiting:** the forgot-password flow locks out a code
  after 5 failed verification attempts (`PasswordResetController`).
- **Role-based access control:** see [PERMISSIONS.md](PERMISSIONS.md).
