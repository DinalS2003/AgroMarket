-- AgroMarket Phase 1: Fix Auth Sync, Null Tokens, Identities, and Listing Creation
-- Resolves GoTrue 500 'Database error querying schema' caused by NULL tokens in auth.users
-- and missing records in auth.identities, plus adds create_farmer_listing RPC function.

-- 1. Fix NULLs in auth.users tokens
UPDATE auth.users
SET
  confirmation_token = COALESCE(confirmation_token, ''),
  recovery_token = COALESCE(recovery_token, ''),
  email_change = COALESCE(email_change, ''),
  email_change_token_new = COALESCE(email_change_token_new, ''),
  email_change_token_current = COALESCE(email_change_token_current, ''),
  phone_change = COALESCE(phone_change, ''),
  phone_change_token = COALESCE(phone_change_token, ''),
  reauthentication_token = COALESCE(reauthentication_token, '');

-- 2. Populate auth.identities for any users missing it
INSERT INTO auth.identities (
  id,
  user_id,
  identity_data,
  provider,
  provider_id,
  last_sign_in_at,
  created_at,
  updated_at
)
SELECT
  gen_random_uuid(),
  u.id,
  jsonb_build_object('sub', u.id::text, 'email', u.email),
  'email',
  u.id::text,
  NOW(),
  NOW(),
  NOW()
FROM auth.users u
WHERE NOT EXISTS (
  SELECT 1 FROM auth.identities i WHERE i.user_id = u.id AND i.provider = 'email'
);

-- 3. Update get_or_create_phone_auth to cleanly handle auth.users & auth.identities
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
            confirmation_token,
            recovery_token,
            email_change,
            email_change_token_new,
            email_change_token_current,
            phone_change,
            phone_change_token,
            reauthentication_token,
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
            '', '', '', '', '', '', '', '',
            '{"provider":"email","providers":["email"]}'::jsonb,
            jsonb_build_object('phone', v_clean_phone),
            NOW(),
            NOW()
        );

        INSERT INTO auth.identities (
            id,
            user_id,
            identity_data,
            provider,
            provider_id,
            last_sign_in_at,
            created_at,
            updated_at
        ) VALUES (
            gen_random_uuid(),
            v_user_id,
            jsonb_build_object('sub', v_user_id::text, 'email', v_email),
            'email',
            v_user_id::text,
            NOW(),
            NOW(),
            NOW()
        ) ON CONFLICT (provider_id, provider) DO NOTHING;
    ELSE
        UPDATE auth.users
        SET
          confirmation_token = COALESCE(confirmation_token, ''),
          recovery_token = COALESCE(recovery_token, ''),
          email_change = COALESCE(email_change, ''),
          email_change_token_new = COALESCE(email_change_token_new, ''),
          email_change_token_current = COALESCE(email_change_token_current, ''),
          phone_change = COALESCE(phone_change, ''),
          phone_change_token = COALESCE(phone_change_token, ''),
          reauthentication_token = COALESCE(reauthentication_token, '')
        WHERE id = v_user_id;

        INSERT INTO auth.identities (
            id,
            user_id,
            identity_data,
            provider,
            provider_id,
            last_sign_in_at,
            created_at,
            updated_at
        ) VALUES (
            gen_random_uuid(),
            v_user_id,
            jsonb_build_object('sub', v_user_id::text, 'email', v_email),
            'email',
            v_user_id::text,
            NOW(),
            NOW(),
            NOW()
        ) ON CONFLICT (provider_id, provider) DO NOTHING;
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

GRANT EXECUTE ON FUNCTION get_or_create_phone_auth(TEXT) TO anon, authenticated, service_role;

-- 4. Update register_profile to ensure clean auth.users & identities
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
    v_clean_phone TEXT;
    v_email TEXT;
    v_password TEXT;
    v_normalized_nic TEXT;
