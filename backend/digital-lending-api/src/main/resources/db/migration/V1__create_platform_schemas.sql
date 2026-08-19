-- ============================================================================
-- V1 : Logical schema layout for the modular monolith.
--
-- Each backend module owns exactly one schema and may only reach into another
-- module's schema through that module's service layer, never with a cross-schema
-- join in a repository. Keeping the boundary visible in the database is what
-- makes a later extraction into separate services possible.
--
-- Tables are added by the migration that introduces the module that needs them.
-- This migration deliberately creates no tables.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS auth;
COMMENT ON SCHEMA auth IS 'Users, roles, permissions, devices, sessions, login history';

CREATE SCHEMA IF NOT EXISTS organization;
COMMENT ON SCHEMA organization IS 'Bank, zone, region, branch, department, business unit, credit unit';

CREATE SCHEMA IF NOT EXISTS customer;
COMMENT ON SCHEMA customer IS 'Customer master, addresses, contacts, employment, financial profile';

CREATE SCHEMA IF NOT EXISTS kyc;
COMMENT ON SCHEMA kyc IS 'KYC and e-KYC verification records and provider responses';

CREATE SCHEMA IF NOT EXISTS account;
COMMENT ON SCHEMA account IS 'Account products, account opening applications, opened accounts';

CREATE SCHEMA IF NOT EXISTS document;
COMMENT ON SCHEMA document IS 'Document metadata and verification state; binaries live in object storage';

CREATE SCHEMA IF NOT EXISTS product;
COMMENT ON SCHEMA product IS 'Loan products, product versions, parameters, fees, required documents';

CREATE SCHEMA IF NOT EXISTS rules;
COMMENT ON SCHEMA rules IS 'Rule definitions, rule groups, operators, evaluation results';

CREATE SCHEMA IF NOT EXISTS application;
COMMENT ON SCHEMA application IS 'Loan applications, applicants, financials, purposes, queries, comments';

CREATE SCHEMA IF NOT EXISTS workflow;
COMMENT ON SCHEMA workflow IS 'Workflow states, transitions, role-state permissions, state history';

CREATE SCHEMA IF NOT EXISTS credit;
COMMENT ON SCHEMA credit IS 'Credit analysis, scorecards, CIB records, screening results, risk grades';

CREATE SCHEMA IF NOT EXISTS approval;
COMMENT ON SCHEMA approval IS 'Approval matrix, tiers, limits, delegation, conditions, group approval';

CREATE SCHEMA IF NOT EXISTS loan;
COMMENT ON SCHEMA loan IS 'Disbursed loans, schedules, balances, charges, settlement, closure';

CREATE SCHEMA IF NOT EXISTS repayment;
COMMENT ON SCHEMA repayment IS 'Repayment transactions, allocation, reversals, reconciliation';

CREATE SCHEMA IF NOT EXISTS collection;
COMMENT ON SCHEMA collection IS 'DPD buckets, collection queue, contact history, promise to pay, recovery';

CREATE SCHEMA IF NOT EXISTS notification;
COMMENT ON SCHEMA notification IS 'Notification templates, events, queue and delivery log';

CREATE SCHEMA IF NOT EXISTS integration;
COMMENT ON SCHEMA integration IS 'Outbox events, external request/response records, reconciliation state';

CREATE SCHEMA IF NOT EXISTS audit;
COMMENT ON SCHEMA audit IS 'Immutable audit trail of every material action across all modules';
