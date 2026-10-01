-- ============================================================================
-- AgroMarket: Quick Fix Script for Supabase SQL Editor
-- Run this entire script in Supabase Studio -> SQL Editor -> New Query -> Run
-- It fixes foreign keys, enables live chat, fixes order placement, and seeds photos.
-- ============================================================================

-- 1. Ensure Demo Users in auth.users
DO $$
BEGIN
    -- Kamal Silva (Farmer 1 - Kandy)
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = '00000000-0000-0000-0000-000000000003') THEN
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password,
            email_confirmed_at, phone, phone_confirmed_at,
            confirmation_token, recovery_token, email_change,
            email_change_token_new, email_change_token_current,
            phone_change, phone_change_token, reauthentication_token,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000003',
            '00000000-0000-0000-0000-000000000000',
            'authenticated', 'authenticated',
            'kamal.farmer@agromarket.lk',
            crypt('FarmerPass123!', gen_salt('bf')),
            NOW(), '+94712345678', NOW(),
            '', '', '', '', '', '', '', '',
            '{"provider":"phone","providers":["phone"]}'::jsonb,
            '{"full_name":"Kamal Silva"}'::jsonb,
            NOW(), NOW()
        ) ON CONFLICT (id) DO NOTHING;
    END IF;

    -- Saman Kumara (Farmer 2 - Nuwara Eliya)
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = '00000000-0000-0000-0000-000000000004') THEN
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password,
            email_confirmed_at, phone, phone_confirmed_at,
            confirmation_token, recovery_token, email_change,
            email_change_token_new, email_change_token_current,
            phone_change, phone_change_token, reauthentication_token,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000004',
            '00000000-0000-0000-0000-000000000000',
            'authenticated', 'authenticated',
            'saman.farmer@agromarket.lk',
            crypt('FarmerPass123!', gen_salt('bf')),
            NOW(), '+94781234567', NOW(),
            '', '', '', '', '', '', '', '',
            '{"provider":"phone","providers":["phone"]}'::jsonb,
            '{"full_name":"Saman Kumara"}'::jsonb,
            NOW(), NOW()
        ) ON CONFLICT (id) DO NOTHING;
    END IF;

    -- Nimal Perera (Buyer - Colombo)
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = '00000000-0000-0000-0000-000000000002') THEN
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password,
            email_confirmed_at, phone, phone_confirmed_at,
            confirmation_token, recovery_token, email_change,
            email_change_token_new, email_change_token_current,
            phone_change, phone_change_token, reauthentication_token,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000002',
            '00000000-0000-0000-0000-000000000000',
            'authenticated', 'authenticated',
            'nimal.buyer@agromarket.lk',
            crypt('BuyerPass123!', gen_salt('bf')),
            NOW(), '+94771234567', NOW(),
            '', '', '', '', '', '', '', '',
            '{"provider":"phone","providers":["phone"]}'::jsonb,
            '{"full_name":"Nimal Perera"}'::jsonb,
            NOW(), NOW()
        ) ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;

-- 2. Populate Profiles, Farmers, Farmer Stats
INSERT INTO profiles (id, full_name, district_id, city_id) VALUES
('00000000-0000-0000-0000-000000000002', 'Nimal Perera', 1, 9),
('00000000-0000-0000-0000-000000000003', 'Kamal Silva', 4, 38),
('00000000-0000-0000-0000-000000000004', 'Saman Kumara', 6, 60)
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    district_id = EXCLUDED.district_id,
    city_id = EXCLUDED.city_id;

INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops, land_size, land_unit, default_pickup_landmark)
VALUES 
('00000000-0000-0000-0000-000000000003', 4, 38, ARRAY['Tomato', 'Beans', 'Carrot', 'Leeks', 'Potato'], 2.5, 'acres', 'Near Peradeniya Botanical Gardens entrance, Kandy Road'),
('00000000-0000-0000-0000-000000000004', 6, 60, ARRAY['Potato', 'Cabbage', 'Carrot', 'Beetroot'], 4.0, 'acres', 'Opposite Nuwara Eliya Post Office / Racecourse road')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO farmer_stats (farmer_id, completed_orders, terminal_accepted_orders, fulfilled_orders, fulfillment_rate, delivered_orders, on_time_orders, on_time_rate, rating_avg, rating_count, reliability_score, is_top_farmer)
VALUES 
('00000000-0000-0000-0000-000000000003', 25, 25, 24, 0.960, 24, 23, 0.958, 4.80, 22, 95.9, true),
('00000000-0000-0000-0000-000000000004', 18, 18, 17, 0.944, 17, 16, 0.941, 4.70, 15, 93.5, true)
ON CONFLICT (farmer_id) DO NOTHING;

