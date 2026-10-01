-- Migration: 20260101000009_chat_security_hotfix.sql
-- Description: Security Hotfix for Chat System:
--   1. Drop legacy send_chat_message(UUID, TEXT, UUID) and drop p_sender_id/p_buyer_id parameters
--      from create_or_get_inquiry_chat. Identity is derived ONLY from auth.uid(), raising if null.
--   2. Revoke EXECUTE on all chat RPCs from anon; grant only to authenticated and service_role.
--   3. Fix get_order_messages: raise NOT_A_PARTY when auth.uid() is null or caller is neither
--      buyer, farmer nor admin.
--   4. Remove every hardcoded demo UUID fallback.

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. DROP legacy RPCs and obsolete parameter signatures
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.send_chat_message(UUID, TEXT, UUID);
DROP FUNCTION IF EXISTS public.send_chat_message(UUID, TEXT);
DROP FUNCTION IF EXISTS public.create_or_get_inquiry_chat(UUID, UUID);

-- Ensure orders status constraint allows 'inquiry' for pre-order chat inquiries
ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE public.orders ADD CONSTRAINT orders_status_check CHECK (
    status = ANY (ARRAY[
        'inquiry'::text, 'requested'::text, 'accepted'::text, 'rejected'::text,
        'paid'::text, 'ready'::text, 'dispatched'::text, 'delivered'::text,
        'completed'::text, 'cancelled'::text, 'expired'::text, 'refunded'::text, 'disputed'::text
    ])
);

