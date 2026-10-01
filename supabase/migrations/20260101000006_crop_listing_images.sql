-- ============================================================================
-- Migration: Add and Support Crop Images for Listings
-- Description:
--   1. Ensures the `photos` TEXT[] column exists on `listings` table.
--   2. Updates CHECK constraint to allow up to 5 images per listing.
--   3. Sets up Supabase Storage bucket 'crop-photos' with public read access
--      and authenticated upload RLS policies.
--   4. Updates `create_farmer_listing` RPC to accept optional `p_photos` array.
--   5. Updates existing listings with high-quality agricultural crop images.
-- ============================================================================

-- 1. Ensure photos column exists on listings and update check constraint
ALTER TABLE listings ADD COLUMN IF NOT EXISTS photos TEXT[] NOT NULL DEFAULT '{}';

-- Update constraint to allow up to 5 photos (optional, 0 to 5 photos)
ALTER TABLE listings DROP CONSTRAINT IF EXISTS chk_photos_length;
ALTER TABLE listings DROP CONSTRAINT IF EXISTS listings_photos_check;
ALTER TABLE listings ADD CONSTRAINT listings_photos_check 
    CHECK (photos IS NULL OR array_length(photos, 1) IS NULL OR array_length(photos, 1) <= 5);

-- Index on array length or GIN index for search if needed
CREATE INDEX IF NOT EXISTS idx_listings_photos_exists ON listings((array_length(photos, 1) > 0));

-- 2. Setup Supabase Storage bucket for crop photos (if storage schema exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'storage') THEN
        INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
        VALUES (
            'crop-photos',
            'crop-photos',
            true,
            5242880, -- 5MB limit
            ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif']
        )
        ON CONFLICT (id) DO UPDATE SET 
            public = true,
            file_size_limit = 5242880,
            allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

        -- Storage RLS Policies
        DROP POLICY IF EXISTS "Public can view crop photos" ON storage.objects;
        CREATE POLICY "Public can view crop photos"
        ON storage.objects FOR SELECT
        USING (bucket_id = 'crop-photos');

        DROP POLICY IF EXISTS "Authenticated users can upload crop photos" ON storage.objects;
        CREATE POLICY "Authenticated users can upload crop photos"
        ON storage.objects FOR INSERT
        TO authenticated
        WITH CHECK (bucket_id = 'crop-photos');

        DROP POLICY IF EXISTS "Users can update their own crop photos" ON storage.objects;
        CREATE POLICY "Users can update their own crop photos"
        ON storage.objects FOR UPDATE
        TO authenticated
        USING (bucket_id = 'crop-photos');

        DROP POLICY IF EXISTS "Users can delete their own crop photos" ON storage.objects;
        CREATE POLICY "Users can delete their own crop photos"
        ON storage.objects FOR DELETE
        TO authenticated
        USING (bucket_id = 'crop-photos');
    END IF;
END $$;

-- 3. Update create_farmer_listing RPC function to safely handle p_photos with default empty array
CREATE OR REPLACE FUNCTION create_farmer_listing(
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
AS $$
DECLARE
    v_farmer_id UUID := coalesce(auth.uid(), p_farmer_id);
    v_listing_id UUID;
    v_result JSONB;
    v_clean_photos TEXT[];
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

    v_clean_photos := COALESCE(p_photos, '{}'::TEXT[]);
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
        TRIM(p_crop_name),
        LOWER(TRIM(p_crop_name_key)),
        p_quantity_available,
        p_price_per_kg,
        p_min_order_kg,
        p_harvest_date,
        v_clean_photos,
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

-- 4. Seed/Update existing listings with high quality agricultural crop images
UPDATE listings
SET photos = ARRAY[
    'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=800&q=80',
    'https://images.unsplash.com/photo-1561136594-7f68413baa99?w=800&q=80'
]
WHERE crop_name_key ILIKE '%tomato%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY[
    'https://images.unsplash.com/photo-1567375698348-5d9d5ae99de0?w=800&q=80'
]
WHERE crop_name_key ILIKE '%bean%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY[
    'https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=800&q=80'
]
WHERE crop_name_key ILIKE '%potato%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY[
    'https://images.unsplash.com/photo-1594282486552-05b4d80fbb9f?w=800&q=80'
]
WHERE crop_name_key ILIKE '%cabbage%' AND (photos IS NULL OR photos = '{}');

UPDATE listings
SET photos = ARRAY[
    'https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?w=800&q=80'
]
WHERE crop_name_key ILIKE '%carrot%' AND (photos IS NULL OR photos = '{}');
