const { Client } = require('/tmp/db/node_modules/pg');

const client = new Client({
  connectionString: 'postgresql://postgres.wqqvikjsfbcprueivxzn:Agromarket2026@aws-0-ap-south-1.pooler.supabase.com:5432/postgres',
  ssl: { rejectUnauthorized: false }
});

async function main() {
  await client.connect();
  console.log('Connected to DB');

  const sql = `
CREATE OR REPLACE FUNCTION public.register_profile(
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
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_user_id UUID := coalesce(auth.uid(), p_user_id);
    v_clean_phone TEXT;
    v_email TEXT;
    v_password TEXT;
    v_normalized_nic TEXT;
BEGIN
    v_clean_phone := regexp_replace(p_phone_e164, '[^0-9+]', '', 'g');

    -- Find existing auth user by phone or email
    IF v_user_id IS NULL THEN
        SELECT id INTO v_user_id FROM auth.users WHERE phone = v_clean_phone LIMIT 1;
    END IF;

    IF v_user_id IS NULL THEN
        v_email := 'p' || regexp_replace(v_clean_phone, '[^0-9]', '', 'g') || '@agromarket.lk';
        SELECT id INTO v_user_id FROM auth.users WHERE email = v_email LIMIT 1;
    END IF;

    -- Create auth user if none exists
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

    IF NOT (v_clean_phone LIKE '+947%' AND length(v_clean_phone) = 12) THEN
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

GRANT EXECUTE ON FUNCTION public.register_profile(TEXT, TEXT, TEXT, INT, INT, UUID) TO anon, authenticated, service_role;
`;

  await client.query(sql);
  console.log('register_profile fixed and granted successfully!');
  await client.end();
}

main().catch(err => {
  console.error('Migration error:', err);
  process.exit(1);
});
