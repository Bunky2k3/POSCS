# Deploying POSCS

This guide covers taking POSCS from source to a running server. See
[README.md](README.md) for local dev setup — this is the production/
staging path.

## 1. Prerequisites on the target server

| Requirement | Version |
|---|---|
| JDK | 21 (matches `javac.source`/`javac.target` in `nbproject/project.properties`) |
| Servlet container | A Jakarta EE 11–compatible container, e.g. Apache Tomcat (project targets `j2ee.platform=11-web`) |
| MySQL | 8.x or later (tested against 9.3; driver is `mysql-connector-j`) |

## 2. Build the WAR

From the repository root:

```bash
ant dist
```

This produces the deployable archive under `dist/`. This works outside
NetBeans since it's a standard Ant project (`build.xml`) — no IDE
required on the server.

## 3. Set up the database

```bash
mysql -u root -p -e "CREATE DATABASE poscs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -u root -p poscs_db < db/schema.sql
```

Then apply any files added later under `db/migrations/`, in order —
see [`db/migrations/README.md`](db/migrations/README.md) for the
naming convention and workflow.

**Create a dedicated MySQL user for the app** — do not use `root` in
production:

```sql
CREATE USER 'poscs_app'@'%' IDENTIFIED BY '<strong-password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON poscs_db.* TO 'poscs_app'@'%';
FLUSH PRIVILEGES;
```

## 4. Configure database credentials

`poscs.dao.DBContext` reads `DB_URL`, `DB_USER`, and `DB_PASSWORD` from
environment variables, falling back to
`jdbc:mysql://localhost:3306/poscs_db` / `root` / `1234` if unset.

**That fallback is for local dev only — never rely on it in production.**
Set real environment variables wherever the servlet container process
picks them up (e.g. Tomcat's `bin/setenv.sh` / `bin/setenv.bat`, or the
system/service environment before Tomcat starts):

```bash
export DB_URL="jdbc:mysql://<db-host>:3306/poscs_db"
export DB_USER="poscs_app"
export DB_PASSWORD="<strong-password>"
```

## 5. Security checklist before going live

- [ ] Dedicated, least-privilege MySQL user (see step 3) — not `root`.
- [ ] Strong, unique `DB_PASSWORD`, different from any dev/local value.
- [ ] MySQL port (3306) not exposed to the public internet — only
      reachable from the app server.
- [ ] HTTPS enabled (typically via a reverse proxy such as Nginx in
      front of Tomcat, or Tomcat's own SSL connector).
- [ ] `nbproject/private/` and any local `.properties` files are not
      deployed (they're git-ignored already — just don't hand-copy them).

## 6. Deploy the WAR

Copy the built `.war` file into the container's deploy directory (e.g.
Tomcat's `webapps/`) and let it auto-deploy, or use `ant run-deploy` if
a target server is configured in the Ant/NetBeans project.

## 7. Ongoing operations

- **Logging:** the app currently uses `slf4j-simple`, which logs to
  the console only. For a real server, replace it with a provider that
  writes to a rotating log file (e.g. Logback with a rolling file
  appender), so logs survive restarts and don't grow unbounded.
- **Backups:** schedule regular `mysqldump` backups of `poscs_db`.
- **Monitoring:** watch the container's error logs and HikariCP's pool
  logs (connection acquisition failures, pool exhaustion) for early
  signs of trouble.

## Current status

Most controllers (`AuthenticationController`, `CustomerController`,
`ContractController`, ...) and DAOs are still scaffolding without real
business logic. Deploying today gets you a working skeleton — real
user-facing functionality depends on that work landing first.