BEGIN
    v_clean_phone := regexp_replace(p_phone_e164, '[^0-9+]', '', 'g');

    IF v_user_id IS NULL THEN
        SELECT id INTO v_user_id FROM auth.users WHERE phone = v_clean_phone LIMIT 1;
    END IF;

    IF v_user_id IS NULL THEN
        v_email := 'p' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '@agromarket.lk';
        SELECT id INTO v_user_id FROM auth.users WHERE email = v_email LIMIT 1;
    END IF;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        v_email := 'p' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '@agromarket.lk';
        v_password := 'AgroPass_' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '!';

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
            confirmation_token,
            recovery_token,
            email_change,
            email_change_token_new,
            email_change_token_current,
            phone_change,
            phone_change_token,
            reauthentication_token,
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
            '', '', '', '', '', '', '', '',
            '{"provider":"email","providers":["email"]}'::jsonb,
            jsonb_build_object('phone', v_clean_phone),
            NOW(),
            NOW()
        );

        INSERT INTO auth.identities (
            id,
            user_id,
            identity_data,
            provider,
            provider_id,
            last_sign_in_at,
            created_at,
            updated_at
        ) VALUES (
            gen_random_uuid(),
            v_user_id,
            jsonb_build_object('sub', v_user_id::text, 'email', v_email),
            'email',
            v_user_id::text,
            NOW(),
            NOW(),
            NOW()
        ) ON CONFLICT (provider_id, provider) DO NOTHING;
    END IF;

    v_normalized_nic := UPPER(TRIM(p_nic));

    IF NOT (v_normalized_nic ~ '^[0-9]{9}[VX]$' OR v_normalized_nic ~ '^[0-9]{12}$') THEN
        RAISE EXCEPTION 'Invalid NIC format. Must be 9 digits followed by V/X or 12 digits';
    END IF;

    IF NOT (v_clean_phone ~ '^\+947[0-9]{8}$') THEN
        RAISE EXCEPTION 'Invalid Sri Lankan mobile format. Must be +947XXXXXXXX';
    END IF;

    INSERT INTO profiles (id, full_name, district_id, city_id)
    VALUES (v_user_id, p_full_name, p_district_id, p_city_id)
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        district_id = EXCLUDED.district_id,
        city_id = EXCLUDED.city_id,
        updated_at = NOW();

    INSERT INTO user_private (user_id, nic, phone_e164)
    VALUES (v_user_id, v_normalized_nic, v_clean_phone)
    ON CONFLICT (user_id) DO UPDATE SET
        nic = EXCLUDED.nic,
        phone_e164 = EXCLUDED.phone_e164;

    RETURN jsonb_build_object(
        'success', true,
        'user_id', v_user_id,
        'full_name', p_full_name
    );
END;
$$;

GRANT EXECUTE ON FUNCTION register_profile(TEXT, TEXT, TEXT, INT, INT, UUID) TO anon, authenticated, service_role;

-- 5. Update register_farmer
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
    v_is_suspended BOOLEAN;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User ID is required';
    END IF;

    SELECT is_suspended INTO v_is_suspended FROM profiles WHERE id = v_user_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Profile must be registered before becoming a farmer';
    END IF;
    IF v_is_suspended THEN
        RAISE EXCEPTION 'Account is suspended';
    END IF;

    INSERT INTO farmers (
        user_id,
        cultivation_district_id,
        cultivation_city_id,
        main_crops,
        land_size,
        land_unit,
        default_pickup_landmark
    )
    VALUES (
        v_user_id,
        p_cultivation_district_id,
        p_cultivation_city_id,
        p_main_crops,
        p_land_size,
        p_land_unit,
        p_default_pickup_landmark
    )
    ON CONFLICT (user_id) DO UPDATE SET
        cultivation_district_id = EXCLUDED.cultivation_district_id,
        cultivation_city_id = EXCLUDED.cultivation_city_id,
        main_crops = EXCLUDED.main_crops,
        land_size = EXCLUDED.land_size,
        land_unit = EXCLUDED.land_unit,
        default_pickup_landmark = EXCLUDED.default_pickup_landmark;

    INSERT INTO farmer_private (
        user_id,
        cultivation_address,
        bank_name,
        bank_branch,
        account_holder_name,
        account_number
    )
    VALUES (
        v_user_id,
        p_cultivation_address,
        COALESCE(p_bank_name, ''),
        COALESCE(p_bank_branch, ''),
        COALESCE(p_account_holder_name, ''),
        COALESCE(p_account_number, '')
    )
    ON CONFLICT (user_id) DO UPDATE SET
        cultivation_address = EXCLUDED.cultivation_address,
        bank_name = EXCLUDED.bank_name,
        bank_branch = EXCLUDED.bank_branch,
        account_holder_name = EXCLUDED.account_holder_name,
        account_number = EXCLUDED.account_number,
        updated_at = NOW();

    INSERT INTO farmer_stats (farmer_id)
    VALUES (v_user_id)
    ON CONFLICT (farmer_id) DO NOTHING;

    RETURN jsonb_build_object('success', true, 'farmer_id', v_user_id);
END;
$$;

GRANT EXECUTE ON FUNCTION register_farmer(INT, INT, TEXT, TEXT[], NUMERIC, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, UUID) TO anon, authenticated, service_role;

-- 6. RPC Function for creating listings
CREATE OR REPLACE FUNCTION create_farmer_listing(
    p_crop_name TEXT,
    p_crop_name_key TEXT,
    p_quantity_available NUMERIC,
    p_price_per_kg NUMERIC,
    p_min_order_kg NUMERIC,
    p_harvest_date DATE,
    p_photos TEXT[],
    p_farmer_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_farmer_id UUID := coalesce(auth.uid(), p_farmer_id);
    v_listing_id UUID;
    v_result JSONB;
BEGIN
    IF v_farmer_id IS NULL THEN
        RAISE EXCEPTION 'Farmer authentication required';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM farmers WHERE user_id = v_farmer_id) THEN
        INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops)
        VALUES (v_farmer_id, 1, 1, ARRAY[p_crop_name])
        ON CONFLICT (user_id) DO NOTHING;
    END IF;

    IF EXISTS (SELECT 1 FROM profiles WHERE id = v_farmer_id AND is_suspended = true) THEN
        RAISE EXCEPTION 'Farmer account is suspended';
    END IF;

    v_listing_id := gen_random_uuid();

    INSERT INTO listings (
        id,
        farmer_id,
        crop_name,
        crop_name_key,
        quantity_available,
        price_per_kg,
        min_order_kg,
        harvest_date,
        photos,
        is_active,
        created_at,
        updated_at
    ) VALUES (
        v_listing_id,
        v_farmer_id,
        p_crop_name,
        p_crop_name_key,
        p_quantity_available,
        p_price_per_kg,
        p_min_order_kg,
        p_harvest_date,
        p_photos,
        true,
        NOW(),
        NOW()
    );

    SELECT to_jsonb(l) INTO v_result
    FROM listings l
    WHERE l.id = v_listing_id;

    RETURN v_result;
