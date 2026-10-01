-- AgroMarket Phase 1: Database Schema
-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 1. Districts (25 official Sri Lankan districts)
CREATE TABLE IF NOT EXISTS districts (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    province TEXT NOT NULL
);

-- 2. Cities (Administrative and DS divisions per district)
CREATE TABLE IF NOT EXISTS cities (
    id SERIAL PRIMARY KEY,
    district_id INTEGER NOT NULL REFERENCES districts(id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    postal_code TEXT,
    CONSTRAINT uq_district_city UNIQUE (district_id, name)
);

CREATE INDEX IF NOT EXISTS idx_cities_district_id ON cities(district_id);

-- 3. Profiles (Public user metadata - auth.users linked)
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    district_id INTEGER NOT NULL REFERENCES districts(id) ON DELETE RESTRICT,
    city_id INTEGER NOT NULL REFERENCES cities(id) ON DELETE RESTRICT,
    is_suspended BOOLEAN NOT NULL DEFAULT false,
    suspended_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. User Private Details (Strict privacy: phone and NIC, visible only to owner and admins)
CREATE TABLE IF NOT EXISTS user_private (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nic TEXT NOT NULL UNIQUE,
    phone_e164 TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_nic_format CHECK (
        nic ~ '^[0-9]{9}[VX]$' OR nic ~ '^[0-9]{12}$'
    )
);

-- 5. Farmers (Public farmer profile)
CREATE TABLE IF NOT EXISTS farmers (
    user_id UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    cultivation_district_id INTEGER NOT NULL REFERENCES districts(id) ON DELETE RESTRICT,
    cultivation_city_id INTEGER NOT NULL REFERENCES cities(id) ON DELETE RESTRICT,
    main_crops TEXT[] NOT NULL DEFAULT '{}',
    land_size NUMERIC(10,2) CHECK (land_size IS NULL OR land_size > 0),
    land_unit TEXT CHECK (land_unit IS NULL OR land_unit IN ('acres', 'perches')),
    default_pickup_landmark TEXT,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_farmers_district ON farmers(cultivation_district_id);

-- 6. Farmer Private Details (Strict privacy: cultivation address & bank details, visible only to owner and admins)
CREATE TABLE IF NOT EXISTS farmer_private (
    user_id UUID PRIMARY KEY REFERENCES farmers(user_id) ON DELETE CASCADE,
    cultivation_address TEXT NOT NULL,
    bank_name TEXT NOT NULL,
    bank_branch TEXT NOT NULL,
    account_holder_name TEXT NOT NULL,
    account_number TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. App Settings (Single row for configurable business parameters)
CREATE TABLE IF NOT EXISTS app_settings (
    id INT PRIMARY KEY DEFAULT 1,
    commission_rate NUMERIC(4,3) NOT NULL DEFAULT 0.030,
    farmer_response_hours INT NOT NULL DEFAULT 12,
    buyer_payment_hours INT NOT NULL DEFAULT 2,
    dispute_window_hours INT NOT NULL DEFAULT 24,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO app_settings (id, commission_rate, farmer_response_hours, buyer_payment_hours, dispute_window_hours)
VALUES (1, 0.030, 12, 2, 24)
ON CONFLICT (id) DO NOTHING;

-- 8. Listings
CREATE TABLE IF NOT EXISTS listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farmer_id UUID NOT NULL REFERENCES farmers(user_id) ON DELETE CASCADE,
    crop_name TEXT NOT NULL CHECK (char_length(trim(crop_name)) BETWEEN 2 AND 40),
    crop_name_key TEXT NOT NULL,
    quantity_available NUMERIC(10,2) NOT NULL CHECK (quantity_available >= 0),
    price_per_kg NUMERIC(10,2) NOT NULL CHECK (price_per_kg > 0),
    min_order_kg NUMERIC(10,2) NOT NULL CHECK (min_order_kg > 0),
    harvest_date DATE NOT NULL,
    photos TEXT[] NOT NULL DEFAULT '{}' CHECK (array_length(photos, 1) IS NULL OR array_length(photos, 1) <= 5),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_min_order CHECK (min_order_kg <= quantity_available)
);

CREATE INDEX IF NOT EXISTS idx_listings_farmer ON listings(farmer_id);
CREATE INDEX IF NOT EXISTS idx_listings_crop_key ON listings(crop_name_key);
CREATE INDEX IF NOT EXISTS idx_listings_active_qty ON listings(is_active, quantity_available);

-- 9. Crop suggestions view for autocomplete
CREATE OR REPLACE VIEW crop_suggestions AS
SELECT
    crop_name_key,
    MAX(crop_name) AS display_name,
    COUNT(*)::INTEGER AS listing_count
FROM listings
WHERE is_active = true AND quantity_available > 0
GROUP BY crop_name_key
ORDER BY listing_count DESC, display_name ASC;

-- 10. Orders
CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1001;

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number TEXT NOT NULL UNIQUE DEFAULT ('AM-' || lpad(nextval('order_number_seq')::TEXT, 6, '0')),
    buyer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    farmer_id UUID NOT NULL REFERENCES farmers(user_id) ON DELETE RESTRICT,
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    crop_name TEXT NOT NULL,
    price_per_kg NUMERIC(10,2) NOT NULL,
    quantity_kg NUMERIC(10,2) NOT NULL CHECK (quantity_kg > 0),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    delivery_method TEXT NOT NULL CHECK (delivery_method IN ('buyer_arranged', 'farmer_delivery')),
    farmer_delivery_mode TEXT CHECK (farmer_delivery_mode IS NULL OR farmer_delivery_mode IN ('own_transport', 'pickme')),
    delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (delivery_fee >= 0),
    commission_rate NUMERIC(4,3) NOT NULL,
    commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    farmer_payout_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    requested_date DATE NOT NULL,
    delivery_district_id INTEGER REFERENCES districts(id) ON DELETE RESTRICT,
    delivery_city_id INTEGER REFERENCES cities(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'requested' CHECK (
        status IN ('requested', 'accepted', 'rejected', 'paid', 'ready', 'dispatched', 'delivered', 'completed', 'cancelled', 'expired', 'refunded', 'disputed')
    ),
    stock_reserved BOOLEAN NOT NULL DEFAULT false,
    expires_at TIMESTAMPTZ,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    ready_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    ended_by TEXT CHECK (ended_by IS NULL OR ended_by IN ('buyer', 'farmer', 'system', 'admin')),
    end_reason TEXT,
    payout_status TEXT NOT NULL DEFAULT 'none' CHECK (
        payout_status IN ('none', 'held', 'pending', 'paid_out', 'void')
    ),
    payout_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    payout_reference TEXT,
    paid_out_at TIMESTAMPTZ,
    refund_status TEXT NOT NULL DEFAULT 'none' CHECK (
        refund_status IN ('none', 'required', 'refunded')
    ),
    refund_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    refund_reference TEXT,
    refunded_at TIMESTAMPTZ,
    is_on_time BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_farmer_not_buyer CHECK (buyer_id <> farmer_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_buyer ON orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_farmer ON orders(farmer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_expires_at ON orders(expires_at) WHERE status IN ('requested', 'accepted');

-- 11. Order Private Details (Delivery address & pickup landmark; strict privacy)
CREATE TABLE IF NOT EXISTS order_private_details (
    order_id UUID PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    delivery_address TEXT,
    pickup_landmark TEXT
);

-- 12. Payments
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    attempt INT NOT NULL DEFAULT 1,
    payhere_order_id TEXT NOT NULL UNIQUE,
    amount NUMERIC(12,2) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'LKR',
    status TEXT NOT NULL DEFAULT 'initiated' CHECK (
        status IN ('initiated', 'success', 'failed', 'cancelled', 'chargedback')
    ),
    payhere_payment_id TEXT,
    status_code INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_payhere_id ON payments(payhere_order_id);

-- 13. Messages (One chat per order)
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL, -- NULL indicates system message
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_order_created ON messages(order_id, created_at);

-- 14. Flagged Messages (Audit log for leak attempts)
CREATE TABLE IF NOT EXISTS flagged_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    original_body TEXT NOT NULL,
    reasons TEXT[] NOT NULL,
    reviewed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flagged_messages_reviewed ON flagged_messages(reviewed);

-- 15. Disputes
CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    raised_by UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) BETWEEN 10 AND 1000),
    photos TEXT[] NOT NULL DEFAULT '{}' CHECK (array_length(photos, 1) IS NULL OR array_length(photos, 1) <= 3),
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved')),
    resolution TEXT CHECK (resolution IS NULL OR resolution IN ('full_refund', 'partial_refund', 'release_to_farmer')),
    refund_amount NUMERIC(12,2) DEFAULT 0.00,
    admin_note TEXT,
    resolved_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 16. Reviews (Buyer reviews farmer upon order completion)
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    farmer_id UUID NOT NULL REFERENCES farmers(user_id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT CHECK (comment IS NULL OR char_length(trim(comment)) <= 500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reviews_farmer ON reviews(farmer_id);

-- 17. Farmer Stats (Reliability metrics)
CREATE TABLE IF NOT EXISTS farmer_stats (
    farmer_id UUID PRIMARY KEY REFERENCES farmers(user_id) ON DELETE CASCADE,
    completed_orders INT NOT NULL DEFAULT 0,
    terminal_accepted_orders INT NOT NULL DEFAULT 0,
    fulfilled_orders INT NOT NULL DEFAULT 0,
    fulfillment_rate NUMERIC(4,3) NOT NULL DEFAULT 0.000,
    delivered_orders INT NOT NULL DEFAULT 0,
    on_time_orders INT NOT NULL DEFAULT 0,
    on_time_rate NUMERIC(4,3) NOT NULL DEFAULT 0.000,
    rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    reliability_score NUMERIC(4,1) NOT NULL DEFAULT 0.0,
    is_top_farmer BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 18. Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, read_at);

-- 19. Device Tokens
CREATE TABLE IF NOT EXISTS device_tokens (
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, token)
);

-- 20. Admin Users
CREATE TABLE IF NOT EXISTS admin_users (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 21. Audit Log
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    entity TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
-- AgroMarket Phase 1: Row Level Security & Policies
-- Default Deny on ALL tables

ALTER TABLE districts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cities ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_private ENABLE ROW LEVEL SECURITY;
ALTER TABLE farmers ENABLE ROW LEVEL SECURITY;
ALTER TABLE farmer_private ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_private_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE flagged_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE farmer_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Helper function to check if caller is an admin
CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM admin_users WHERE user_id = auth.uid()
  );
$$;

-- 1. Districts & Cities: Public read, admin write
CREATE POLICY "Public read districts" ON districts FOR SELECT USING (true);
CREATE POLICY "Admin write districts" ON districts FOR ALL USING (is_admin());

CREATE POLICY "Public read cities" ON cities FOR SELECT USING (true);
CREATE POLICY "Admin write cities" ON cities FOR ALL USING (is_admin());

-- 2. Profiles: Public read, owner or admin update
CREATE POLICY "Public read profiles" ON profiles FOR SELECT USING (true);
CREATE POLICY "Owner update profile" ON profiles FOR UPDATE USING (auth.uid() = id);

-- 3. User Private (NIC & Phone): STRICT PRIVACY - Owner or Admin only
CREATE POLICY "Owner or Admin read user_private" ON user_private
    FOR SELECT USING (auth.uid() = user_id OR is_admin());

CREATE POLICY "Owner insert user_private" ON user_private
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Owner update user_private" ON user_private
    FOR UPDATE USING (auth.uid() = user_id);

-- 4. Farmers: Public read, owner update
CREATE POLICY "Public read farmers" ON farmers FOR SELECT USING (true);
CREATE POLICY "Owner update farmer" ON farmers FOR UPDATE USING (auth.uid() = user_id);

-- 5. Farmer Private (Address & Bank): STRICT PRIVACY - Owner or Admin only
CREATE POLICY "Owner or Admin read farmer_private" ON farmer_private
    FOR SELECT USING (auth.uid() = user_id OR is_admin());

CREATE POLICY "Owner insert farmer_private" ON farmer_private
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Owner update farmer_private" ON farmer_private
    FOR UPDATE USING (auth.uid() = user_id);

-- 6. App Settings: Public read, Admin write
CREATE POLICY "Public read app_settings" ON app_settings FOR SELECT USING (true);
CREATE POLICY "Admin update app_settings" ON app_settings FOR UPDATE USING (is_admin());

-- 7. Listings:
-- Visible if active AND quantity_available > 0 AND farmer not suspended, OR if caller is the farmer or admin
CREATE POLICY "Read listings" ON listings FOR SELECT USING (
    (
        is_active = true
        AND quantity_available > 0
        AND EXISTS (
            SELECT 1 FROM profiles p
            WHERE p.id = listings.farmer_id AND p.is_suspended = false
        )
    )
    OR auth.uid() = farmer_id
    OR is_admin()
);

CREATE POLICY "Farmer insert own listing" ON listings FOR INSERT
    WITH CHECK (auth.uid() = farmer_id AND NOT EXISTS (
        SELECT 1 FROM profiles WHERE id = auth.uid() AND is_suspended = true
    ));

CREATE POLICY "Farmer update own listing" ON listings FOR UPDATE
    USING (auth.uid() = farmer_id OR is_admin());

CREATE POLICY "Farmer delete own listing" ON listings FOR DELETE
    USING (auth.uid() = farmer_id OR is_admin());

-- 8. Orders:
-- Selectable only by buyer, farmer, or admin
CREATE POLICY "Order parties or Admin read orders" ON orders FOR SELECT
    USING (auth.uid() = buyer_id OR auth.uid() = farmer_id OR is_admin());

-- Notice: NO client insert or update policies for normal users on orders.
-- Orders are strictly modified through SECURITY DEFINER database functions.

-- 9. Order Private Details:
-- STRICT PRIVACY: Zero SELECT policy for regular users. Admin can select.
-- Normal users read strictly via the get_order_private_details(order_id) function!
CREATE POLICY "Admin read order_private_details" ON order_private_details FOR SELECT
    USING (is_admin());

-- 10. Payments:
CREATE POLICY "Order parties or Admin read payments" ON payments FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = payments.order_id
            AND (o.buyer_id = auth.uid() OR o.farmer_id = auth.uid())
        )
        OR is_admin()
    );

-- 11. Messages:
-- Parties to the order can read messages. Admins can read only if order is in dispute or admin.
CREATE POLICY "Order parties or Admin read messages" ON messages FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = messages.order_id
            AND (
                o.buyer_id = auth.uid()
                OR o.farmer_id = auth.uid()
                OR (is_admin() AND o.status = 'disputed')
            )
        )
    );

-- Notice: Messages are inserted strictly via the moderated send-message Edge Function (using service role).

-- 12. Flagged Messages: Admin only
CREATE POLICY "Admin read flagged_messages" ON flagged_messages FOR SELECT USING (is_admin());
CREATE POLICY "Admin update flagged_messages" ON flagged_messages FOR UPDATE USING (is_admin());

-- 13. Disputes:
CREATE POLICY "Order parties or Admin read disputes" ON disputes FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = disputes.order_id
            AND (o.buyer_id = auth.uid() OR o.farmer_id = auth.uid())
        )
        OR is_admin()
    );

