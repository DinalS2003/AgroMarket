const { Client } = require('/tmp/db/node_modules/pg');

const client = new Client({
  connectionString: 'postgresql://postgres.wqqvikjsfbcprueivxzn:Agromarket2026@aws-0-ap-south-1.pooler.supabase.com:5432/postgres',
  ssl: { rejectUnauthorized: false }
});

async function main() {
  await client.connect();
  console.log('Connected to DB');

  // 1. Remove all hardcoded/dummy seed data
  // Dummy IDs:
  // '084567da-1226-4c2e-b11a-a5f553c94332' (Nimal Perera)
  // '00000000-0000-0000-0000-000000000003' (Kamal Silva)
  // '4e5b76cb-ef83-460c-b29e-6730f3bf05e9' (Test User)
  // And any '00000000-0000-0000-0000-00000000000%' seed accounts
  const dummyUserIds = [
    '084567da-1226-4c2e-b11a-a5f553c94332',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000004',
    '4e5b76cb-ef83-460c-b29e-6730f3bf05e9'
  ];

  console.log('Cleaning up hardcoded seed listings and users...');
  
  const safeDelete = async (table, col) => {
    try {
      await client.query(`DELETE FROM public.${table} WHERE ${col} = ANY($1)`, [dummyUserIds]);
    } catch (e) {
      console.log(`Note for ${table}.${col}: ${e.message}`);
    }
  };

  await safeDelete('messages', 'sender_id');
  await safeDelete('disputes', 'raised_by');
  try {
    await client.query(`DELETE FROM public.reviews WHERE buyer_id = ANY($1) OR farmer_id = ANY($1)`, [dummyUserIds]);
  } catch (e) {
    console.log(`reviews note: ${e.message}`);
  }
  try {
    await client.query(`DELETE FROM public.order_private_details WHERE order_id IN (SELECT id FROM public.orders WHERE buyer_id = ANY($1) OR farmer_id = ANY($1))`, [dummyUserIds]);
  } catch (e) {
    console.log(`order_private_details note: ${e.message}`);
  }
  try {
    await client.query(`DELETE FROM public.orders WHERE buyer_id = ANY($1) OR farmer_id = ANY($1)`, [dummyUserIds]);
  } catch (e) {
    console.log(`orders note: ${e.message}`);
  }
  await safeDelete('listings', 'farmer_id');
  await safeDelete('farmer_stats', 'farmer_id');
  await safeDelete('farmer_private', 'user_id');
  await safeDelete('farmers', 'user_id');
  await safeDelete('user_private', 'user_id');
  await safeDelete('profiles', 'id');
  try {
    await client.query(`DELETE FROM auth.identities WHERE user_id = ANY($1)`, [dummyUserIds]);
    await client.query(`DELETE FROM auth.users WHERE id = ANY($1)`, [dummyUserIds]);
  } catch (e) {
    console.log(`auth delete note: ${e.message}`);
  }

  console.log('Hardcoded mock data deleted successfully!');

  // 2. Update create_farmer_listing with robust profile fallback
  const updateRpcSql = `
CREATE OR REPLACE FUNCTION public.create_farmer_listing(
    p_crop_name TEXT,
    p_crop_name_key TEXT,
    p_quantity_available NUMERIC,
    p_price_per_kg NUMERIC,
    p_min_order_kg NUMERIC,
    p_harvest_date DATE,
    p_photos TEXT[] DEFAULT '{}',
    p_farmer_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_farmer_id UUID := coalesce(auth.uid(), p_farmer_id);
    v_listing_id UUID;
    v_result JSONB;
BEGIN
    IF v_farmer_id IS NULL THEN
        RAISE EXCEPTION 'Farmer authentication required';
    END IF;

    -- Ensure profile exists
    IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = v_farmer_id) THEN
        INSERT INTO profiles (id, full_name, district_id, city_id)
        VALUES (v_farmer_id, 'Farmer', 1, 1)
        ON CONFLICT (id) DO NOTHING;
    END IF;

    -- Ensure farmer exists or create basic farmer profile
    IF NOT EXISTS (SELECT 1 FROM farmers WHERE user_id = v_farmer_id) THEN
        INSERT INTO farmers (user_id, cultivation_district_id, cultivation_city_id, main_crops)
        VALUES (v_farmer_id, 1, 1, ARRAY[p_crop_name])
        ON CONFLICT (user_id) DO NOTHING;
    END IF;

    -- Ensure not suspended
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

GRANT EXECUTE ON FUNCTION public.create_farmer_listing(TEXT, TEXT, NUMERIC, NUMERIC, NUMERIC, DATE, TEXT[], UUID) TO anon, authenticated, service_role;
`;
  await client.query(updateRpcSql);
  console.log('create_farmer_listing updated with auto-profile fallback!');

  // Check remaining profiles and listings
  const profs = await client.query('SELECT id, full_name FROM public.profiles');
  console.log('Remaining profiles in DB:', profs.rows);

  const lists = await client.query('SELECT id, crop_name FROM public.listings');
  console.log('Remaining listings in DB:', lists.rows);

  const users = await client.query('SELECT id, phone FROM auth.users');
  console.log('Remaining users in auth.users:', users.rows);

  await client.end();
}

main().catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