END;
$$;

GRANT EXECUTE ON FUNCTION create_farmer_listing(TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC, DATE, TEXT[], UUID) TO anon, authenticated, service_role;

-- 7. Update create_order_request to support p_buyer_id fallback
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

    SELECT * INTO v_buyer_profile FROM profiles WHERE id = v_buyer_id;
    IF v_buyer_profile.is_suspended THEN
        RAISE EXCEPTION 'Your account is suspended: %', COALESCE(v_buyer_profile.suspended_reason, 'Contact support');
    END IF;

    SELECT * INTO v_listing FROM listings WHERE id = p_listing_id;
    IF NOT FOUND OR NOT v_listing.is_active OR v_listing.quantity_available <= 0 THEN
        RAISE EXCEPTION 'Listing is not available';
    END IF;

    IF v_listing.farmer_id = v_buyer_id THEN
        RAISE EXCEPTION 'You cannot order your own harvest';
    END IF;

    v_today_sl := (NOW() AT TIME ZONE 'Asia/Colombo')::DATE;

    IF p_quantity_kg < v_listing.min_order_kg THEN
        RAISE EXCEPTION 'Order quantity must be at least % kg', v_listing.min_order_kg;
    END IF;

    IF p_quantity_kg > v_listing.quantity_available THEN
        RAISE EXCEPTION 'Requested quantity exceeds available stock of % kg', v_listing.quantity_available;
    END IF;

    SELECT * INTO v_settings FROM app_settings LIMIT 1;

    IF p_delivery_method NOT IN ('farmer_delivery', 'buyer_arranged') THEN
        RAISE EXCEPTION 'Invalid delivery method: %', p_delivery_method;
    END IF;

    v_subtotal := ROUND(p_quantity_kg * v_listing.price_per_kg, 2);
    v_expires_at := NOW() + (COALESCE(v_settings.order_request_timeout_hours, 12) || ' hours')::INTERVAL;
    v_order_id := gen_random_uuid();

    UPDATE listings
    SET quantity_available = quantity_available - p_quantity_kg,
        updated_at = NOW()
    WHERE id = p_listing_id;

    INSERT INTO orders (
        id,
        order_number,
        buyer_id,
        farmer_id,
        listing_id,
        crop_name,
        price_per_kg,
        quantity_kg,
        subtotal,
        delivery_method,
        delivery_fee,
        commission_rate,
        commission_amount,
        total_amount,
        farmer_payout_amount,
        requested_date,
        delivery_district_id,
        delivery_city_id,
        status,
        stock_reserved,
        expires_at,
        created_at,
        updated_at
    ) VALUES (
        v_order_id,
        'AM-' || LPAD(FLOOR(RANDOM() * 900000 + 100000)::TEXT, 6, '0'),
        v_buyer_id,
        v_listing.farmer_id,
        p_listing_id,
        v_listing.crop_name,
        v_listing.price_per_kg,
        p_quantity_kg,
        v_subtotal,
        p_delivery_method,
        0.00,
        COALESCE(v_settings.commission_rate, 0.03),
        ROUND(v_subtotal * COALESCE(v_settings.commission_rate, 0.03), 2),
        v_subtotal,
        ROUND(v_subtotal * (1 - COALESCE(v_settings.commission_rate, 0.03)), 2),
        p_requested_date,
        p_delivery_district_id,
        p_delivery_city_id,
        'requested',
        true,
        v_expires_at,
        NOW(),
        NOW()
    );

    INSERT INTO order_private_details (
        order_id,
        delivery_address,
        pickup_landmark
    ) VALUES (
        v_order_id,
        CASE WHEN p_delivery_method = 'farmer_delivery' THEN TRIM(p_delivery_address) ELSE NULL END,
        CASE WHEN p_delivery_method = 'buyer_arranged' THEN (SELECT default_pickup_landmark FROM farmers WHERE user_id = v_listing.farmer_id) ELSE NULL END
    );

    RETURN v_order_id;
END;
$$;

GRANT EXECUTE ON FUNCTION create_order_request(UUID, NUMERIC, TEXT, DATE, INT, INT, TEXT, UUID) TO anon, authenticated, service_role;
