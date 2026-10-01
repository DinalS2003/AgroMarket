-- Migration: 20260101000008_rebuild_chat.sql
-- Description: Rebuild order chat with strict RLS, client_nonce idempotency, keyset pagination,
--              service_role chat_send gateway, read receipts, and dispute audit logging.

BEGIN;

-- 1. Ensure messages columns: kind, client_nonce
ALTER TABLE public.messages
    ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'user' CHECK (kind IN ('user', 'system')),
    ADD COLUMN IF NOT EXISTS client_nonce UUID;

-- Backfill existing messages
UPDATE public.messages SET kind = 'user' WHERE kind IS NULL;

-- Unique constraint for idempotent retries
DROP INDEX IF EXISTS public.idx_messages_order_sender_nonce;
CREATE UNIQUE INDEX idx_messages_order_sender_nonce
    ON public.messages(order_id, sender_id, client_nonce)
    WHERE client_nonce IS NOT NULL;

-- Keyset pagination index
DROP INDEX IF EXISTS public.idx_messages_order_created_id_desc;
CREATE INDEX idx_messages_order_created_id_desc
    ON public.messages(order_id, created_at DESC, id DESC);

-- 2. Chat Read Receipts table
CREATE TABLE IF NOT EXISTS public.chat_reads (
    order_id UUID NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (order_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_reads_user ON public.chat_reads(user_id);

-- 3. mark_chat_read RPC
CREATE OR REPLACE FUNCTION public.mark_chat_read(p_order_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO public.chat_reads (order_id, user_id, last_read_at)
    VALUES (p_order_id, v_uid, NOW())
    ON CONFLICT (order_id, user_id)
    DO UPDATE SET last_read_at = NOW();
END;
$$;

GRANT EXECUTE ON FUNCTION public.mark_chat_read(UUID) TO authenticated, anon;

-- 4. RLS on messages: select for the order's two parties only; no client insert/update/delete
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "messages_select_parties" ON public.messages;
DROP POLICY IF EXISTS "messages_insert_parties" ON public.messages;
DROP POLICY IF EXISTS "messages_update_none" ON public.messages;
DROP POLICY IF EXISTS "messages_delete_none" ON public.messages;
DROP POLICY IF EXISTS "Users can read messages for their orders" ON public.messages;
DROP POLICY IF EXISTS "Participants can view order messages" ON public.messages;
DROP POLICY IF EXISTS "Admins can view all messages" ON public.messages;
DROP POLICY IF EXISTS "Anyone can insert messages" ON public.messages;
DROP POLICY IF EXISTS "Authenticated users can insert messages" ON public.messages;
DROP POLICY IF EXISTS "messages_select_parties_and_admin" ON public.messages;

CREATE POLICY "messages_select_parties_and_admin" ON public.messages
FOR SELECT TO authenticated, anon
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

-- Strictly revoke direct client mutations
REVOKE INSERT, UPDATE, DELETE ON public.messages FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.messages TO authenticated, anon;
GRANT ALL ON public.messages TO service_role, postgres;

-- Ensure messages table is in realtime publication
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
END $$;

-- 5. Admin Alerts table for repeated abuse
CREATE TABLE IF NOT EXISTS public.admin_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    order_id UUID REFERENCES public.orders(id) ON DELETE SET NULL,
    details JSONB DEFAULT '{}'::jsonb,
    is_resolved BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_alerts_user ON public.admin_alerts(user_id, type);

-- 6. chat_send: SECURITY DEFINER, service_role only
CREATE OR REPLACE FUNCTION public.chat_send(
    p_order_id UUID,
    p_sender_id UUID,
    p_body TEXT,
    p_client_nonce UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_order RECORD;
    v_is_suspended BOOLEAN;
    v_recent_count INT;
    v_existing RECORD;
    v_new_message RECORD;
    v_recipient_id UUID;
    v_clean_body TEXT;
BEGIN
    IF p_order_id IS NULL OR p_sender_id IS NULL THEN
        RAISE EXCEPTION 'ORDER_ID_AND_SENDER_REQUIRED' USING ERRCODE = 'P0001';
    END IF;

    v_clean_body := TRIM(p_body);
    IF LENGTH(v_clean_body) < 1 OR LENGTH(v_clean_body) > 1000 THEN
        RAISE EXCEPTION 'INVALID_BODY_LENGTH' USING ERRCODE = 'P0001';
    END IF;

    -- Lock the order row
    SELECT id, order_number, buyer_id, farmer_id, status
    INTO v_order
    FROM public.orders
    WHERE id = p_order_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ORDER_NOT_FOUND' USING ERRCODE = 'P0001';
    END IF;

    -- Check sender is a party
    IF v_order.buyer_id != p_sender_id AND v_order.farmer_id != p_sender_id THEN
        RAISE EXCEPTION 'NOT_A_PARTY' USING ERRCODE = 'P0001';
    END IF;

    -- Check order not terminal
    IF v_order.status IN ('rejected', 'cancelled', 'expired', 'completed', 'refunded') THEN
        RAISE EXCEPTION 'ORDER_CLOSED' USING ERRCODE = 'P0002';
    END IF;

    -- Check sender not suspended
    SELECT is_suspended INTO v_is_suspended
    FROM public.profiles
    WHERE id = p_sender_id;

    IF v_is_suspended IS TRUE THEN
        RAISE EXCEPTION 'SUSPENDED' USING ERRCODE = 'P0003';
    END IF;

    -- Max 20 messages per minute
    SELECT COUNT(*) INTO v_recent_count
    FROM public.messages
    WHERE sender_id = p_sender_id
      AND created_at >= NOW() - INTERVAL '1 minute';

    IF v_recent_count >= 20 THEN
        RAISE EXCEPTION 'RATE_LIMITED' USING ERRCODE = 'P0004';
    END IF;

    -- Idempotent retry: return existing row if nonce repeats
    IF p_client_nonce IS NOT NULL THEN
        SELECT id, order_id, sender_id, kind, body, client_nonce, created_at
        INTO v_existing
        FROM public.messages
        WHERE order_id = p_order_id
          AND sender_id = p_sender_id
          AND client_nonce = p_client_nonce
        LIMIT 1;

        IF FOUND THEN
            RETURN jsonb_build_object(
                'id', v_existing.id,
                'order_id', v_existing.order_id,
                'sender_id', v_existing.sender_id,
                'kind', v_existing.kind,
                'body', v_existing.body,
                'client_nonce', v_existing.client_nonce,
                'created_at', v_existing.created_at,
                'is_duplicate', true
            );
        END IF;
    END IF;

    -- Insert message
    INSERT INTO public.messages (
        order_id,
        sender_id,
        kind,
        body,
        client_nonce,
        created_at
    ) VALUES (
        p_order_id,
        p_sender_id,
        'user',
        v_clean_body,
        p_client_nonce,
        NOW()
    )
    RETURNING id, order_id, sender_id, kind, body, client_nonce, created_at
    INTO v_new_message;

    -- Update sender's read receipt
    INSERT INTO public.chat_reads (order_id, user_id, last_read_at)
    VALUES (p_order_id, p_sender_id, NOW())
    ON CONFLICT (order_id, user_id)
    DO UPDATE SET last_read_at = NOW();

    -- Generic notification to counterpart (never the message text!)
    v_recipient_id := CASE WHEN p_sender_id = v_order.buyer_id THEN v_order.farmer_id ELSE v_order.buyer_id END;

    INSERT INTO public.notifications (
        user_id,
        type,
        title,
        body,
        order_id
    ) VALUES (
        v_recipient_id,
        'new_chat_message',
        'Order Message',
        'New message on order ' || v_order.order_number,
        p_order_id
    );

    RETURN jsonb_build_object(
        'id', v_new_message.id,
        'order_id', v_new_message.order_id,
        'sender_id', v_new_message.sender_id,
        'kind', v_new_message.kind,
        'body', v_new_message.body,
        'client_nonce', v_new_message.client_nonce,
        'created_at', v_new_message.created_at,
        'is_duplicate', false
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.chat_send(UUID, UUID, TEXT, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.chat_send(UUID, UUID, TEXT, UUID) TO service_role, postgres;

-- 7. record_blocked_message: writes flagged_messages; 3+ in 24h creates one admin_alerts 'chat_abuse' row
CREATE OR REPLACE FUNCTION public.record_blocked_message(
    p_order_id UUID,
    p_sender_id UUID,
    p_original_body TEXT,
    p_reasons TEXT[]
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_24h_count INT;
    v_order_num TEXT;
BEGIN
    INSERT INTO public.flagged_messages (
        order_id,
        sender_id,
        original_body,
        reasons,
        reviewed,
        created_at
    ) VALUES (
        p_order_id,
        p_sender_id,
        p_original_body,
        p_reasons,
        false,
        NOW()
    );

    SELECT COUNT(*) INTO v_24h_count
    FROM public.flagged_messages
    WHERE sender_id = p_sender_id
      AND created_at >= NOW() - INTERVAL '24 hours';

    IF v_24h_count >= 3 THEN
        SELECT order_number INTO v_order_num FROM public.orders WHERE id = p_order_id;

        IF NOT EXISTS (
            SELECT 1 FROM public.admin_alerts
            WHERE user_id = p_sender_id
              AND type = 'chat_abuse'
              AND created_at >= NOW() - INTERVAL '24 hours'
        ) THEN
            INSERT INTO public.admin_alerts (
                type,
                user_id,
                order_id,
                details,
                is_resolved,
                created_at
            ) VALUES (
                'chat_abuse',
                p_sender_id,
                p_order_id,
                jsonb_build_object(
                    'violation_count_24h', v_24h_count,
                    'order_number', v_order_num,
                    'latest_reasons', p_reasons
                ),
                false,
                NOW()
            );
        END IF;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.record_blocked_message(UUID, UUID, TEXT, TEXT[]) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_blocked_message(UUID, UUID, TEXT, TEXT[]) TO service_role, postgres;

-- 8. post_system_message: called from order-status functions
CREATE OR REPLACE FUNCTION public.post_system_message(
    p_order_id UUID,
    p_body TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_msg_id UUID;
BEGIN
    INSERT INTO public.messages (
        order_id,
        sender_id,
        kind,
        body,
        client_nonce,
        created_at
    ) VALUES (
        p_order_id,
        NULL,
        'system',
        TRIM(p_body),
        gen_random_uuid(),
        NOW()
    )
    RETURNING id INTO v_msg_id;

    RETURN v_msg_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.post_system_message(UUID, TEXT) TO authenticated, service_role, postgres;

-- 9. get_order_messages: keyset pagination, newest first, returns is_mine and sender_role, no profile data
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
    SELECT buyer_id, farmer_id INTO v_order
    FROM public.orders
    WHERE orders.id = p_order_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF v_uid IS NOT NULL AND v_order.buyer_id != v_uid AND v_order.farmer_id != v_uid THEN
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
        (v_uid IS NOT NULL AND m.sender_id = v_uid) AS is_mine,
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

GRANT EXECUTE ON FUNCTION public.get_order_messages(UUID, TIMESTAMPTZ, UUID, INT) TO authenticated, anon;

-- 10. get_chat_overview: unread counts
CREATE OR REPLACE FUNCTION public.get_chat_overview()
RETURNS TABLE (
    order_id UUID,
    order_number TEXT,
    crop_name TEXT,
    order_status TEXT,
    last_message_body TEXT,
    last_message_at TIMESTAMPTZ,
    unread_count BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY
    WITH user_orders AS (
        SELECT o.id, o.order_number, o.crop_name, o.status
        FROM public.orders o
        WHERE o.buyer_id = v_uid OR o.farmer_id = v_uid
    ),
    latest_msgs AS (
        SELECT DISTINCT ON (m.order_id)
            m.order_id,
            m.body,
            m.created_at
        FROM public.messages m
        JOIN user_orders uo ON uo.id = m.order_id
        ORDER BY m.order_id, m.created_at DESC, m.id DESC
    ),
    unread_counts AS (
        SELECT
            uo.id AS order_id,
            COUNT(m.id) AS unreads
        FROM user_orders uo
        LEFT JOIN public.chat_reads cr ON cr.order_id = uo.id AND cr.user_id = v_uid
        LEFT JOIN public.messages m ON m.order_id = uo.id
            AND m.created_at > COALESCE(cr.last_read_at, '1970-01-01'::timestamptz)
            AND (m.sender_id IS NULL OR m.sender_id != v_uid)
        GROUP BY uo.id
    )
    SELECT
        uo.id AS order_id,
        uo.order_number,
        uo.crop_name,
        uo.status AS order_status,
        lm.body AS last_message_body,
        lm.created_at AS last_message_at,
        COALESCE(uc.unreads, 0) AS unread_count
    FROM user_orders uo
    LEFT JOIN latest_msgs lm ON lm.order_id = uo.id
    LEFT JOIN unread_counts uc ON uc.order_id = uo.id
    ORDER BY lm.created_at DESC NULLS LAST;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_chat_overview() TO authenticated, anon;

-- 11. admin_get_dispute_chat: admins only, only if dispute exists, writes audit_log
CREATE OR REPLACE FUNCTION public.admin_get_dispute_chat(p_order_id UUID)
RETURNS TABLE (
    id UUID,
    order_id UUID,
    sender_id UUID,
    kind TEXT,
    body TEXT,
    created_at TIMESTAMPTZ,
    sender_role TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_admin_id UUID := auth.uid();
    v_order RECORD;
    v_dispute_id UUID;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.admin_users WHERE user_id = v_admin_id) THEN
        RAISE EXCEPTION 'ADMIN_ACCESS_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT id, buyer_id, farmer_id, order_number INTO v_order
    FROM public.orders
    WHERE id = p_order_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ORDER_NOT_FOUND' USING ERRCODE = 'P0001';
    END IF;

    SELECT id INTO v_dispute_id
    FROM public.disputes
    WHERE order_id = p_order_id;

    IF v_dispute_id IS NULL THEN
        RAISE EXCEPTION 'NO_DISPUTE_EXISTS_FOR_ORDER' USING ERRCODE = 'P0001';
    END IF;

    -- Write to audit_log
    INSERT INTO public.audit_log (
        admin_id,
        action,
        entity,
        entity_id,
        details,
        created_at
    ) VALUES (
        v_admin_id,
        'view_dispute_chat',
        'order',
        p_order_id::text,
        jsonb_build_object(
            'dispute_id', v_dispute_id,
            'order_number', v_order.order_number
        ),
        NOW()
    );

    RETURN QUERY
    SELECT
        m.id,
        m.order_id,
        m.sender_id,
        m.kind,
        m.body,
        m.created_at,
        CASE
            WHEN m.kind = 'system' OR m.sender_id IS NULL THEN 'system'
            WHEN m.sender_id = v_order.farmer_id THEN 'farmer'
            ELSE 'buyer'
        END AS sender_role
    FROM public.messages m
    WHERE m.order_id = p_order_id
    ORDER BY m.created_at ASC, m.id ASC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.admin_get_dispute_chat(UUID) TO authenticated, service_role, postgres;

COMMIT;
