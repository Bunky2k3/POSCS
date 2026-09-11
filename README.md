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
  (email), `poi` (Excel), `pdfbox` (PDF), `jackson-databind` (JSON)

## Project Structure

```
src/java/poscs/
  controller/   Servlets handling HTTP requests
  dao/          Data access objects for database operations
  model/        Domain entities (User, Role, Address, ...)
  common/       Shared utilities, plus the servlet filter and the
                scheduled-notification listener
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

## Tests

`ant test` runs everything. Two very different layers live side by side:

- **Unit tests** (`test/poscs/**`, ~340 of them) mock JDBC via
  `poscs.dao.JdbcStub`, so no SQL ever reaches a database. They are fast and
  need no setup — but by construction they cannot catch a wrong column name,
  invalid SQL, a schema drift, a violated UNIQUE/foreign key, a transaction
  that fails to roll back, or the same rule computed two different ways in
  Java and in SQL.
- **Integration tests** (`test/poscs/integration/**`) run the real DAOs
  against a real MySQL. They cover exactly the gap above.

### Running the integration tests

They need a **disposable** database whose name ends in `_it` — every run wipes
it. `IntegrationDb` refuses to run anywhere else, so a stray `DB_URL` pointing
at `poscs_db` cannot destroy real data.

```bash
mysql -u root -p -e "CREATE DATABASE poscs_it CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it \
    -Dtest-sys-prop.DB_USER=root \
    -Dtest-sys-prop.DB_PASSWORD=yourpassword \
    test
```

The schema is loaded from `db/schema.sql` — the same file real setups use — so
a change there is exercised by the tests automatically.

`test-sys-prop.*` is how NetBeans' generated `build-impl.xml` forwards values
into the test JVM as system properties. It has to be a system property rather
than an environment variable, because a JVM cannot set env vars for itself;
`poscs.dao.DBContext` therefore reads a system property first and falls back to
the environment (which is what Tomcat uses in production).

### Without MySQL

Plain `ant test` stays green: the integration tests **skip** themselves when
they are not pointed at an `_it` database. That keeps a fresh clone working
with no setup — at the cost that "all skipped" looks just like "all passed",
so CI has an explicit step asserting they really ran.

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
is enforced server-side for Customer/Contract/Product/Ticket/Employee
via `poscs.common.AccessControl`.

## System log screen (Admin only)

`/systemLog` lets an Admin read the server's log files in the browser and
download one — useful during testing, when the person who hit the bug is not
the person with shell access to the server.

- Log directory: the `LOG_DIR` environment variable (or system property) if
  set, otherwise `${catalina.base}/logs` — where the container collects what
  the application writes to stderr.
- Only `.log` / `.out` / `.txt` files are listed, newest first. The `file`
  parameter is a **name**, always matched against that listing, so no path
  ever reaches the filesystem from a request (`poscs.common.LogFiles`).
- The view shows the last N lines (100–2000) of the selected file with an
  optional keyword filter; reads are capped at the last 512 KB so a huge
  `catalina.out` cannot be pulled into memory.
- Gated by `AccessControl.requireAdmin(...)` on every action, the download
  included — it returns a file without going through a JSP, so it needs its
  own check. See [PERMISSIONS.md](PERMISSIONS.md).

## Security

- **CSRF protection:** every state-changing `POST` (including login)
  must carry a valid per-session token, checked in
  `AuthenticationFilter` before the request reaches a servlet — see
  `poscs.common.CsrfUtil`.
- **Session fixation:** login regenerates the session instead of
  reusing whatever session ID the request arrived with (see
  `AuthenticationController#handleLogin`).
- **OTP rate limiting:** the forgot-password flow locks out a code
  after 5 failed verification attempts, and enforces a 30-second
  server-side cooldown between resend requests
  (`AuthenticationController`).
- **Role-based access control:** see [PERMISSIONS.md](PERMISSIONS.md).
