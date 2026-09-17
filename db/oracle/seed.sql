-- The gvenzl/oracle-xe image runs init .sql files as SYS.
-- Switch to the pluggable database and make SUMATIVA the current schema
-- so the application tables/data are created under the APP_USER schema.
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = SUMATIVA;

-- =====================================================================
-- Sumativa - Sistema de Gestion de Usuarios y Roles
-- Seed data script for Oracle
-- Run after schema.sql
-- =====================================================================

-- Roles
INSERT INTO ROLES (NAME, DESCRIPTION) VALUES ('ADMIN', 'Full access to all system features');
INSERT INTO ROLES (NAME, DESCRIPTION) VALUES ('USER', 'Standard application user');
INSERT INTO ROLES (NAME, DESCRIPTION) VALUES ('EDITOR', 'Can create and edit content');

-- Users
-- Note: PASSWORD_HASH values below are placeholder bcrypt-like hashes for demo/seed purposes only.
INSERT INTO USERS (USERNAME, EMAIL, PASSWORD_HASH)
VALUES ('admin', 'admin@sumativa.local', '$2a$10$examplehash.admin.0000000000000000000000');

INSERT INTO USERS (USERNAME, EMAIL, PASSWORD_HASH)
VALUES ('jperez', 'jperez@sumativa.local', '$2a$10$examplehash.jperez.000000000000000000000');

INSERT INTO USERS (USERNAME, EMAIL, PASSWORD_HASH)
VALUES ('mfigueroa', 'mfigueroa@sumativa.local', '$2a$10$examplehash.mfigueroa.0000000000000000000');

-- Role assignments (USER_ID / ROLE_ID rely on identity generation order above:
-- ROLES  -> 1=ADMIN, 2=USER, 3=EDITOR
-- USERS  -> 1=admin, 2=jperez, 3=mfigueroa)
INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (1, 1); -- admin -> ADMIN
INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (1, 2); -- admin -> USER
INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (2, 2); -- jperez -> USER
INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (3, 2); -- mfigueroa -> USER
INSERT INTO USER_ROLES (USER_ID, ROLE_ID) VALUES (3, 3); -- mfigueroa -> EDITOR

COMMIT;