-- 14. Reviews: Public read
CREATE POLICY "Public read reviews" ON reviews FOR SELECT USING (true);

-- 15. Farmer Stats: Public read
CREATE POLICY "Public read farmer_stats" ON farmer_stats FOR SELECT USING (true);

-- 16. Notifications: Owner only
CREATE POLICY "Owner read notifications" ON notifications FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Owner update notifications" ON notifications FOR UPDATE
    USING (auth.uid() = user_id);

-- 17. Device Tokens: Owner only
CREATE POLICY "Owner manage device_tokens" ON device_tokens FOR ALL
    USING (auth.uid() = user_id);

-- 18. Admin Users: Admin only
CREATE POLICY "Admin read admin_users" ON admin_users FOR SELECT USING (is_admin());

-- 19. Audit Log: Admin only
CREATE POLICY "Admin read audit_log" ON audit_log FOR SELECT USING (is_admin());

-- Storage Bucket Policies
INSERT INTO storage.buckets (id, name, public)
VALUES ('listing-photos', 'listing-photos', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('dispute-photos', 'dispute-photos', false)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS
CREATE POLICY "Public read listing-photos" ON storage.objects FOR SELECT
    USING (bucket_id = 'listing-photos');

CREATE POLICY "Farmer insert own listing-photos" ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'listing-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "Farmer delete own listing-photos" ON storage.objects FOR DELETE
    USING (
        bucket_id = 'listing-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "Parties or Admin read dispute-photos" ON storage.objects FOR SELECT
    USING (
        bucket_id = 'dispute-photos'
        AND (
            is_admin()
            OR auth.uid()::text = (storage.foldername(name))[1]
        )
    );

CREATE POLICY "Buyer insert dispute-photos" ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'dispute-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );
-- AgroMarket Phase 1: Functions and State Machine
-- All transitions execute atomically with row locks

-- 1. Get Order Private Details (Strict privacy enforcement)
CREATE OR REPLACE FUNCTION get_order_private_details(p_order_id UUID)
RETURNS TABLE (
    delivery_address TEXT,
    pickup_landmark TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_order RECORD;
    v_caller_id UUID := auth.uid();
    v_is_admin BOOLEAN;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order not found';
    END IF;

    v_is_admin := EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_caller_id);

    -- Admins can read full private details anytime
    IF v_is_admin THEN
        RETURN QUERY
        SELECT opd.delivery_address, opd.pickup_landmark
        FROM order_private_details opd
        WHERE opd.order_id = p_order_id;
        RETURN;
    END IF;

    -- Only active in-progress stages ('paid', 'ready', 'dispatched') allow parties to see private details
    IF v_order.status NOT IN ('paid', 'ready', 'dispatched') THEN
        RETURN QUERY SELECT NULL::TEXT, NULL::TEXT;
        RETURN;
    END IF;

    -- Farmer can only see delivery_address if farmer_delivery
    IF v_order.farmer_id = v_caller_id THEN
        IF v_order.delivery_method = 'farmer_delivery' THEN
            RETURN QUERY
            SELECT opd.delivery_address, NULL::TEXT
            FROM order_private_details opd
            WHERE opd.order_id = p_order_id;
        ELSE
            RETURN QUERY SELECT NULL::TEXT, NULL::TEXT;
        END IF;
        RETURN;
    END IF;

    -- Buyer can only see pickup_landmark if buyer_arranged
    IF v_order.buyer_id = v_caller_id THEN
        IF v_order.delivery_method = 'buyer_arranged' THEN
            RETURN QUERY
            SELECT NULL::TEXT, opd.pickup_landmark
            FROM order_private_details opd
            WHERE opd.order_id = p_order_id;
        ELSE
            RETURN QUERY SELECT NULL::TEXT, NULL::TEXT;
        END IF;
        RETURN;
    END IF;

    -- Unauthorized
    RAISE EXCEPTION 'Access denied';
END;
$$;

-- 1.5. Phone Auth Bridge (Creates auth.users record and provides credentials for JWT session)
CREATE OR REPLACE FUNCTION get_or_create_phone_auth(
    p_phone_e164 TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID;
    v_clean_phone TEXT;
    v_email TEXT;
    v_password TEXT;
    v_has_profile BOOLEAN;
BEGIN
    v_clean_phone := regexp_replace(p_phone_e164, '[^0-9+]', '', 'g');
    v_email := 'p' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '@agromarket.lk';
    v_password := 'AgroPass_' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '!';

    SELECT id INTO v_user_id FROM auth.users WHERE email = v_email OR phone = v_clean_phone LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (
            id,
            instance_id,
            aud,
            role,
            email,
            encrypted_password,
            email_confirmed_at,
            phone,
            phone_confirmed_at,
            raw_app_meta_data,
            raw_user_meta_data,
            created_at,
            updated_at
        ) VALUES (
            v_user_id,
            '00000000-0000-0000-0000-000000000000',
            'authenticated',
            'authenticated',
            v_email,
            crypt(v_password, gen_salt('bf')),
            NOW(),
            v_clean_phone,
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            jsonb_build_object('phone', v_clean_phone),
            NOW(),
            NOW()
        );
    END IF;

    SELECT EXISTS (SELECT 1 FROM profiles WHERE id = v_user_id) INTO v_has_profile;

    RETURN jsonb_build_object(
        'user_id', v_user_id,
        'email', v_email,
        'password', v_password,
        'has_profile', v_has_profile
    );
END;
$$;

GRANT EXECUTE ON FUNCTION get_or_create_phone_auth TO anon, authenticated;

-- 2. Register Profile (Step 1 Onboarding)
CREATE OR REPLACE FUNCTION register_profile(
    p_full_name TEXT,
    p_nic TEXT,
    p_phone_e164 TEXT,
    p_district_id INT,
    p_city_id INT,
    p_user_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := coalesce(auth.uid(), p_user_id);
    v_normalized_nic TEXT;
BEGIN
    IF v_user_id IS NULL THEN
        SELECT id INTO v_user_id FROM auth.users WHERE phone = p_phone_e164 LIMIT 1;
    END IF;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    v_normalized_nic := UPPER(TRIM(p_nic));

    -- Validate NIC format
    IF NOT (v_normalized_nic ~ '^[0-9]{9}[VX]$' OR v_normalized_nic ~ '^[0-9]{12}$') THEN
        RAISE EXCEPTION 'Invalid NIC format. Must be 9 digits followed by V/X or 12 digits';
    END IF;

    -- Validate Phone format
    IF NOT (p_phone_e164 ~ '^\+947[0-9]{8}$') THEN
        RAISE EXCEPTION 'Invalid Sri Lankan mobile format. Must be +947XXXXXXXX';
    END IF;

    -- Insert or update profiles
    INSERT INTO profiles (id, full_name, district_id, city_id, is_suspended, updated_at)
    VALUES (v_user_id, TRIM(p_full_name), p_district_id, p_city_id, false, NOW())
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        district_id = EXCLUDED.district_id,
        city_id = EXCLUDED.city_id,
        updated_at = NOW();

    -- Insert user_private (cannot update NIC if already set)
    INSERT INTO user_private (user_id, nic, phone_e164)
    VALUES (v_user_id, v_normalized_nic, p_phone_e164)
    ON CONFLICT (user_id) DO UPDATE SET
        phone_e164 = EXCLUDED.phone_e164;

    RETURN jsonb_build_object('success', true, 'user_id', v_user_id);
END;
$$;

GRANT EXECUTE ON FUNCTION register_profile TO anon, authenticated;


-- 3. Register Farmer (Step 2 Onboarding / Become Farmer)
CREATE OR REPLACE FUNCTION register_farmer(
    p_cultivation_district_id INT,
    p_cultivation_city_id INT,
    p_cultivation_address TEXT,
    p_main_crops TEXT[],
    p_land_size NUMERIC DEFAULT NULL,
    p_land_unit TEXT DEFAULT NULL,
    p_default_pickup_landmark TEXT DEFAULT NULL,
    p_bank_name TEXT DEFAULT NULL,
    p_bank_branch TEXT DEFAULT NULL,
    p_account_holder_name TEXT DEFAULT NULL,
    p_account_number TEXT DEFAULT NULL,
    p_user_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := coalesce(auth.uid(), p_user_id);
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF array_length(p_main_crops, 1) IS NULL OR array_length(p_main_crops, 1) < 1 OR array_length(p_main_crops, 1) > 15 THEN
        RAISE EXCEPTION 'Main crops must contain between 1 and 15 items';
    END IF;

    IF p_bank_name IS NULL OR p_bank_branch IS NULL OR p_account_holder_name IS NULL OR p_account_number IS NULL THEN
        RAISE EXCEPTION 'Bank details are required for farmer registration';
    END IF;

    INSERT INTO farmers (
        user_id, cultivation_district_id, cultivation_city_id, main_crops,
        land_size, land_unit, default_pickup_landmark, registered_at
    )
    VALUES (
        v_user_id, p_cultivation_district_id, p_cultivation_city_id, p_main_crops,
        p_land_size, p_land_unit, TRIM(p_default_pickup_landmark), NOW()
    )
    ON CONFLICT (user_id) DO UPDATE SET
        cultivation_district_id = EXCLUDED.cultivation_district_id,
        cultivation_city_id = EXCLUDED.cultivation_city_id,
        main_crops = EXCLUDED.main_crops,
        land_size = EXCLUDED.land_size,
        land_unit = EXCLUDED.land_unit,
        default_pickup_landmark = EXCLUDED.default_pickup_landmark;

    INSERT INTO farmer_private (
        user_id, cultivation_address, bank_name, bank_branch, account_holder_name, account_number, updated_at
    )
    VALUES (
        v_user_id, TRIM(p_cultivation_address), TRIM(p_bank_name), TRIM(p_bank_branch),
        TRIM(p_account_holder_name), TRIM(p_account_number), NOW()
    )
    ON CONFLICT (user_id) DO UPDATE SET
        cultivation_address = EXCLUDED.cultivation_address,
        bank_name = EXCLUDED.bank_name,
        bank_branch = EXCLUDED.bank_branch,
        account_holder_name = EXCLUDED.account_holder_name,
        account_number = EXCLUDED.account_number,
        updated_at = NOW();

    -- Ensure initial farmer stats row exists
    INSERT INTO farmer_stats (farmer_id)
    VALUES (v_user_id)
    ON CONFLICT (farmer_id) DO NOTHING;

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 4. Create Order Request
CREATE OR REPLACE FUNCTION create_order_request(
    p_listing_id UUID,
    p_quantity_kg NUMERIC,
    p_delivery_method TEXT,
    p_requested_date DATE,
    p_delivery_district_id INT DEFAULT NULL,
    p_delivery_city_id INT DEFAULT NULL,
    p_delivery_address TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_listing RECORD;
    v_settings RECORD;
    v_today_sl DATE;
    v_min_order NUMERIC;
    v_subtotal NUMERIC(12,2);
    v_expires_at TIMESTAMPTZ;
    v_order_id UUID;
    v_buyer_profile RECORD;
BEGIN
    IF v_buyer_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Check suspension
    SELECT * INTO v_buyer_profile FROM profiles WHERE id = v_buyer_id;
    IF v_buyer_profile.is_suspended THEN
        RAISE EXCEPTION 'Your account is suspended: %', COALESCE(v_buyer_profile.suspended_reason, 'Contact support');
    END IF;

    -- Lock listing for read
    SELECT * INTO v_listing FROM listings WHERE id = p_listing_id;
    IF NOT FOUND OR NOT v_listing.is_active OR v_listing.quantity_available <= 0 THEN
        RAISE EXCEPTION 'Listing is not available';
    END IF;

    IF v_listing.farmer_id = v_buyer_id THEN
        RAISE EXCEPTION 'You cannot order your own harvest';
    END IF;

    -- Current date in Sri Lanka
    v_today_sl := (NOW() AT TIME ZONE 'Asia/Colombo')::DATE;

    -- Rule: today >= harvest_date (buyers can only order on or after harvest date)
    IF v_today_sl < v_listing.harvest_date THEN
        RAISE EXCEPTION 'Listing is available only from harvest date: %', v_listing.harvest_date;
    END IF;

    -- Rule: requested_date >= max(today, harvest_date)
    IF p_requested_date < GREATEST(v_today_sl, v_listing.harvest_date) THEN
        RAISE EXCEPTION 'Requested date must be on or after %', GREATEST(v_today_sl, v_listing.harvest_date);
    END IF;

    -- Quantity validation
    v_min_order := LEAST(v_listing.min_order_kg, v_listing.quantity_available);
    IF p_quantity_kg < v_min_order OR p_quantity_kg > v_listing.quantity_available THEN
        RAISE EXCEPTION 'Order quantity must be between % kg and % kg', v_min_order, v_listing.quantity_available;
    END IF;

    IF p_delivery_method NOT IN ('buyer_arranged', 'farmer_delivery') THEN
        RAISE EXCEPTION 'Invalid delivery method';
    END IF;

    IF p_delivery_method = 'farmer_delivery' THEN
        IF p_delivery_district_id IS NULL OR p_delivery_city_id IS NULL OR NULLIF(TRIM(p_delivery_address), '') IS NULL THEN
            RAISE EXCEPTION 'Delivery district, city and address are required for farmer delivery';
        END IF;
    END IF;

    SELECT * INTO v_settings FROM app_settings WHERE id = 1;

    v_subtotal := ROUND(p_quantity_kg * v_listing.price_per_kg, 2);
    v_expires_at := LEAST(
        NOW() + (v_settings.farmer_response_hours || ' hours')::INTERVAL,
        (p_requested_date::TIMESTAMP + TIME '23:59:59') AT TIME ZONE 'Asia/Colombo'
    );

    INSERT INTO orders (
        buyer_id, farmer_id, listing_id, crop_name, price_per_kg, quantity_kg, subtotal,
        delivery_method, commission_rate, requested_date,
        delivery_district_id, delivery_city_id, status, stock_reserved, expires_at, requested_at
    )
    VALUES (
        v_buyer_id, v_listing.farmer_id, v_listing.id, v_listing.crop_name, v_listing.price_per_kg,
        p_quantity_kg, v_subtotal, p_delivery_method, v_settings.commission_rate,
        p_requested_date, p_delivery_district_id, p_delivery_city_id, 'requested', false,
        v_expires_at, NOW()
    )
    RETURNING id INTO v_order_id;

    -- Insert order private details (buyer address)
    INSERT INTO order_private_details (order_id, delivery_address, pickup_landmark)
    VALUES (v_order_id, TRIM(p_delivery_address), NULL);

    -- Insert system message in chat
    INSERT INTO messages (order_id, sender_id, body)
    VALUES (v_order_id, NULL, 'Order request created for ' || p_quantity_kg || ' kg of ' || v_listing.crop_name || '.');

    -- Notification to farmer
    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (
        v_listing.farmer_id, 'new_order_request', 'New Order Request',
        'New request for ' || p_quantity_kg || ' kg of ' || v_listing.crop_name || ' received.',
        v_order_id
    );

    RETURN v_order_id;
END;
$$;

-- 5. Farmer Accept Order
CREATE OR REPLACE FUNCTION farmer_accept_order(
    p_order_id UUID,
    p_delivery_mode TEXT DEFAULT NULL,
    p_delivery_fee NUMERIC DEFAULT 0.00,
    p_pickup_landmark TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
    v_listing RECORD;
    v_farmer RECORD;
    v_settings RECORD;
    v_landmark TEXT;
    v_comm_amount NUMERIC(12,2);
    v_total_amount NUMERIC(12,2);
    v_payout_amount NUMERIC(12,2);
    v_expires_at TIMESTAMPTZ;
BEGIN
    IF v_farmer_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Lock order row
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order not found';
    END IF;

    IF v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'requested' THEN
        RAISE EXCEPTION 'Order is not in requested status';
    END IF;

    IF v_order.expires_at <= NOW() THEN
        RAISE EXCEPTION 'Order request has expired';
    END IF;

    -- Lock listing row to prevent race condition overselling
    SELECT * INTO v_listing FROM listings WHERE id = v_order.listing_id FOR UPDATE;
    IF v_listing.quantity_available < v_order.quantity_kg THEN
        RAISE EXCEPTION 'Not enough stock available';
    END IF;

    -- Reserve stock
    UPDATE listings
    SET quantity_available = quantity_available - v_order.quantity_kg,
        updated_at = NOW()
    WHERE id = v_listing.id;

    SELECT * INTO v_settings FROM app_settings WHERE id = 1;
    SELECT * INTO v_farmer FROM farmers WHERE user_id = v_farmer_id;

    IF v_order.delivery_method = 'farmer_delivery' THEN
        IF p_delivery_mode NOT IN ('own_transport', 'pickme') THEN
            RAISE EXCEPTION 'Valid delivery mode is required (own_transport or pickme)';
        END IF;
        IF p_delivery_fee < 0 THEN
            RAISE EXCEPTION 'Delivery fee cannot be negative';
        END IF;
    ELSE
        -- Buyer arranged
        v_landmark := COALESCE(NULLIF(TRIM(p_pickup_landmark), ''), v_farmer.default_pickup_landmark);
        IF v_landmark IS NULL OR TRIM(v_landmark) = '' THEN
            RAISE EXCEPTION 'Pickup landmark is required for buyer arranged pickup';
        END IF;

        UPDATE order_private_details
        SET pickup_landmark = TRIM(v_landmark)
        WHERE order_id = p_order_id;
    END IF;

    -- Financial calculations:
    -- Commission is 3% of subtotal ONLY, never on delivery fee!
    v_comm_amount := ROUND(v_order.subtotal * v_order.commission_rate, 2);
    v_total_amount := v_order.subtotal + COALESCE(p_delivery_fee, 0.00);
    v_payout_amount := (v_order.subtotal - v_comm_amount) + COALESCE(p_delivery_fee, 0.00);
    v_expires_at := NOW() + (v_settings.buyer_payment_hours || ' hours')::INTERVAL;

    UPDATE orders
    SET status = 'accepted',
        stock_reserved = true,
        farmer_delivery_mode = p_delivery_mode,
        delivery_fee = COALESCE(p_delivery_fee, 0.00),
        commission_amount = v_comm_amount,
        total_amount = v_total_amount,
        farmer_payout_amount = v_payout_amount,
        accepted_at = NOW(),
        expires_at = v_expires_at,
        updated_at = NOW()
    WHERE id = p_order_id;

    -- System message in chat
    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order accepted by farmer. Total amount: Rs. ' || to_char(v_total_amount, 'FM999,999,990.00') || '. Please complete payment within ' || v_settings.buyer_payment_hours || ' hours.');

    -- Notification to buyer
    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (
        v_order.buyer_id, 'order_accepted', 'Order Accepted',
        'Your order ' || v_order.order_number || ' has been accepted. Please pay within 2 hours.',
        p_order_id
    );

    RETURN jsonb_build_object('success', true, 'total_amount', v_total_amount);
END;
$$;

-- 6. Helper: Restore stock atomically if reserved
CREATE OR REPLACE FUNCTION restore_order_stock_if_needed(p_order_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF v_order.stock_reserved THEN
        UPDATE listings
        SET quantity_available = quantity_available + v_order.quantity_kg,
            updated_at = NOW()
        WHERE id = v_order.listing_id;

        UPDATE orders
        SET stock_reserved = false
        WHERE id = p_order_id;
    END IF;
END;
$$;

-- 7. Farmer Reject Order
CREATE OR REPLACE FUNCTION farmer_reject_order(p_order_id UUID, p_reason TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'requested' THEN
        RAISE EXCEPTION 'Order is not in requested status';
    END IF;

    UPDATE orders
    SET status = 'rejected',
        ended_at = NOW(),
        ended_by = 'farmer',
        end_reason = TRIM(p_reason),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order request rejected by farmer.' || CASE WHEN p_reason IS NOT NULL AND TRIM(p_reason) <> '' THEN ' Reason: ' || TRIM(p_reason) ELSE '' END);

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'order_rejected', 'Order Rejected', 'Your order ' || v_order.order_number || ' was declined by the farmer.', p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 8. Buyer Cancel Order (Pre-payment only)
CREATE OR REPLACE FUNCTION buyer_cancel_order(p_order_id UUID, p_reason TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.buyer_id <> v_buyer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status NOT IN ('requested', 'accepted') THEN
        RAISE EXCEPTION 'Cannot cancel order after payment';
    END IF;

    -- Restore stock if reserved
    PERFORM restore_order_stock_if_needed(p_order_id);

    UPDATE orders
    SET status = 'cancelled',
        ended_at = NOW(),
        ended_by = 'buyer',
        end_reason = TRIM(p_reason),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order cancelled by buyer.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'order_cancelled', 'Order Cancelled', 'Order ' || v_order.order_number || ' was cancelled by the buyer.', p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 9. Farmer Cancel Order (Allowed in requested, accepted, paid, ready; impacts reliability score)
CREATE OR REPLACE FUNCTION farmer_cancel_order(p_order_id UUID, p_reason TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
    v_refund_req BOOLEAN := false;
BEGIN
    IF NULLIF(TRIM(p_reason), '') IS NULL THEN
        RAISE EXCEPTION 'A cancellation reason is required';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status NOT IN ('requested', 'accepted', 'paid', 'ready') THEN
        RAISE EXCEPTION 'Cannot cancel order in status %', v_order.status;
    END IF;

    -- If paid or ready, buyer must be refunded
    IF v_order.status IN ('paid', 'ready') THEN
        v_refund_req := true;
    END IF;

    -- Restore stock
    PERFORM restore_order_stock_if_needed(p_order_id);

    UPDATE orders
    SET status = 'cancelled',
        ended_at = NOW(),
        ended_by = 'farmer',
        end_reason = TRIM(p_reason),
        refund_status = CASE WHEN v_refund_req THEN 'required' ELSE refund_status END,
        refund_amount = CASE WHEN v_refund_req THEN v_order.total_amount ELSE 0.00 END,
        payout_status = CASE WHEN v_refund_req THEN 'void' ELSE payout_status END,
        updated_at = NOW()
    WHERE id = p_order_id;

    -- Recompute farmer stats (this cancellation reduces fulfillment rate)
    PERFORM recompute_farmer_stats(v_farmer_id);

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order cancelled by farmer. Reason: ' || TRIM(p_reason));

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (
        v_order.buyer_id, 'order_cancelled', 'Order Cancelled by Farmer',
        'Order ' || v_order.order_number || ' was cancelled by farmer. ' ||
        CASE WHEN v_refund_req THEN 'Full refund of Rs. ' || v_order.total_amount || ' will be processed.' ELSE '' END,
        p_order_id
    );

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 10. Mark Order Paid (Called by PayHere webhook / edge function)
CREATE OR REPLACE FUNCTION mark_order_paid(
    p_order_id UUID,
    p_payhere_payment_id TEXT,
    p_amount NUMERIC,
    p_status_code INT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order not found';
    END IF;

    -- Check if order is in expected status
    IF v_order.status = 'accepted' AND v_order.expires_at > NOW() THEN
        UPDATE orders
        SET status = 'paid',
            paid_at = NOW(),
            payout_status = 'held',
            updated_at = NOW()
        WHERE id = p_order_id;

        INSERT INTO messages (order_id, sender_id, body)
        VALUES (p_order_id, NULL, 'Payment of Rs. ' || to_char(p_amount, 'FM999,999,990.00') || ' verified successfully.');

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.farmer_id, 'payment_received', 'Payment Received', 'Buyer has completed payment for order ' || v_order.order_number || '. You may now prepare the harvest.', p_order_id);

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.buyer_id, 'payment_confirmed', 'Payment Confirmed', 'Payment of Rs. ' || to_char(p_amount, 'FM999,999,990.00') || ' was confirmed for order ' || v_order.order_number || '.', p_order_id);

        RETURN jsonb_build_object('success', true, 'status', 'paid');
    ELSE
        -- Late payment or expired order: mark payment recorded, flag refund required
        UPDATE orders
        SET refund_status = 'required',
            refund_amount = p_amount,
            payout_status = 'void',
            updated_at = NOW()
        WHERE id = p_order_id;

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.buyer_id, 'late_payment_refund', 'Payment Received Late', 'Payment for order ' || v_order.order_number || ' arrived after expiration. A refund will be issued.', p_order_id);

        RETURN jsonb_build_object('success', true, 'status', 'refund_required');
    END IF;
END;
$$;

-- 11. Farmer Mark Ready
CREATE OR REPLACE FUNCTION farmer_mark_ready(p_order_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'paid' THEN
        RAISE EXCEPTION 'Order must be paid before marking ready';
    END IF;

    UPDATE orders
    SET status = 'ready',
        ready_at = NOW(),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Harvest is packed and ready.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'order_ready', 'Order Ready', 'Your order ' || v_order.order_number || ' is packed and ready.', p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 12. Farmer Mark Dispatched (Farmer delivery only)
CREATE OR REPLACE FUNCTION farmer_mark_dispatched(p_order_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'ready' THEN
        RAISE EXCEPTION 'Order must be in ready status to dispatch';
    END IF;

    IF v_order.delivery_method <> 'farmer_delivery' THEN
        RAISE EXCEPTION 'Dispatch is only for farmer delivery';
    END IF;

    UPDATE orders
    SET status = 'dispatched',
        dispatched_at = NOW(),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order dispatched for delivery.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'order_dispatched', 'Order Dispatched', 'Your order ' || v_order.order_number || ' is on the way.', p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 13. Farmer Mark Delivered / Handed Over
CREATE OR REPLACE FUNCTION farmer_mark_delivered(p_order_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := auth.uid();
    v_order RECORD;
    v_is_on_time BOOLEAN;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.farmer_id <> v_farmer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.delivery_method = 'farmer_delivery' AND v_order.status <> 'dispatched' THEN
        RAISE EXCEPTION 'Farmer delivery orders must be dispatched before delivered';
    ELSIF v_order.delivery_method = 'buyer_arranged' AND v_order.status <> 'ready' THEN
        RAISE EXCEPTION 'Buyer arranged orders must be ready before handed over';
    END IF;

    -- On-time rule: delivered_at date in Asia/Colombo <= requested_date
    v_is_on_time := ((NOW() AT TIME ZONE 'Asia/Colombo')::DATE <= v_order.requested_date);

    UPDATE orders
    SET status = 'delivered',
        delivered_at = NOW(),
        is_on_time = v_is_on_time,
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order marked as delivered / handed over.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'order_delivered', 'Order Delivered', 'Order ' || v_order.order_number || ' was marked as delivered. Please confirm receipt within 24 hours.', p_order_id);

    RETURN jsonb_build_object('success', true, 'is_on_time', v_is_on_time);
END;
$$;

-- 14. Buyer Confirm Delivered (Completes order)
CREATE OR REPLACE FUNCTION buyer_confirm_delivered(p_order_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.buyer_id <> v_buyer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'delivered' THEN
        RAISE EXCEPTION 'Order is not in delivered status';
    END IF;

    UPDATE orders
    SET status = 'completed',
        completed_at = NOW(),
        payout_status = 'pending',
        payout_amount = v_order.farmer_payout_amount,
        updated_at = NOW()
    WHERE id = p_order_id;

    PERFORM recompute_farmer_stats(v_order.farmer_id);

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Order receipt confirmed by buyer. Order completed.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'order_completed', 'Order Completed', 'Buyer confirmed receipt for ' || v_order.order_number || '. Payout of Rs. ' || v_order.farmer_payout_amount || ' is now pending release.', p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 15. Buyer Raise Dispute
CREATE OR REPLACE FUNCTION buyer_raise_dispute(
    p_order_id UUID,
    p_reason TEXT,
    p_photos TEXT[] DEFAULT '{}'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_order RECORD;
    v_settings RECORD;
    v_today_sl DATE;
    v_dispute_id UUID;
BEGIN
    IF NULLIF(TRIM(p_reason), '') IS NULL OR char_length(TRIM(p_reason)) < 10 THEN
        RAISE EXCEPTION 'Dispute reason must be at least 10 characters';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND OR v_order.buyer_id <> v_buyer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    SELECT * INTO v_settings FROM app_settings WHERE id = 1;
    v_today_sl := (NOW() AT TIME ZONE 'Asia/Colombo')::DATE;

    -- Check conditions per section 1 state machine:
    -- In paid/ready/dispatched: allowed ONLY when today in Asia/Colombo is strictly after requested_date
    IF v_order.status IN ('paid', 'ready', 'dispatched') THEN
        IF v_today_sl <= v_order.requested_date THEN
            RAISE EXCEPTION 'You can only dispute unfulfilled orders after the requested delivery date (%)', v_order.requested_date;
        END IF;
    -- In delivered: allowed within dispute_window_hours of delivered_at
    ELSIF v_order.status = 'delivered' THEN
        IF NOW() > (v_order.delivered_at + (v_settings.dispute_window_hours || ' hours')::INTERVAL) THEN
            RAISE EXCEPTION 'The % hour dispute window has expired', v_settings.dispute_window_hours;
        END IF;
    ELSE
        RAISE EXCEPTION 'Disputes cannot be raised in status %', v_order.status;
    END IF;

    UPDATE orders
    SET status = 'disputed',
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO disputes (order_id, raised_by, reason, photos, status)
    VALUES (p_order_id, v_buyer_id, TRIM(p_reason), p_photos, 'open')
    RETURNING id INTO v_dispute_id;

    INSERT INTO messages (order_id, sender_id, body)
    VALUES (p_order_id, NULL, 'Dispute raised by buyer. Admin mediation pending.');

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'dispute_raised', 'Dispute Raised', 'Buyer raised a dispute for order ' || v_order.order_number || '.', p_order_id);

    RETURN v_dispute_id;
END;
$$;

-- 16. Admin Resolve Dispute
CREATE OR REPLACE FUNCTION admin_resolve_dispute(
    p_dispute_id UUID,
    p_resolution TEXT,
    p_refund_amount NUMERIC DEFAULT 0.00,
    p_note TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
    v_dispute RECORD;
    v_order RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'Admin authorization required';
    END IF;

    IF NULLIF(TRIM(p_note), '') IS NULL THEN
        RAISE EXCEPTION 'Admin note is required to resolve a dispute';
    END IF;

    SELECT * INTO v_dispute FROM disputes WHERE id = p_dispute_id FOR UPDATE;
    IF NOT FOUND OR v_dispute.status <> 'open' THEN
        RAISE EXCEPTION 'Dispute not found or already resolved';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = v_dispute.order_id FOR UPDATE;

    IF p_resolution = 'full_refund' THEN
        -- Full refund: total_amount refunded, farmer payout void. (Stock is NOT restored)
        UPDATE orders
        SET status = 'refunded',
            refund_status = 'required',
            refund_amount = v_order.total_amount,
            payout_status = 'void',
            payout_amount = 0.00,
            ended_at = NOW(),
            ended_by = 'admin',
            end_reason = 'Dispute resolved: full refund',
            updated_at = NOW()
        WHERE id = v_order.id;

    ELSIF p_resolution = 'partial_refund' THEN
        IF p_refund_amount <= 0 OR p_refund_amount >= v_order.total_amount THEN
            RAISE EXCEPTION 'Partial refund amount must be between 0 and total amount (%)', v_order.total_amount;
        END IF;

        -- Partial refund: payout to farmer reduced, commission unchanged
        UPDATE orders
        SET status = 'completed',
            completed_at = NOW(),
            refund_status = 'required',
            refund_amount = p_refund_amount,
            payout_status = 'pending',
            payout_amount = GREATEST(0.00, v_order.farmer_payout_amount - p_refund_amount),
            ended_at = NOW(),
            ended_by = 'admin',
            end_reason = 'Dispute resolved: partial refund',
            updated_at = NOW()
        WHERE id = v_order.id;

    ELSIF p_resolution = 'release_to_farmer' THEN
        -- No refund: full payout pending to farmer
        UPDATE orders
        SET status = 'completed',
            completed_at = NOW(),
            payout_status = 'pending',
            payout_amount = v_order.farmer_payout_amount,
            refund_status = 'none',
            ended_at = NOW(),
            ended_by = 'admin',
            end_reason = 'Dispute resolved: release to farmer',
            updated_at = NOW()
        WHERE id = v_order.id;
    ELSE
        RAISE EXCEPTION 'Invalid resolution type: %', p_resolution;
    END IF;

    UPDATE disputes
    SET status = 'resolved',
        resolution = p_resolution,
        refund_amount = p_refund_amount,
        admin_note = TRIM(p_note),
        resolved_by = v_admin_id,
        resolved_at = NOW(),
        updated_at = NOW()
    WHERE id = p_dispute_id;

    PERFORM recompute_farmer_stats(v_order.farmer_id);

    -- Log to audit
    INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
    VALUES (v_admin_id, 'resolve_dispute', 'disputes', p_dispute_id::TEXT, jsonb_build_object('order_id', v_order.id, 'resolution', p_resolution, 'refund_amount', p_refund_amount, 'note', p_note));

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'dispute_resolved', 'Dispute Resolved', 'Dispute for order ' || v_order.order_number || ' resolved with outcome: ' || p_resolution, v_order.id);

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'dispute_resolved', 'Dispute Resolved', 'Dispute for order ' || v_order.order_number || ' resolved with outcome: ' || p_resolution, v_order.id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 17. Buyer Submit Review
CREATE OR REPLACE FUNCTION buyer_submit_review(
    p_order_id UUID,
    p_rating SMALLINT,
    p_comment TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_order RECORD;
    v_review_id UUID;
BEGIN
    IF p_rating < 1 OR p_rating > 5 THEN
        RAISE EXCEPTION 'Rating must be between 1 and 5';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id;
    IF NOT FOUND OR v_order.buyer_id <> v_buyer_id THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    IF v_order.status <> 'completed' THEN
        RAISE EXCEPTION 'Reviews can only be submitted for completed orders';
    END IF;

    INSERT INTO reviews (order_id, farmer_id, buyer_id, rating, comment)
    VALUES (p_order_id, v_order.farmer_id, v_buyer_id, p_rating, NULLIF(TRIM(p_comment), ''))
    RETURNING id INTO v_review_id;

    PERFORM recompute_farmer_stats(v_order.farmer_id);

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'review_received', 'New Review', 'You received a ' || p_rating || '-star rating for order ' || v_order.order_number || '.', p_order_id);

    RETURN v_review_id;
END;
$$;

-- 18. Recompute Farmer Stats
CREATE OR REPLACE FUNCTION recompute_farmer_stats(p_farmer_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_completed_orders INT := 0;
    v_terminal_accepted INT := 0;
    v_fulfilled INT := 0;
    v_fulfillment_rate NUMERIC(4,3) := 0.000;
    v_delivered INT := 0;
    v_on_time INT := 0;
    v_on_time_rate NUMERIC(4,3) := 0.000;
    v_rating_avg NUMERIC(3,2) := 0.00;
    v_rating_count INT := 0;
    v_reliability_score NUMERIC(4,1) := 0.0;
    v_is_top_farmer BOOLEAN := false;
BEGIN
    -- Completed count
    SELECT COUNT(*) INTO v_completed_orders
    FROM orders
    WHERE farmer_id = p_farmer_id AND status = 'completed';

    -- terminal_accepted_orders: accepted_at is NOT NULL and status IN ('completed', 'refunded', 'cancelled' by farmer)
    SELECT COUNT(*) INTO v_terminal_accepted
    FROM orders
    WHERE farmer_id = p_farmer_id
      AND accepted_at IS NOT NULL
      AND (
          status IN ('completed', 'refunded')
          OR (status = 'cancelled' AND ended_by = 'farmer')
      );

    -- fulfilled_orders: reached delivered_at and status <> 'refunded'
    SELECT COUNT(*) INTO v_fulfilled
    FROM orders
    WHERE farmer_id = p_farmer_id
      AND delivered_at IS NOT NULL
      AND status <> 'refunded';

    IF v_terminal_accepted > 0 THEN
        v_fulfillment_rate := ROUND((v_fulfilled::NUMERIC / v_terminal_accepted::NUMERIC), 3);
    END IF;

    -- delivered_orders & on_time_orders
    SELECT COUNT(*), COUNT(*) FILTER (WHERE is_on_time = true)
    INTO v_delivered, v_on_time
    FROM orders
    WHERE farmer_id = p_farmer_id AND delivered_at IS NOT NULL;

    IF v_delivered > 0 THEN
        v_on_time_rate := ROUND((v_on_time::NUMERIC / v_delivered::NUMERIC), 3);
    END IF;

    -- Reviews
    SELECT COALESCE(ROUND(AVG(rating)::NUMERIC, 2), 0.00), COUNT(*)
    INTO v_rating_avg, v_rating_count
    FROM reviews
    WHERE farmer_id = p_farmer_id;

    -- Reliability score = 50 * fulfillment_rate + 30 * on_time_rate + 20 * (rating_avg / 5.0)
    v_reliability_score := ROUND(
        (50.0 * v_fulfillment_rate) +
        (30.0 * v_on_time_rate) +
        (20.0 * (v_rating_avg / 5.0)),
        1
    );

    -- Top Farmer rule: completed >= 20, rating_avg >= 4.5, fulfillment_rate >= 0.90
    IF v_completed_orders >= 20 AND v_rating_avg >= 4.50 AND v_fulfillment_rate >= 0.900 THEN
        v_is_top_farmer := true;
    END IF;

    INSERT INTO farmer_stats (
        farmer_id, completed_orders, terminal_accepted_orders, fulfilled_orders,
        fulfillment_rate, delivered_orders, on_time_orders, on_time_rate,
        rating_avg, rating_count, reliability_score, is_top_farmer, updated_at
    )
    VALUES (
        p_farmer_id, v_completed_orders, v_terminal_accepted, v_fulfilled,
        v_fulfillment_rate, v_delivered, v_on_time, v_on_time_rate,
        v_rating_avg, v_rating_count, v_reliability_score, v_is_top_farmer, NOW()
    )
    ON CONFLICT (farmer_id) DO UPDATE SET
        completed_orders = EXCLUDED.completed_orders,
        terminal_accepted_orders = EXCLUDED.terminal_accepted_orders,
        fulfilled_orders = EXCLUDED.fulfilled_orders,
        fulfillment_rate = EXCLUDED.fulfillment_rate,
        delivered_orders = EXCLUDED.delivered_orders,
        on_time_orders = EXCLUDED.on_time_orders,
        on_time_rate = EXCLUDED.on_time_rate,
        rating_avg = EXCLUDED.rating_avg,
        rating_count = EXCLUDED.rating_count,
        reliability_score = EXCLUDED.reliability_score,
        is_top_farmer = EXCLUDED.is_top_farmer,
        updated_at = NOW();
END;
$$;

-- 19. Search Listings (Strictly filters by farmer's cultivation district)
CREATE OR REPLACE FUNCTION search_listings(
    p_district_id INT,
    p_crop_query TEXT DEFAULT NULL,
    p_min_price NUMERIC DEFAULT NULL,
    p_max_price NUMERIC DEFAULT NULL,
    p_sort TEXT DEFAULT 'rating',
    p_cursor TIMESTAMPTZ DEFAULT NULL,
    p_limit INT DEFAULT 20
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
    IF p_district_id IS NULL THEN
        RAISE EXCEPTION 'Cultivation district_id is required';
    END IF;

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
        COALESCE(fs.rating_avg, 0.00) AS rating_avg,
        COALESCE(fs.rating_count, 0) AS rating_count,
        COALESCE(fs.reliability_score, 0.0) AS reliability_score,
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
      AND p.is_suspended = false
      AND f.cultivation_district_id = p_district_id
      AND (p_crop_query IS NULL OR l.crop_name_key ILIKE '%' || LOWER(TRIM(p_crop_query)) || '%')
      AND (p_min_price IS NULL OR l.price_per_kg >= p_min_price)
      AND (p_max_price IS NULL OR l.price_per_kg <= p_max_price)
      AND (p_cursor IS NULL OR l.created_at < p_cursor)
    ORDER BY
        CASE WHEN p_sort = 'rating' THEN COALESCE(fs.rating_avg, 0) END DESC NULLS LAST,
        l.created_at DESC
    LIMIT p_limit;
END;
$$;

-- 20. Run Order Timers (Cron routine: idempotent & safe to run concurrently)
CREATE OR REPLACE FUNCTION run_order_timers()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_order RECORD;
    v_settings RECORD;
    v_expired_requested INT := 0;
    v_expired_accepted INT := 0;
    v_auto_completed INT := 0;
BEGIN
    SELECT * INTO v_settings FROM app_settings WHERE id = 1;

    -- 1. Expire requested orders past expires_at
    FOR v_order IN
        SELECT * FROM orders
        WHERE status = 'requested' AND expires_at <= NOW()
        FOR UPDATE SKIP LOCKED
    LOOP
        UPDATE orders
        SET status = 'expired',
            ended_at = NOW(),
            ended_by = 'system',
            end_reason = 'Farmer response window expired',
            updated_at = NOW()
        WHERE id = v_order.id;

        INSERT INTO messages (order_id, sender_id, body)
        VALUES (v_order.id, NULL, 'Order request expired due to inactivity.');

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.buyer_id, 'order_expired', 'Order Expired', 'Order ' || v_order.order_number || ' expired without farmer acceptance.', v_order.id);

        v_expired_requested := v_expired_requested + 1;
    END LOOP;

    -- 2. Expire accepted orders past expires_at (RESTORE STOCK!)
    FOR v_order IN
        SELECT * FROM orders
        WHERE status = 'accepted' AND expires_at <= NOW()
        FOR UPDATE SKIP LOCKED
    LOOP
        PERFORM restore_order_stock_if_needed(v_order.id);

        UPDATE orders
        SET status = 'expired',
            ended_at = NOW(),
            ended_by = 'system',
            end_reason = 'Buyer payment window expired',
            updated_at = NOW()
        WHERE id = v_order.id;

        INSERT INTO messages (order_id, sender_id, body)
        VALUES (v_order.id, NULL, 'Order cancelled automatically due to lack of payment.');

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.farmer_id, 'order_expired', 'Order Expired', 'Order ' || v_order.order_number || ' expired because buyer did not pay in time. Stock restored.', v_order.id);

        v_expired_accepted := v_expired_accepted + 1;
    END LOOP;

    -- 3. Auto-complete delivered orders past dispute window with no dispute
    FOR v_order IN
        SELECT o.* FROM orders o
        WHERE o.status = 'delivered'
          AND o.delivered_at + (v_settings.dispute_window_hours || ' hours')::INTERVAL <= NOW()
          AND NOT EXISTS (SELECT 1 FROM disputes d WHERE d.order_id = o.id)
        FOR UPDATE SKIP LOCKED
    LOOP
        UPDATE orders
        SET status = 'completed',
            completed_at = NOW(),
            payout_status = 'pending',
            payout_amount = v_order.farmer_payout_amount,
            updated_at = NOW()
        WHERE id = v_order.id;

        PERFORM recompute_farmer_stats(v_order.farmer_id);

        INSERT INTO messages (order_id, sender_id, body)
        VALUES (v_order.id, NULL, 'Order auto-completed after dispute window.');

        INSERT INTO notifications (user_id, type, title, body, order_id)
        VALUES (v_order.farmer_id, 'order_completed', 'Order Completed', 'Order ' || v_order.order_number || ' auto-completed. Payout of Rs. ' || v_order.farmer_payout_amount || ' is pending.', v_order.id);

        v_auto_completed := v_auto_completed + 1;
    END LOOP;

    RETURN jsonb_build_object(
        'expired_requested', v_expired_requested,
        'expired_accepted', v_expired_accepted,
        'auto_completed', v_auto_completed
    );
END;
$$;

-- 21. Admin Payout Actions
CREATE OR REPLACE FUNCTION admin_mark_payout_paid(p_order_id UUID, p_reference TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'Admin authorization required';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order not found';
    END IF;

    IF v_order.payout_status <> 'pending' THEN
        RAISE EXCEPTION 'Order payout is not in pending status';
    END IF;

    UPDATE orders
    SET payout_status = 'paid_out',
        payout_reference = TRIM(p_reference),
        paid_out_at = NOW(),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
    VALUES (v_admin_id, 'payout_paid', 'orders', p_order_id::TEXT, jsonb_build_object('reference', p_reference, 'amount', v_order.payout_amount));

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.farmer_id, 'payout_sent', 'Payout Dispatched', 'Payout of Rs. ' || v_order.payout_amount || ' for order ' || v_order.order_number || ' has been transferred. Ref: ' || p_reference, p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 22. Admin Refund Actions
CREATE OR REPLACE FUNCTION admin_mark_refunded(p_order_id UUID, p_reference TEXT, p_amount NUMERIC)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
    v_order RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'Admin authorization required';
    END IF;

    SELECT * INTO v_order FROM orders WHERE id = p_order_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Order not found';
    END IF;

    UPDATE orders
    SET refund_status = 'refunded',
        refund_reference = TRIM(p_reference),
        refund_amount = p_amount,
        refunded_at = NOW(),
        updated_at = NOW()
    WHERE id = p_order_id;

    INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
    VALUES (v_admin_id, 'mark_refunded', 'orders', p_order_id::TEXT, jsonb_build_object('reference', p_reference, 'amount', p_amount));

    INSERT INTO notifications (user_id, type, title, body, order_id)
    VALUES (v_order.buyer_id, 'refund_processed', 'Refund Processed', 'Refund of Rs. ' || p_amount || ' for order ' || v_order.order_number || ' has been processed. Ref: ' || p_reference, p_order_id);

    RETURN jsonb_build_object('success', true);
END;
$$;

-- 23. Admin Suspend / Unsuspend User
CREATE OR REPLACE FUNCTION admin_suspend_user(p_user_id UUID, p_reason TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'Admin authorization required';
    END IF;

    IF NULLIF(TRIM(p_reason), '') IS NULL THEN
        RAISE EXCEPTION 'Suspension reason is required';
    END IF;

    UPDATE profiles
    SET is_suspended = true,
        suspended_reason = TRIM(p_reason),
        updated_at = NOW()
    WHERE id = p_user_id;

    -- Deactivate user's active listings
    UPDATE listings
    SET is_active = false,
        updated_at = NOW()
    WHERE farmer_id = p_user_id;

    INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
    VALUES (v_admin_id, 'suspend_user', 'profiles', p_user_id::TEXT, jsonb_build_object('reason', p_reason));

    INSERT INTO notifications (user_id, type, title, body)
    VALUES (p_user_id, 'account_suspended', 'Account Suspended', 'Your account has been suspended: ' || TRIM(p_reason));

    RETURN jsonb_build_object('success', true);
END;
$$;

CREATE OR REPLACE FUNCTION admin_unsuspend_user(p_user_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'Admin authorization required';
    END IF;

    UPDATE profiles
    SET is_suspended = false,
        suspended_reason = NULL,
        updated_at = NOW()
    WHERE id = p_user_id;

    INSERT INTO audit_log (admin_id, action, entity, entity_id, details)
    VALUES (v_admin_id, 'unsuspend_user', 'profiles', p_user_id::TEXT, '{}'::jsonb);

    INSERT INTO notifications (user_id, type, title, body)
    VALUES (p_user_id, 'account_reactivated', 'Account Reactivated', 'Your account has been restored to active status.');

    RETURN jsonb_build_object('success', true);
END;
$$;
-- AgroMarket Phase 1: Sri Lankan 25 Districts & Comprehensive Cities/DS Divisions

INSERT INTO districts (id, name, province) VALUES
(1, 'Colombo', 'Western'),
(2, 'Gampaha', 'Western'),
(3, 'Kalutara', 'Western'),
(4, 'Kandy', 'Central'),
(5, 'Matale', 'Central'),
(6, 'Nuwara Eliya', 'Central'),
(7, 'Galle', 'Southern'),
(8, 'Matara', 'Southern'),
(9, 'Hambantota', 'Southern'),
(10, 'Jaffna', 'Northern'),
(11, 'Kilinochchi', 'Northern'),
(12, 'Mannar', 'Northern'),
(13, 'Vavuniya', 'Northern'),
(14, 'Mullaitivu', 'Northern'),
(15, 'Batticaloa', 'Eastern'),
(16, 'Ampara', 'Eastern'),
(17, 'Trincomalee', 'Eastern'),
(18, 'Kurunegala', 'North Western'),
(19, 'Puttalam', 'North Western'),
(20, 'Anuradhapura', 'North Central'),
(21, 'Polonnaruwa', 'North Central'),
(22, 'Badulla', 'Uva'),
(23, 'Monaragala', 'Uva'),
(24, 'Ratnapura', 'Sabaragamuwa'),
(25, 'Kegalle', 'Sabaragamuwa')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence to 26
SELECT setval('districts_id_seq', 25);

-- Cities and Divisional Secretariat towns for all 25 districts
INSERT INTO cities (district_id, name, postal_code) VALUES
-- 1. Colombo
(1, 'Colombo 01 - Fort', '00100'),
(1, 'Colombo 02 - Slave Island', '00200'),
(1, 'Colombo 03 - Kollupitiya', '00300'),
(1, 'Colombo 04 - Bambalapitiya', '00400'),
(1, 'Colombo 05 - Havelock Town', '00500'),
(1, 'Colombo 06 - Wellawatte', '00600'),
(1, 'Colombo 07 - Cinnamon Gardens', '00700'),
(1, 'Colombo 08 - Borella', '00800'),
(1, 'Dehiwala', '10350'),
(1, 'Mount Lavinia', '10370'),
(1, 'Moratuwa', '10400'),
(1, 'Kotte (Sri Jayawardenepura)', '10100'),
(1, 'Nugegoda', '10250'),
(1, 'Maharagama', '10280'),
(1, 'Kesbewa', '10300'),
(1, 'Homagama', '10200'),
(1, 'Kaduwela', '10640'),
(1, 'Avissawella', '10700'),
(1, 'Padukka', '10500'),
(1, 'Battaramulla', '10120'),
(1, 'Malabe', '10115'),
(1, 'Piliyandala', '10300'),

-- 2. Gampaha
(2, 'Gampaha', '11000'),
(2, 'Negombo', '11500'),
(2, 'Kelaniya', '11600'),
(2, 'Wattala', '11300'),
(2, 'Ja-Ela', '11350'),
(2, 'Katunayake', '11450'),
(2, 'Minuwangoda', '11550'),
(2, 'Mirigama', '11200'),
(2, 'Divulapitiya', '11210'),
(2, 'Attanagalla', '11120'),
(2, 'Biyagama', '11650'),
(2, 'Mahara', '11010'),
(2, 'Dompe', '11680'),
(2, 'Kiribathgoda', '11850'),
(2, 'Kadawatha', '11850'),

-- 3. Kalutara
(3, 'Kalutara', '12000'),
(3, 'Panadura', '12500'),
(3, 'Horana', '12400'),
(3, 'Beruwala', '12070'),
(3, 'Aluthgama', '12080'),
(3, 'Matugama', '12100'),
(3, 'Bandaragama', '12530'),
(3, 'Bulathsinhala', '12390'),
(3, 'Ingiriya', '12440'),
(3, 'Agalawatta', '12200'),
(3, 'Dodangoda', '12020'),
(3, 'Walallavita', '12180'),

-- 4. Kandy
(4, 'Kandy', '20000'),
(4, 'Peradeniya', '20400'),
(4, 'Gampola', '20500'),
(4, 'Katugastota', '20800'),
(4, 'Kundasale', '20168'),
(4, 'Akurana', '20850'),
(4, 'Nawalapitiya', '20650'),
(4, 'Teldeniya', '20900'),
(4, 'Harispattuwa', '20100'),
(4, 'Poojapitiya', '20110'),
(4, 'Panvila', '20830'),
(4, 'Udunuwara', '20700'),
(4, 'Yatinuwara', '20400'),
(4, 'Alawatugoda', '20140'),

-- 5. Matale
(5, 'Matale', '21000'),
(5, 'Dambulla', '21100'),
(5, 'Galewela', '21200'),
(5, 'Naula', '21260'),
(5, 'Rattota', '21400'),
(5, 'Ukuwela', '21300'),
(5, 'Sigiriya', '21120'),
(5, 'Pallepola', '21152'),
(5, 'Wilgamuwa', '21600'),
(5, 'Laggala-Pallegama', '21520'),

-- 6. Nuwara Eliya
(6, 'Nuwara Eliya', '22200'),
(6, 'Hatton', '22000'),
(6, 'Talawakele', '22100'),
(6, 'Walapane', '22270'),
(6, 'Hanguranketha', '22280'),
(6, 'Ginigathena', '22080'),
(6, 'Maskeliya', '22070'),
(6, 'Kotagala', '22090'),
(6, 'Ragala', '22220'),
(6, 'Pundaluoya', '22120'),

-- 7. Galle
(7, 'Galle', '80000'),
(7, 'Ambalangoda', '80300'),
(7, 'Hikkaduwa', '80240'),
(7, 'Elpitiya', '80400'),
(7, 'Baddegama', '80200'),
(7, 'Karapitiya', '80000'),
(7, 'Bentota', '80500'),
(7, 'Balapitiya', '80550'),
(7, 'Neluwa', '80082'),
(7, 'Nagoda', '80110'),
(7, 'Habaraduwa', '80630'),
(7, 'Akmeemana', '80000'),

-- 8. Matara
(8, 'Matara', '81000'),
(8, 'Weligama', '81700'),
(8, 'Akuressa', '81400'),
(8, 'Dikwella', '81170'),
(8, 'Deniyaya', '81500'),
(8, 'Hakmana', '81300'),
(8, 'Devinuwara', '81160'),
(8, 'Kamburupitiya', '81050'),
(8, 'Morawaka', '81470'),
(8, 'Thihagoda', '81280'),

-- 9. Hambantota
(9, 'Hambantota', '82000'),
(9, 'Tangalle', '82200'),
(9, 'Tissamaharama', '82600'),
(9, 'Ambalantota', '82100'),
(9, 'Beliatta', '82240'),
(9, 'Walasmulla', '82250'),
(9, 'Weeraketiya', '82240'),
(9, 'Sooriyawewa', '82010'),
(9, 'Lunugamvehera', '82634'),
(9, 'Angunakolapelessa', '82220'),

-- 10. Jaffna
(10, 'Jaffna', '40000'),
(10, 'Chavakachcheri', '40500'),
(10, 'Point Pedro', '40160'),
(10, 'Nallur', '40000'),
(10, 'Valvettithurai', '40180'),
(10, 'Kopay', '40220'),
(10, 'Karainagar', '40070'),
(10, 'Chankanai', '40000'),
(10, 'Sandilipay', '40000'),
(10, 'Tellippalai', '40280'),
(10, 'Velanai', '40080'),

-- 11. Kilinochchi
(11, 'Kilinochchi', '44000'),
(11, 'Pallai', '44040'),
(11, 'Poonakary', '44000'),
(11, 'Karachchi', '44000'),
(11, 'Kandawalai', '44000'),

-- 12. Mannar
(12, 'Mannar', '41000'),
(12, 'Nanaddan', '41000'),
(12, 'Musali', '41000'),
(12, 'Madhu', '41000'),
(12, 'Manthai West', '41000'),

-- 13. Vavuniya
(13, 'Vavuniya', '43000'),
(13, 'Nedunkeni', '43000'),
(13, 'Cheddikulam', '43000'),
(13, 'Vavuniya South', '43000'),

-- 14. Mullaitivu
(14, 'Mullaitivu', '42000'),
(14, 'Puthukkudiyiruppu', '42000'),
(14, 'Oddusuddan', '42000'),
(14, 'Mankulam', '42000'),
(14, 'Maritimepattu', '42000'),

-- 15. Batticaloa
(15, 'Batticaloa', '30000'),
(15, 'Kattankudy', '30130'),
(15, 'Eravur', '30300'),
(15, 'Valaichchenai', '30400'),
(15, 'Kaluwanchikudy', '30200'),
(15, 'Chenkalady', '30350'),
(15, 'Oddamavadi', '30420'),

-- 16. Ampara
(16, 'Ampara', '32000'),
(16, 'Kalmunai', '32300'),
(16, 'Sammanthurai', '32200'),
(16, 'Akkaraipattu', '32400'),
(16, 'Pottuvil', '32500'),
(16, 'Dehiattakandiya', '32150'),
(16, 'Uhana', '32060'),
(16, 'Mahaoya', '32040'),
(16, 'Damana', '32014'),

-- 17. Trincomalee
(17, 'Trincomalee', '31000'),
(17, 'Kinniya', '31100'),
(17, 'Muttur', '31200'),
(17, 'Kantale', '31300'),
(17, 'Kuchchaveli', '31000'),
(17, 'Seruwila', '31260'),
(17, 'Thampalakamam', '31046'),

-- 18. Kurunegala
(18, 'Kurunegala', '60000'),
(18, 'Kuliyapitiya', '60200'),
(18, 'Pannala', '60160'),
(18, 'Narammala', '60100'),
(18, 'Wariyapola', '60400'),
(18, 'Polgahawela', '60300'),
(18, 'Mawathagama', '60060'),
(18, 'Alawwa', '60280'),
(18, 'Ibbagamuwa', '60500'),
(18, 'Galgamuwa', '60700'),
(18, 'Nikaweratiya', '60470'),
(18, 'Mahawa', '60600'),
(18, 'Bingiriya', '60180'),

-- 19. Puttalam
(19, 'Puttalam', '61300'),
(19, 'Chilaw', '61000'),
(19, 'Marawila', '61140'),
(19, 'Wennappuwa', '61170'),
(19, 'Anamaduwa', '61500'),
(19, 'Dankotuwa', '61180'),
(19, 'Kalpitiya', '61360'),
(19, 'Nattandiya', '61190'),
(19, 'Madampe', '61230'),

-- 20. Anuradhapura
(20, 'Anuradhapura', '50000'),
(20, 'Kekirawa', '50100'),
(20, 'Medawachchiya', '50500'),
(20, 'Tambuttegama', '50140'),
(20, 'Eppawala', '50180'),
(20, 'Nochchiyagama', '50200'),
(20, 'Galnewa', '50174'),
(20, 'Horowpathana', '50350'),
(20, 'Kahatagasdigiliya', '50320'),
(20, 'Talawa', '50230'),
(20, 'Padaviya', '50570'),

-- 21. Polonnaruwa
(21, 'Polonnaruwa', '51000'),
(21, 'Kaduruwela', '51000'),
(21, 'Hingurakgoda', '51400'),
(21, 'Medirigiriya', '51500'),
(21, 'Dimbulagala', '51031'),
(21, 'Welikanda', '51070'),
(21, 'Lankapura', '51000'),
(21, 'Thamankaduwa', '51000'),

-- 22. Badulla
(22, 'Badulla', '90000'),
(22, 'Bandarawela', '90100'),
(22, 'Welimada', '90200'),
(22, 'Haputale', '90160'),
(22, 'Mahiyanganaya', '90700'),
(22, 'Ella', '90090'),
(22, 'Diyatalawa', '90422'),
(22, 'Passara', '90500'),
(22, 'Hali-Ela', '90060'),
(22, 'Uva Paranagama', '90230'),

-- 23. Monaragala
(23, 'Monaragala', '91200'),
(23, 'Wellawaya', '91230'),
(23, 'Bibile', '91500'),
(23, 'Buttala', '91100'),
(23, 'Kataragama', '91400'),
(23, 'Siyambalanduwa', '91430'),
(23, 'Medagama', '91550'),
(23, 'Thanamalwila', '91300'),

-- 24. Ratnapura
(24, 'Ratnapura', '70000'),
(24, 'Balangoda', '70100'),
(24, 'Embilipitiya', '70200'),
(24, 'Pelmadulla', '70070'),
(24, 'Kuruwita', '70500'),
(24, 'Eheliyagoda', '70600'),
(24, 'Kahawatta', '70150'),
(24, 'Godakawela', '70160'),
(24, 'Nivitigala', '70400'),
(24, 'Kalawana', '70450'),

-- 25. Kegalle
(25, 'Kegalle', '71000'),
(25, 'Mawanella', '71500'),
(25, 'Warakapola', '71600'),
(25, 'Rambukkana', '71100'),
(25, 'Ruwanwella', '71700'),
(25, 'Dehiowita', '71400'),
(25, 'Yatiyantota', '71720'),
(25, 'Deraniyagala', '71430'),
(25, 'Galigamuwa', '71350')
ON CONFLICT (district_id, name) DO NOTHING;
-- AgroMarket Phase 1: Triggers, Automated Recomputations & Cron Timers

-- 1. Trigger to recompute farmer stats on order terminal / delivered states
CREATE OR REPLACE FUNCTION trigger_order_stats_recompute()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF (TG_OP = 'UPDATE') THEN
        IF (OLD.status <> NEW.status OR OLD.is_on_time IS DISTINCT FROM NEW.is_on_time) THEN
            PERFORM recompute_farmer_stats(NEW.farmer_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_order_stats ON orders;
CREATE TRIGGER trg_order_stats
AFTER UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION trigger_order_stats_recompute();

-- 2. Trigger on review inserted to recompute farmer stats
CREATE OR REPLACE FUNCTION trigger_review_stats_recompute()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    PERFORM recompute_farmer_stats(NEW.farmer_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_review_stats ON reviews;
CREATE TRIGGER trg_review_stats
AFTER INSERT ON reviews
FOR EACH ROW
EXECUTE FUNCTION trigger_review_stats_recompute();

-- 3. Push Dispatch trigger on notifications insert
CREATE OR REPLACE FUNCTION trigger_notify_push_dispatch()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_url TEXT;
    v_anon_key TEXT;
    v_payload JSONB;
BEGIN
    -- Only dispatch if pg_net extension is loaded
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net') THEN
        v_url := 'http://host.docker.internal:54321/functions/v1/push-dispatch';
        v_payload := jsonb_build_object(
            'notification_id', NEW.id,
            'user_id', NEW.user_id,
            'title', NEW.title,
            'body', NEW.body,
            'type', NEW.type,
            'order_id', NEW.order_id
        );

        -- Non-blocking HTTP POST via pg_net
        PERFORM net.http_post(
            url := v_url,
            headers := jsonb_build_object('Content-Type', 'application/json'),
            body := v_payload
        );
    END IF;
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Never fail the transaction if push fails
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_push_dispatch ON notifications;
CREATE TRIGGER trg_push_dispatch
AFTER INSERT ON notifications
FOR EACH ROW
EXECUTE FUNCTION trigger_notify_push_dispatch();

-- 4. Setup pg_cron for minute-interval order timers if pg_cron exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.unschedule('run-order-timers-every-minute');
        PERFORM cron.schedule(
            'run-order-timers-every-minute',
            '* * * * *',
            'SELECT run_order_timers();'
        );
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- pg_cron will be scheduled when running in cloud Supabase
    NULL;
END;
$$;