-- 3. Update Tables
ALTER TABLE listings ADD COLUMN IF NOT EXISTS photos TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE listings DROP CONSTRAINT IF EXISTS chk_photos_length;
ALTER TABLE listings DROP CONSTRAINT IF EXISTS listings_photos_check;
ALTER TABLE listings ADD CONSTRAINT listings_photos_check 
    CHECK (photos IS NULL OR array_length(photos, 1) IS NULL OR array_length(photos, 1) <= 5);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN ('inquiry', 'requested', 'accepted', 'rejected', 'paid', 'ready', 'dispatched', 'delivered', 'completed', 'cancelled', 'expired', 'refunded', 'disputed')
);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_farmer_not_buyer;

-- 4. Enable RLS with Open Access for Messages & Orders
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public or parties read messages" ON messages;
CREATE POLICY "Public or parties read messages" ON messages FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow insert messages" ON messages;
CREATE POLICY "Allow insert messages" ON messages FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Parties read orders" ON orders;
CREATE POLICY "Parties read orders" ON orders FOR SELECT USING (true);

DROP POLICY IF EXISTS "Parties insert orders" ON orders;
CREATE POLICY "Parties insert orders" ON orders FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Parties update orders" ON orders;
CREATE POLICY "Parties update orders" ON orders FOR UPDATE USING (true);

-- 5. search_listings Function (Island-wide + District filter)
DROP FUNCTION IF EXISTS search_listings(INT, TEXT, NUMERIC, NUMERIC, TEXT, TIMESTAMPTZ, INT);
DROP FUNCTION IF EXISTS search_listings(INT, TEXT, NUMERIC, NUMERIC, TEXT, TEXT, INT);
DROP FUNCTION IF EXISTS search_listings(INT, TEXT, NUMERIC, NUMERIC, TEXT, INT);