-- ----------------------------------------------------------------------------
-- 2. Re-create create_or_get_inquiry_chat deriving identity ONLY from auth.uid()
--    No p_buyer_id or p_sender_id parameter allowed. No hardcoded demo UUID fallback.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_or_get_inquiry_chat(
    p_listing_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_buyer_id UUID := auth.uid();
    v_listing RECORD;
    v_order_id UUID;
    v_existing_id UUID;
BEGIN
    -- 1. Identity must strictly come from auth.uid(); no fallbacks to demo UUIDs
    IF v_buyer_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED: Authentication required to start chat inquiry' USING ERRCODE = '42501';
    END IF;

    -- Ensure buyer profile exists
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_buyer_id) THEN
        INSERT INTO public.profiles (id, full_name, district_id, city_id)
        VALUES (v_buyer_id, 'Buyer ' || SUBSTRING(v_buyer_id::text, 1, 6), 1, 1)
        ON CONFLICT (id) DO NOTHING;
    END IF;

    -- 2. Validate crop listing
    SELECT * INTO v_listing FROM public.listings WHERE id = p_listing_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Crop listing not found' USING ERRCODE = 'P0001';
    END IF;

    -- Buyer cannot inquire on their own listing
    IF v_listing.farmer_id = v_buyer_id THEN
        RAISE EXCEPTION 'CANNOT_INQUIRE_OWN_LISTING: You cannot create an inquiry on your own crop listing' USING ERRCODE = 'P0001';
    END IF;

    -- 3. Check for existing active inquiry or order for this listing
    SELECT id INTO v_existing_id
    FROM public.orders
    WHERE buyer_id = v_buyer_id
      AND listing_id = p_listing_id
      AND status NOT IN ('rejected', 'cancelled', 'expired', 'completed', 'refunded')
    ORDER BY requested_at DESC
    LIMIT 1;

    IF v_existing_id IS NOT NULL THEN
        RETURN v_existing_id;
    END IF;

    -- 4. Create new inquiry order record for chat discussion
    v_order_id := gen_random_uuid();
    INSERT INTO public.orders (
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
        commission_rate,
        commission_amount,
        total_amount,
        farmer_payout_amount,
        requested_date,
        status,
        stock_reserved,
        requested_at,
        created_at,
        updated_at
    ) VALUES (
        v_order_id,
        'INQ-' || LPAD(FLOOR(RANDOM() * 900000 + 100000)::TEXT, 6, '0'),
        v_buyer_id,
        v_listing.farmer_id,
        p_listing_id,
        v_listing.crop_name,
        v_listing.price_per_kg,
        v_listing.min_order_kg,
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        'buyer_arranged',
        0.03,
        0.00,
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        ROUND(v_listing.min_order_kg * v_listing.price_per_kg, 2),
        CURRENT_DATE,
        'inquiry',
        false,
        NOW(),
        NOW(),
        NOW()
    );

    -- Opening system message
    INSERT INTO public.messages (
        order_id,
        sender_id,
        kind,
        body,
        client_nonce,
        created_at
    ) VALUES (
        v_order_id,
        NULL,
        'system',
        'Discussion opened for ' || v_listing.crop_name || '. Ask about harvest readiness, quantity discounts, or pickup details.',
        gen_random_uuid(),
        NOW()
    );

    RETURN v_order_id;
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. Fix get_order_messages: raise NOT_A_PARTY when auth.uid() is null or
--    caller is neither buyer, farmer, nor admin.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_order_messages(
    p_order_id UUID,
    p_before_created_at TIMESTAMPTZ DEFAULT NULL,
    p_before_id UUID DEFAULT NULL,
    p_limit INT DEFAULT 30
)
RETURNS TABLE (
    id UUID,
    order_id UUID,
    sender_id UUID,
    kind TEXT,
    body TEXT,
    client_nonce UUID,
    created_at TIMESTAMPTZ,
    is_mine BOOLEAN,
    sender_role TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_order RECORD;
BEGIN
    -- Raise NOT_A_PARTY if caller is not authenticated
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'NOT_A_PARTY' USING ERRCODE = 'P0001';
    END IF;

    SELECT buyer_id, farmer_id INTO v_order
    FROM public.orders
    WHERE orders.id = p_order_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ORDER_NOT_FOUND' USING ERRCODE = 'P0001';
    END IF;

    -- Raise NOT_A_PARTY if caller is neither buyer, farmer, nor admin
    IF v_order.buyer_id != v_uid AND v_order.farmer_id != v_uid THEN
        IF NOT EXISTS (SELECT 1 FROM public.admin_users WHERE user_id = v_uid) THEN
            RAISE EXCEPTION 'NOT_A_PARTY' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    RETURN QUERY
    SELECT
        m.id,
        m.order_id,
        m.sender_id,
        m.kind,
        m.body,
        m.client_nonce,
        m.created_at,
        (m.sender_id = v_uid) AS is_mine,
        CASE
            WHEN m.kind = 'system' OR m.sender_id IS NULL THEN 'system'
            WHEN m.sender_id = v_order.farmer_id THEN 'farmer'
            ELSE 'buyer'
        END AS sender_role
    FROM public.messages m
    WHERE m.order_id = p_order_id
      AND (
          p_before_created_at IS NULL
          OR (m.created_at, m.id) < (p_before_created_at, p_before_id)
      )
    ORDER BY m.created_at DESC, m.id DESC
    LIMIT LEAST(p_limit, 100);
END;
$$;

-- ----------------------------------------------------------------------------
-- 4. Revoke EXECUTE on all chat RPCs from anon; grant only to authenticated
-- ----------------------------------------------------------------------------
-- create_or_get_inquiry_chat
REVOKE ALL ON FUNCTION public.create_or_get_inquiry_chat(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_or_get_inquiry_chat(UUID) TO authenticated, service_role;

-- get_order_messages
REVOKE ALL ON FUNCTION public.get_order_messages(UUID, TIMESTAMPTZ, UUID, INT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_order_messages(UUID, TIMESTAMPTZ, UUID, INT) TO authenticated, service_role;

-- mark_chat_read
REVOKE ALL ON FUNCTION public.mark_chat_read(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_chat_read(UUID) TO authenticated, service_role;

-- get_chat_overview
REVOKE ALL ON FUNCTION public.get_chat_overview() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_chat_overview() TO authenticated, service_role;

-- chat_send (strictly service_role gateway)
REVOKE ALL ON FUNCTION public.chat_send(UUID, UUID, TEXT, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.chat_send(UUID, UUID, TEXT, UUID) TO service_role, postgres;

-- post_system_message
REVOKE ALL ON FUNCTION public.post_system_message(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.post_system_message(UUID, TEXT) TO authenticated, service_role;

-- admin_get_dispute_chat
REVOKE ALL ON FUNCTION public.admin_get_dispute_chat(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_get_dispute_chat(UUID) TO authenticated, service_role;

-- ----------------------------------------------------------------------------
-- 5. Tighten RLS and permissions on messages and chat_reads tables
-- ----------------------------------------------------------------------------
-- Revoke direct permissions from anon
REVOKE ALL ON public.messages FROM PUBLIC, anon;
GRANT SELECT ON public.messages TO authenticated, service_role;

REVOKE ALL ON public.chat_reads FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE ON public.chat_reads TO authenticated, service_role;

-- Re-create messages select policy strictly for authenticated parties and admins
DROP POLICY IF EXISTS "messages_select_parties_and_admin" ON public.messages;
CREATE POLICY "messages_select_parties_and_admin" ON public.messages
FOR SELECT TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.orders o
        WHERE o.id = messages.order_id
          AND (o.buyer_id = auth.uid() OR o.farmer_id = auth.uid())
    )
    OR EXISTS (
        SELECT 1 FROM public.admin_users a
        WHERE a.user_id = auth.uid()
    )
);

COMMIT;
