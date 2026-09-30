-- The gvenzl/oracle-xe image runs init .sql files as SYS.
-- Switch to the pluggable database and make SUMATIVA the current schema
-- so the application tables/data are created under the APP_USER schema.
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = SUMATIVA;

-- =====================================================================
-- Sumativa - Sistema de Gestion de Usuarios y Roles
-- Schema creation script for Oracle
-- Run against the target schema/user (e.g. SUMATIVA)
-- =====================================================================

-- Drop objects if they already exist (safe re-run for local dev)
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE USER_ROLES CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN
         RAISE;
      END IF;
END;
/

BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE USERS CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN
         RAISE;
      END IF;
END;
/

BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE ROLES CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN
         RAISE;
      END IF;
END;
/

-- Reset audit table when rebuilding a development volume.
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE EVENT_AUDIT CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

-- =====================================================================
-- USERS
-- =====================================================================
CREATE TABLE USERS (
    ID              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USERNAME        VARCHAR2(50)  NOT NULL,
    EMAIL           VARCHAR2(120) NOT NULL,
    PASSWORD_HASH   VARCHAR2(255) NOT NULL,
    CREATED_AT      TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT UQ_USERS_USERNAME UNIQUE (USERNAME),
    CONSTRAINT UQ_USERS_EMAIL UNIQUE (EMAIL)
);

-- =====================================================================
-- ROLES
-- =====================================================================
CREATE TABLE ROLES (
    ID              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    NAME            VARCHAR2(50)  NOT NULL,
    DESCRIPTION     VARCHAR2(255),
    CONSTRAINT UQ_ROLES_NAME UNIQUE (NAME)
);

-- =====================================================================
-- USER_ROLES (many-to-many join table)
-- =====================================================================
CREATE TABLE USER_ROLES (
    USER_ID         NUMBER NOT NULL,
    ROLE_ID         NUMBER NOT NULL,
    CONSTRAINT PK_USER_ROLES PRIMARY KEY (USER_ID, ROLE_ID),
    CONSTRAINT FK_USER_ROLES_USER FOREIGN KEY (USER_ID) REFERENCES USERS (ID) ON DELETE CASCADE,
    CONSTRAINT FK_USER_ROLES_ROLE FOREIGN KEY (ROLE_ID) REFERENCES ROLES (ID) ON DELETE CASCADE
);

-- Event Grid consumer audit. EVENT_ID is unique to make redelivery idempotent.
CREATE TABLE EVENT_AUDIT (
    EVENT_ID        VARCHAR2(80) PRIMARY KEY,
    EVENT_TYPE      VARCHAR2(120) NOT NULL,
    SUBJECT         VARCHAR2(255) NOT NULL,
    EVENT_TIME      VARCHAR2(40),
    EVENT_DATA      VARCHAR2(4000),
    RECEIVED_AT     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);
