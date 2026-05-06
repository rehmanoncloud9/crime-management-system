# Crime Management System

A desktop application built for Pakistani law enforcement, developed as a semester project using Java, JavaFX, Hibernate, and MariaDB (via XAMPP).

---

## What this system does

CMS is an internal tool for managing police operations — registering crime incidents, tracking case files, managing suspects and evidence, handling court proceedings, and generating reports. Access is role-based, so officers, investigators, supervisors, and administrators each see what's relevant to their job.

---

## Getting started

### What you need installed

- Java 21
- Maven
- XAMPP (for MariaDB — MySQL also works)

### 1. Set up the database

Open XAMPP Control Panel and start MySQL. That's it — the app will create the database automatically on first run.

### 2. Set your database password

Open `src/main/resources/db.properties` and put in your MySQL password:

```properties
db.url=jdbc:mysql://localhost:3306/cms_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
db.username=root
db.password=YOUR_PASSWORD_HERE
db.driver=com.mysql.cj.jdbc.Driver
```

This is the only place you need to touch. Everything else reads from here.

> **Note:** `db.properties` is in `.gitignore` so your password never gets pushed to GitHub.

### 3. Run the app

```bash
mvn clean javafx:run
```

### First login

On a fresh database, the app creates one default admin account automatically. The credentials are set in `config.properties`:

```properties
app.admin.username=admin
app.admin.default.password=Admin@CMS2024!
```

You'll be asked to change the password immediately after your first login — you can't skip this step.

---

## How user accounts work

This system doesn't let anyone register themselves. All accounts are created by the admin through the User Management screen. Here's the flow:

**Admin setup (first run)**
The app checks if any admin exists in the database. If not, it creates one using the credentials in `config.properties`. This only ever happens once — restarts don't touch existing accounts or reset passwords.

**Creating officer accounts**
1. Log in as admin
2. Go to User Management
3. Fill in the officer's name, badge number, role, and precinct
4. The system generates a temporary password (badge number + random 4-digit pin)
5. A dialog shows you the temporary credentials to hand to the officer
6. The officer logs in and is immediately prompted to set their own password before they can do anything else

**Password resets**
If an admin resets someone's password through User Management, that officer will be forced to set a new password on their next login — the admin-set password is only ever temporary.

---

## Project structure

```
src/main/java/com/cms/
├── controller/       JavaFX screen controllers
├── model/            Hibernate entity classes
├── repository/       Database query classes
├── service/          Business logic
└── util/             Helpers and utilities

src/main/resources/
├── fxml/             Screen layouts
├── css/              Styling
├── db.properties     Your database credentials (gitignored)
├── config.properties App settings
└── hibernate.cfg.xml Hibernate configuration
```

---

## Configuration reference

### `db.properties` — database connection
| Key | What it does |
|-----|-------------|
| `db.url` | Full JDBC connection string |
| `db.username` | MySQL username |
| `db.password` | MySQL password — set this to yours |
| `db.driver` | JDBC driver class |

### `config.properties` — app settings
| Key | What it does |
|-----|-------------|
| `app.admin.username` | Username for the first-run admin account |
| `app.admin.badge` | Badge number for the first-run admin |
| `app.admin.email` | Email for the first-run admin |
| `app.admin.default.password` | Temporary password for first login |
| `app.support.email` | Shown on the login screen under "Contact Administrator" |
| `app.session.timeout.minutes` | How long before an inactive session expires |
| `app.password.lockout.attempts` | Failed logins before account lockout |

---

## Roles

| Role | What they can do |
|------|-----------------|
| Administrator | Everything — user management, config, all modules |
| Supervisor | Case oversight, officer management, reports |
| Officer | Register incidents, manage cases, log evidence |
| Analyst | View data, run reports, AI analytics |
| Records Clerk | Data entry, person registry |
| Legal Officer | Court management, charge sheets |

---

## Troubleshooting

**"Access denied for user root@localhost"**
Your password in `db.properties` is wrong. Open XAMPP, start MySQL, and verify your password by connecting manually:
```bash
"C:\xampp\mysql\bin\mysql.exe" -u root -p
```

**"Unable to create requested service"**
MySQL isn't running. Open XAMPP Control Panel and click Start next to MySQL.

**App starts but login fails with a red error**
Check that your database is running and that the schema was applied. Look at the console output for details — every error is logged there.

**Forgot the admin password**
Since the app no longer resets it automatically, you'll need to reset it directly in the database:
```sql
-- Connect to MySQL, then run:
USE cms_db;
UPDATE users SET password_hash = '$2a$10$...bcrypt_hash_here' WHERE username = 'admin';
```
Or just delete the admin user and let the app recreate it on next startup.

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| UI | JavaFX 21 |
| ORM | Hibernate 6 |
| Database | MariaDB 10.4 (via XAMPP) |
| Connection pool | HikariCP |
| Password hashing | BCrypt |
| Build tool | Maven |
| AI chatbot | Groq API (llama3-70b) with local fallback |
| ML models | Weka (risk scoring) |
| Reporting | JasperReports |
| Logging | SLF4J + Logback |

---

## Environment variables (optional)

If you'd rather not store credentials in a file at all, the app also reads from environment variables:

```bash
CMS_DB_URL=jdbc:mysql://localhost:3306/cms_db
CMS_DB_USER=root
CMS_DB_PASSWORD=yourpassword
```

These take priority over `db.properties` if set.

For the AI chatbot feature:
```bash
GROQ_API_KEY=your_groq_api_key
```
Without this the chatbot falls back to a built-in local response engine — it still works, just without the LLM.

## Design Language: Aurora Nexus v2.0
The application features the **Aurora Nexus v2.0** design system, a cutting-edge UI/UX framework designed specifically for this project. It goes beyond standard flat designs to offer a state-of-the-art experience:

- **Liquid Glassmorphism**: Translucent, frosted-glass navigation panels and hubs with dynamic gradients.
- **Claymorphism Identity Hubs**: Soft, 3D "bubbled" containers for user profiles and action groups that provide a tactile, premium feel.
- **Holographic Widgets**: High-fidelity clock and status panels with vibrant neon accents.
- **Interactive Micro-Animations**: Smooth scale transitions, rotations, and glows that respond to user presence.
- **Accessibility & Contrast**: Carefully curated color palettes ensuring high visibility and readability across all modules.

---

## Notes for the examiner

This project demonstrates:

- **Ultra-Premium UI/UX (Aurora Nexus)** — State-of-the-art glassmorphism and claymorphism implementation using custom CSS.
- **MVC architecture** — controllers, services, and repositories are cleanly separated.
- **ORM with Hibernate** — all database interactions go through entity mappings and HQL.
- **Role-based access control** — each user role sees a different dashboard and has different permissions.
- **Security fundamentals** — passwords are BCrypt-hashed, accounts lock after failed attempts, sessions expire.
- **AI-Powered Intelligence** — Integrated LLM support (Groq) for database analysis and natural language queries.
- **Audit trail** — every significant action is logged to the `audit_logs` table.
- **Normalization** — schema follows 1NF/2NF/3NF with proper foreign keys and junction tables.

---

*Built with Aurora Nexus Design System · JavaFX 21 · Hibernate 6 · MariaDB · Maven*
