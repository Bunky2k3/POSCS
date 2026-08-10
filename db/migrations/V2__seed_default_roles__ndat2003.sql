-- Seeds the 4 roles the application's permission model is designed
-- around. See PERMISSIONS.md at the repo root for the full
-- resource x role access matrix these roles are used for.

INSERT INTO schema_migrations (version) VALUES ('V2__seed_default_roles__ndat2003');

INSERT INTO roles (role_name) VALUES
('Admin'),
('Sales'),
('Kỹ thuật'),
('CSKH');
