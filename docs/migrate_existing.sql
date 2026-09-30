-- Run as SUMATIVA against XEPDB1 once on an existing EC2 Oracle volume.
-- This file is outside container-entrypoint-initdb.d so it never reruns at startup.
CREATE TABLE EVENT_AUDIT (
    EVENT_ID VARCHAR2(80) PRIMARY KEY,
    EVENT_TYPE VARCHAR2(120) NOT NULL,
    SUBJECT VARCHAR2(255) NOT NULL,
    EVENT_TIME VARCHAR2(40),
    EVENT_DATA VARCHAR2(4000),
    RECEIVED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);
