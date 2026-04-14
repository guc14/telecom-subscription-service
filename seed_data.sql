-- ============================================================
-- Telecom Subscription Service — Test Data
-- Based on subscriptions.csv
-- Run this in MySQL after docker-compose up -d
-- ============================================================

-- Clear existing data (safe to re-run)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE subscriptions;
TRUNCATE TABLE customer_profiles;
TRUNCATE TABLE service_plans;
TRUNCATE TABLE customers;
SET FOREIGN_KEY_CHECKS = 1;

-- ── Customers ─────────────────────────────────────────────
INSERT INTO customers (id, name, age) VALUES
(101, 'Alice Chen',    28),
(102, 'Bob Smith',     35),
(103, 'Carol Tan',     42),
(104, 'Dave Wong',     29),
(105, 'Eve Li',        31),
(106, 'Frank Wu',      55),
(107, 'Grace Kim',     24),
(108, 'Henry Zhang',   47),
(109, 'Iris Liu',      38),
(110, 'Jack Ma',       62),
(111, 'Karen Lee',     33),
(112, 'Leo Wang',      26),
(113, 'Mia Chen',      45),
(114, 'Nick Park',     30),
(115, 'Olivia Sun',    52),
(116, 'Peter Lin',     27),
(117, 'Quinn Ho',      39),
(118, 'Rachel Yu',     44),
(119, 'Sam Ng',        31),
(120, 'Tina Zhou',     28);

-- ── Service Plans ─────────────────────────────────────────
INSERT INTO service_plans (id, name, description, monthly_fee_cents, capacity) VALUES
(10, '5G Unlimited Plus',
     'Unlimited 5G data with 100GB hotspot, international calling to 50+ countries, and HD streaming included.',
     8500, NULL),
(20, 'Home Fibre 500M',
     'High-speed home fibre internet at 500Mbps download, perfect for families with multiple devices.',
     6500, NULL),
(30, 'Basic Mobile',
     'Essential mobile plan with 5GB data, unlimited local calls and texts. Great for light users.',
     2900, NULL),
(40, 'Business Pro',
     'Enterprise-grade plan with dedicated support, 200GB data, VPN access, and priority network routing.',
     12000, 500);

-- ── Customer Profiles ─────────────────────────────────────
INSERT INTO customer_profiles (customer_id, billing_address, account_tier, preferred_contact) VALUES
(101, '101 Maple St, Toronto, ON',      'PREMIUM',  'EMAIL'),
(102, '202 Oak Ave, Vancouver, BC',     'STANDARD', 'SMS'),
(103, '303 Pine Rd, Calgary, AB',       'VIP',      'EMAIL'),
(104, '404 Elm Blvd, Ottawa, ON',       'STANDARD', 'PUSH'),
(105, '505 Cedar Lane, Montreal, QC',   'PREMIUM',  'EMAIL'),
(106, '606 Birch St, Edmonton, AB',     'VIP',      'EMAIL'),
(107, '707 Spruce Ave, Winnipeg, MB',   'STANDARD', 'SMS'),
(108, '808 Willow Rd, Halifax, NS',     'PREMIUM',  'EMAIL'),
(109, '909 Aspen Dr, Victoria, BC',     'STANDARD', 'PUSH'),
(110, '1010 Walnut St, Toronto, ON',    'VIP',      'EMAIL');

-- ── Subscriptions ─────────────────────────────────────────
INSERT INTO subscriptions (customer_id, plan_id, activated_at, status, idempotency_key) VALUES
(101, 10, '2025-01-15 09:23:11', 'ACTIVE',     'seed-sub-001'),
(102, 10, '2025-01-18 14:05:33', 'ACTIVE',     'seed-sub-002'),
(103, 20, '2025-01-20 11:44:02', 'ACTIVE',     'seed-sub-003'),
(104, 30, '2025-02-01 08:30:00', 'ACTIVE',     'seed-sub-004'),
(105, 10, '2025-02-03 16:12:55', 'ACTIVE',     'seed-sub-005'),
(106, 20, '2025-02-10 10:00:00', 'ACTIVE',     'seed-sub-006'),
(107, 30, '2025-02-14 13:22:41', 'CANCELLED',  'seed-sub-007'),
(108, 10, '2025-02-20 09:05:17', 'ACTIVE',     'seed-sub-008'),
(109, 20, '2025-03-01 11:11:11', 'ACTIVE',     'seed-sub-009'),
(110, 30, '2025-03-05 15:30:00', 'ACTIVE',     'seed-sub-010'),
(111, 10, '2025-03-08 08:45:22', 'ACTIVE',     'seed-sub-011'),
(112, 20, '2025-03-12 14:20:00', 'ACTIVE',     'seed-sub-012'),
(113, 30, '2025-03-15 10:10:10', 'ACTIVE',     'seed-sub-013'),
(114, 10, '2025-03-20 16:55:00', 'CANCELLED',  'seed-sub-014'),
(115, 20, '2025-03-25 09:30:00', 'ACTIVE',     'seed-sub-015'),
(116, 30, '2025-04-01 11:00:00', 'ACTIVE',     'seed-sub-016'),
(117, 10, '2025-04-03 14:15:00', 'ACTIVE',     'seed-sub-017'),
(118, 20, '2025-04-05 10:45:00', 'ACTIVE',     'seed-sub-018'),
(119, 30, '2025-04-08 13:00:00', 'ACTIVE',     'seed-sub-019'),
(120, 10, '2025-04-10 09:00:00', 'ACTIVE',     'seed-sub-020');

-- ── Verify ────────────────────────────────────────────────
SELECT 'Customers'     AS table_name, COUNT(*) AS count FROM customers
UNION ALL
SELECT 'Service Plans' AS table_name, COUNT(*) AS count FROM service_plans
UNION ALL
SELECT 'Subscriptions' AS table_name, COUNT(*) AS count FROM subscriptions
UNION ALL
SELECT 'Profiles'      AS table_name, COUNT(*) AS count FROM customer_profiles;