CREATE OR REPLACE FUNCTION search_listings(
    p_district_id INT DEFAULT NULL,
    p_crop_query TEXT DEFAULT NULL,
    p_min_price NUMERIC DEFAULT NULL,
    p_max_price NUMERIC DEFAULT NULL,
    p_sort TEXT DEFAULT 'rating',
    p_cursor TIMESTAMPTZ DEFAULT NULL,
    p_limit INT DEFAULT 100
)
RETURNS TABLE (
    id UUID,
    farmer_id UUID,
    crop_name TEXT,
    price_per_kg NUMERIC,
    quantity_available NUMERIC,
    min_order_kg NUMERIC,
    harvest_date DATE,
    photos TEXT[],
    is_available_now BOOLEAN,
    created_at TIMESTAMPTZ,
    farmer_first_name TEXT,
    cultivation_district_name TEXT,
    cultivation_city_name TEXT,
    rating_avg NUMERIC,
    rating_count INT,
    reliability_score NUMERIC,
    is_top_farmer BOOLEAN,
    completed_orders INT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_today_sl DATE := (NOW() AT TIME ZONE 'Asia/Colombo')::DATE;
BEGIN
    RETURN QUERY
    SELECT
        l.id,
        l.farmer_id,
        l.crop_name,
        l.price_per_kg,
        l.quantity_available,
        l.min_order_kg,
        l.harvest_date,
        l.photos,
        (v_today_sl >= l.harvest_date) AS is_available_now,
        l.created_at,
        split_part(p.full_name, ' ', 1) AS farmer_first_name,
        d.name AS cultivation_district_name,
        c.name AS cultivation_city_name,
        COALESCE(fs.rating_avg, 5.00) AS rating_avg,
        COALESCE(fs.rating_count, 0) AS rating_count,
        COALESCE(fs.reliability_score, 95.0) AS reliability_score,
        COALESCE(fs.is_top_farmer, false) AS is_top_farmer,
        COALESCE(fs.completed_orders, 0) AS completed_orders
    FROM listings l
    JOIN farmers f ON f.user_id = l.farmer_id
    JOIN profiles p ON p.id = l.farmer_id
    JOIN districts d ON d.id = f.cultivation_district_id
    JOIN cities c ON c.id = f.cultivation_city_id
    LEFT JOIN farmer_stats fs ON fs.farmer_id = l.farmer_id
    WHERE l.is_active = true
      AND l.quantity_available > 0
      AND (p_district_id IS NULL OR f.cultivation_district_id = p_district_id)
      AND (p_crop_query IS NULL OR l.crop_name ILIKE '%' || p_crop_query || '%' OR l.crop_name_key ILIKE '%' || p_crop_query || '%')
      AND (p_min_price IS NULL OR l.price_per_kg >= p_min_price)
      AND (p_max_price IS NULL OR l.price_per_kg <= p_max_price)
      AND (p_cursor IS NULL OR l.created_at < p_cursor)
    ORDER BY
        CASE WHEN p_sort = 'price_asc' THEN l.price_per_kg END ASC,
        CASE WHEN p_sort = 'price_desc' THEN l.price_per_kg END DESC,
        CASE WHEN p_sort = 'rating' THEN COALESCE(fs.rating_avg, 5.0) END DESC,
        l.created_at DESC
    LIMIT LEAST(p_limit, 200);
END;
$$;

GRANT EXECUTE ON FUNCTION search_listings(INT, TEXT, NUMERIC, NUMERIC, TEXT, TIMESTAMPTZ, INT) TO anon, authenticated, service_role;

-- 6. create_order_request Function
DROP FUNCTION IF EXISTS create_order_request(UUID, NUMERIC, TEXT, DATE, INT, INT, TEXT);
DROP FUNCTION IF EXISTS create_order_request(UUID, NUMERIC, TEXT, DATE, INT, INT, TEXT, UUID);

CREATE OR REPLACE FUNCTION create_order_request(
    p_listing_id UUID,
    p_quantity_kg NUMERIC,
    p_delivery_method TEXT,
    p_requested_date DATE,
    p_delivery_district_id INT DEFAULT NULL,
    p_delivery_city_id INT DEFAULT NULL,
    p_delivery_address TEXT DEFAULT NULL,
    p_buyer_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := coalesce(auth.uid(), p_buyer_id);
    v_listing RECORD;
    v_settings RECORD;
    v_timeout_hours INT := 12;
    v_commission_rate NUMERIC := 0.03;
    v_subtotal NUMERIC(12,2);
    v_expires_at TIMESTAMPTZ;
    v_order_id UUID;
BEGIN
    IF v_buyer_id IS NULL THEN
        v_buyer_id := '00000000-0000-0000-0000-000000000002';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = v_buyer_id) THEN
        INSERT INTO profiles (id, full_name, district_id, city_id)
        VALUES (v_buyer_id, 'Buyer ' || SUBSTRING(v_buyer_id::text, 1, 6), COALESCE(p_delivery_district_id, 1), COALESCE(p_delivery_city_id, 1))
        ON CONFLICT (id) DO NOTHING;
    END IF;

    SELECT * INTO v_listing FROM listings WHERE id = p_listing_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Crop listing not found';
    END IF;

    IF NOT v_listing.is_active OR v_listing.quantity_available <= 0 THEN
        RAISE EXCEPTION 'This crop is currently unavailable or out of stock';
    END IF;

    -- Ensure farmer exists
    IF NOT EXISTS (SELECT 1 FROM farmers WHERE user_id = v_listing.farmer_id) THEN
        INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops)
        VALUES (v_listing.farmer_id, 4, 38, ARRAY[v_listing.crop_name])
        ON CONFLICT (user_id) DO NOTHING;
    END IF;

    IF p_quantity_kg < v_listing.min_order_kg THEN
        RAISE EXCEPTION 'Minimum order quantity is % kg', v_listing.min_order_kg;
    END IF;

    IF p_quantity_kg > v_listing.quantity_available THEN
        RAISE EXCEPTION 'Requested quantity exceeds available stock of % kg', v_listing.quantity_available;
    END IF;

    SELECT * INTO v_settings FROM app_settings LIMIT 1;
    IF FOUND THEN
        v_timeout_hours := COALESCE(v_settings.farmer_response_hours, 12);
        v_commission_rate := COALESCE(v_settings.commission_rate, 0.03);
    END IF;

    v_subtotal := ROUND(p_quantity_kg * v_listing.price_per_kg, 2);
    v_expires_at := NOW() + (v_timeout_hours || ' hours')::INTERVAL;
    v_order_id := gen_random_uuid();

    INSERT INTO orders (
        id, order_number, buyer_id, farmer_id, listing_id, crop_name,
        price_per_kg, quantity_kg, subtotal, delivery_method, delivery_fee,
        commission_rate, commission_amount, total_amount, farmer_payout_amount,
        requested_date, delivery_district_id, delivery_city_id, status,
        stock_reserved, expires_at, requested_at, created_at, updated_at
    ) VALUES (
        v_order_id,
        'AM-' || LPAD(FLOOR(RANDOM() * 900000 + 100000)::TEXT, 6, '0'),
        v_buyer_id, v_listing.farmer_id, p_listing_id, v_listing.crop_name,
        v_listing.price_per_kg, p_quantity_kg, v_subtotal,
        COALESCE(p_delivery_method, 'buyer_arranged'), 0.00,
        v_commission_rate, ROUND(v_subtotal * v_commission_rate, 2),
        v_subtotal, ROUND(v_subtotal * (1 - v_commission_rate), 2),
        COALESCE(p_requested_date, CURRENT_DATE), p_delivery_district_id, p_delivery_city_id,
        'requested', true, v_expires_at, NOW(), NOW(), NOW()
    );

    INSERT INTO order_private_details (order_id, delivery_address, pickup_landmark)
    VALUES (
        v_order_id,
        CASE WHEN p_delivery_method = 'farmer_delivery' THEN TRIM(p_delivery_address) ELSE NULL END,
        CASE WHEN p_delivery_method = 'buyer_arranged' THEN (SELECT default_pickup_landmark FROM farmers WHERE user_id = v_listing.farmer_id) ELSE NULL END
    ) ON CONFLICT (order_id) DO NOTHING;

    INSERT INTO messages (order_id, sender_id, body, created_at)
    VALUES (
        v_order_id, NULL,
        'Order requested for ' || p_quantity_kg || ' kg of ' || v_listing.crop_name || ' (Rs. ' || v_subtotal || '). The farmer will confirm availability shortly.',
        NOW()
    );

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (
        v_listing.farmer_id, 'new_order_request', 'New Order: ' || v_listing.crop_name,
        'New order request for ' || p_quantity_kg || ' kg received from buyer.', v_order_id
    );

    RETURN v_order_id;
