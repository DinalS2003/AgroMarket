-- AgroMarket Phase 1: Development Seed Script
-- 1 Admin, 3 Users (2 of which are Farmers), 5 Diverse Listings

-- 1. Seed Auth Users (Dummy bcrypt hash for testing: 'Password123!')
-- Admin User
INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    'admin@agromarket.lk',
    crypt('Password123!', gen_salt('bf')),
    NOW(),
    '{"provider":"email","providers":["email"]}',
    '{"full_name":"System Administrator"}',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Buyer 1 (Nimal Perera - Colombo)
INSERT INTO auth.users (id, instance_id, aud, role, phone, phone_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    '+94771234567',
    NOW(),
    '{"provider":"phone","providers":["phone"]}',
    '{"full_name":"Nimal Perera"}',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Farmer 1 (Kamal Silva - Kandy)
INSERT INTO auth.users (id, instance_id, aud, role, phone, phone_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    '+94712345678',
    NOW(),
    '{"provider":"phone","providers":["phone"]}',
    '{"full_name":"Kamal Silva"}',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Farmer 2 (Saman Kumara - Nuwara Eliya)
INSERT INTO auth.users (id, instance_id, aud, role, phone, phone_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    '+94781234567',
    NOW(),
    '{"provider":"phone","providers":["phone"]}',
    '{"full_name":"Saman Kumara"}',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2. Admin Users Table
INSERT INTO admin_users (user_id)
VALUES ('00000000-0000-0000-0000-000000000001')
ON CONFLICT (user_id) DO NOTHING;

-- 3. Profiles
INSERT INTO profiles (id, full_name, district_id, city_id) VALUES
('00000000-0000-0000-0000-000000000001', 'System Administrator', 1, 1),
('00000000-0000-0000-0000-000000000002', 'Nimal Perera', 1, 9),      -- Colombo, Dehiwala
('00000000-0000-0000-0000-000000000003', 'Kamal Silva', 4, 38),     -- Kandy, Peradeniya
('00000000-0000-0000-0000-000000000004', 'Saman Kumara', 6, 60)     -- Nuwara Eliya
ON CONFLICT (id) DO NOTHING;

-- 4. User Private (Phone & NIC)
INSERT INTO user_private (user_id, nic, phone_e164) VALUES
('00000000-0000-0000-0000-000000000002', '198512345678', '+94771234567'),
('00000000-0000-0000-0000-000000000003', '199012345678', '+94712345678'),
('00000000-0000-0000-0000-000000000004', '199212345678', '+94781234567')
ON CONFLICT (user_id) DO NOTHING;

-- 5. Farmers & Farmer Private
-- Farmer 1: Kamal Silva
INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops, land_size, land_unit, default_pickup_landmark)
VALUES (
    '00000000-0000-0000-0000-000000000003', 4, 38,
    ARRAY['Tomato', 'Beans', 'Carrot', 'Leeks'],
    2.5, 'acres',
    'Near Peradeniya Botanical Gardens entrance, Kandy Road'
) ON CONFLICT (user_id) DO NOTHING;

INSERT INTO farmer_private (user_id, cultivation_address, bank_name, bank_branch, account_holder_name, account_number)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    'No. 45/A, River Valley Organic Farm, Gannoruwa, Peradeniya',
    'Bank of Ceylon', 'Peradeniya', 'K. A. Silva', '7045123490'
) ON CONFLICT (user_id) DO NOTHING;

INSERT INTO farmer_stats (farmer_id, completed_orders, terminal_accepted_orders, fulfilled_orders, fulfillment_rate, delivered_orders, on_time_orders, on_time_rate, rating_avg, rating_count, reliability_score, is_top_farmer)
VALUES ('00000000-0000-0000-0000-000000000003', 25, 25, 24, 0.960, 24, 23, 0.958, 4.80, 22, 95.9, true)
ON CONFLICT (farmer_id) DO NOTHING;

-- Farmer 2: Saman Kumara
INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops, land_size, land_unit, default_pickup_landmark)
VALUES (
    '00000000-0000-0000-0000-000000000004', 6, 60,
    ARRAY['Potato', 'Cabbage', 'Beetroot', 'Carrot'],
    5.0, 'acres',
    'Opposite Pedro Tea Factory Gate, Nuwara Eliya'
) ON CONFLICT (user_id) DO NOTHING;

INSERT INTO farmer_private (user_id, cultivation_address, bank_name, bank_branch, account_holder_name, account_number)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    'Highland Eco Farms, Upper Lake Road, Nuwara Eliya',
    'Commercial Bank of Ceylon', 'Nuwara Eliya', 'Saman Kumara', '8012345678'
) ON CONFLICT (user_id) DO NOTHING;

INSERT INTO farmer_stats (farmer_id, completed_orders, terminal_accepted_orders, fulfilled_orders, fulfillment_rate, delivered_orders, on_time_orders, on_time_rate, rating_avg, rating_count, reliability_score, is_top_farmer)
VALUES ('00000000-0000-0000-0000-000000000004', 8, 8, 8, 1.000, 8, 7, 0.875, 4.60, 7, 94.7, false)
ON CONFLICT (farmer_id) DO NOTHING;

-- 6. Seed 5 Diverse Listings
-- Listing 1: Kamal - Tomato (Ready now)
INSERT INTO listings (id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date, photos, is_active)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000003',
    'Fresh Red Tomatoes (Grade A)', 'tomato',
    350.00, 320.00, 10.00,
    CURRENT_DATE - INTERVAL '1 day',
    ARRAY['https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=600&q=80'],
    true
) ON CONFLICT (id) DO NOTHING;

-- Listing 2: Kamal - Green Beans (Ready now)
INSERT INTO listings (id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date, photos, is_active)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003',
    'Green Beans (Chi Chi)', 'beans',
    180.00, 450.00, 5.00,
    CURRENT_DATE,
    ARRAY['https://images.unsplash.com/photo-1567375698348-5d9d5ae99de0?w=600&q=80'],
    true
) ON CONFLICT (id) DO NOTHING;

-- Listing 3: Saman - Upcountry Potatoes (Ready now)
INSERT INTO listings (id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date, photos, is_active)
VALUES (
    '10000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000004',
    'Nuwara Eliya Seed Potatoes', 'potato',
    1200.00, 380.00, 25.00,
    CURRENT_DATE - INTERVAL '2 days',
    ARRAY['https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=600&q=80'],
    true
) ON CONFLICT (id) DO NOTHING;

-- Listing 4: Saman - Fresh Cabbage (Ready now)
INSERT INTO listings (id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date, photos, is_active)
VALUES (
    '10000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000004',
    'Crisp Green Cabbage', 'cabbage',
    600.00, 190.00, 15.00,
    CURRENT_DATE,
    ARRAY['https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?w=600&q=80'],
    true
) ON CONFLICT (id) DO NOTHING;

-- Listing 5: Saman - Carrots (Upcoming harvest in 4 days)
INSERT INTO listings (id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date, photos, is_active)
VALUES (
    '10000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000004',
    'Highland Sweet Carrots', 'carrot',
    400.00, 280.00, 10.00,
    CURRENT_DATE + INTERVAL '4 days',
    ARRAY['https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?w=600&q=80'],
    true
) ON CONFLICT (id) DO NOTHING;
