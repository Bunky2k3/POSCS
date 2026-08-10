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

## Building and Running

This project is set up as a NetBeans Ant-based web application:

1. Open the project folder in NetBeans (or run Ant directly with
   `ant build` / `ant run` from the repository root).
2. Configure a MySQL database and update the connection settings used by
   `poscs.dao.DBContext`.
3. Deploy the resulting WAR to a Jakarta EE 11–compatible servlet
   container (e.g. Apache Tomcat).