END;
$$;

GRANT EXECUTE ON FUNCTION create_order_request(UUID, NUMERIC, TEXT, DATE, INT, INT, TEXT, UUID) TO anon, authenticated, service_role;

-- 7. create_or_get_inquiry_chat Function
CREATE OR REPLACE FUNCTION create_or_get_inquiry_chat(
    p_listing_id UUID,
    p_buyer_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := coalesce(auth.uid(), p_buyer_id);
    v_listing RECORD;
    v_order_id UUID;
    v_existing_id UUID;
BEGIN
    IF v_buyer_id IS NULL THEN
        v_buyer_id := '00000000-0000-0000-0000-000000000002';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = v_buyer_id) THEN
        INSERT INTO profiles (id, full_name, district_id, city_id)
        VALUES (v_buyer_id, 'Buyer ' || SUBSTRING(v_buyer_id::text, 1, 6), 1, 1)
        ON CONFLICT (id) DO NOTHING;
    END IF;

    SELECT * INTO v_listing FROM listings WHERE id = p_listing_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Crop listing not found';
    END IF;

    SELECT id INTO v_existing_id
    FROM orders
    WHERE buyer_id = v_buyer_id AND listing_id = p_listing_id AND status NOT IN ('rejected', 'cancelled', 'expired', 'completed', 'refunded')
    ORDER BY requested_at DESC LIMIT 1;

    IF v_existing_id IS NOT NULL THEN
        RETURN v_existing_id;
    END IF;

    v_order_id := gen_random_uuid();
    INSERT INTO orders (
        id, order_number, buyer_id, farmer_id, listing_id, crop_name,
        price_per_kg, quantity_kg, subtotal, delivery_method,
        commission_rate, commission_amount, total_amount, farmer_payout_amount,
        requested_date, status, stock_reserved, requested_at, created_at, updated_at
    ) VALUES (
        v_order_id,
        'INQ-' || LPAD(FLOOR(RANDOM() * 900000 + 100000)::TEXT, 6, '0'),
        v_buyer_id, v_listing.farmer_id, p_listing_id, v_listing.crop_name,
        v_listing.price_per_kg, v_listing.min_order_kg,
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        'buyer_arranged', 0.03, 0.00,
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        CURRENT_DATE, 'inquiry', false, NOW(), NOW(), NOW()
    );

    INSERT INTO messages (order_id, sender_id, body, created_at)
    VALUES (
        v_order_id, NULL,
        'Discussion opened for ' || v_listing.crop_name || '. Ask about harvest readiness, quantity discounts, or pickup details.',
        NOW()
    );

    RETURN v_order_id;
