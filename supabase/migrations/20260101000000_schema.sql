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
    photos TEXT[] NOT NULL DEFAULT '{}' CHECK (array_length(photos, 1) IS NULL OR array_length(photos, 1) <= 3),
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