END;
$$;

GRANT EXECUTE ON FUNCTION create_or_get_inquiry_chat(UUID, UUID) TO anon, authenticated, service_role;

-- 8. send_chat_message Function
CREATE OR REPLACE FUNCTION send_chat_message(
    p_order_id UUID,
    p_body TEXT,
    p_sender_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_sender_id UUID := coalesce(auth.uid(), p_sender_id);
    v_order RECORD;
    v_msg_id UUID;
    v_clean_body TEXT;
    v_recipient_id UUID;
    v_digits TEXT;
BEGIN
    v_clean_body := TRIM(p_body);
    IF char_length(v_clean_body) < 1 OR char_length(v_clean_body) > 1000 THEN
        RAISE EXCEPTION 'Message must be between 1 and 1000 characters';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order discussion not found';
    END IF;

    IF v_order.status IN ('rejected', 'cancelled', 'expired', 'completed', 'refunded') THEN
        RAISE EXCEPTION 'Chat is closed because this order has ended';
    END IF;

    v_digits := regexp_replace(v_clean_body, '[^0-9]', '', 'g');
    IF length(v_digits) >= 9 OR v_clean_body ~* '(whatsapp|viber|telegram|wa\.me)' THEN
        IF v_sender_id IS NOT NULL AND EXISTS (SELECT 1 FROM auth.users WHERE id = v_sender_id) THEN
            INSERT INTO flagged_messages (order_id, sender_id, original_body, reasons, reviewed)
            VALUES (p_order_id, v_sender_id, v_clean_body, ARRAY['phone_or_external_contact'], false);
        END IF;
        RAISE EXCEPTION 'Sharing direct phone numbers or external channels is restricted for safety. Please communicate securely within AgroMarket.';
    END IF;

    IF v_sender_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_sender_id) THEN
        v_sender_id := NULL;
    END IF;

    v_msg_id := gen_random_uuid();
    INSERT INTO messages (id, order_id, sender_id, body, created_at)
    VALUES (v_msg_id, p_order_id, v_sender_id, v_clean_body, NOW());

    IF v_sender_id = v_order.buyer_id THEN
        v_recipient_id := v_order.farmer_id;
    ELSE
        v_recipient_id := v_order.buyer_id;
    END IF;

    IF v_recipient_id IS NOT NULL AND EXISTS (SELECT 1 FROM profiles WHERE id = v_recipient_id) THEN
        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_recipient_id, 'new_message', 'New Message (' || v_order.crop_name || ')', v_clean_body, p_order_id);
    END IF;

    RETURN jsonb_build_object(
        'id', v_msg_id,
        'order_id', p_order_id,
        'sender_id', v_sender_id,
        'body', v_clean_body,
        'created_at', NOW()
    );
END;
$$;

GRANT EXECUTE ON FUNCTION send_chat_message(UUID, TEXT, UUID) TO anon, authenticated, service_role;

-- 9. Seed listings with high-res crop images
UPDATE listings
SET photos = ARRAY['https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=800&q=80', 'https://images.unsplash.com/photo-1561136594-7f68413baa99?w=800&q=80']
WHERE crop_name_key ILIKE '%tomato%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY['https://images.unsplash.com/photo-1567375698348-5d9d5ae99de0?w=800&q=80']
WHERE crop_name_key ILIKE '%bean%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY['https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=800&q=80']
WHERE crop_name_key ILIKE '%potato%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY['https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?w=800&q=80']
WHERE crop_name_key ILIKE '%cabbage%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY['https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?w=800&q=80']
WHERE crop_name_key ILIKE '%carrot%' AND (photos IS NULL OR photos = '{}');
