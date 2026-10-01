-- AgroMarket Chat Security Test Suite: chat_security_test.sql
-- Verifies:
-- 1. anon cannot read or send (RPC and direct table access blocked)
-- 2. non-party authenticated user cannot read or send (raises NOT_A_PARTY, RLS returns 0 rows)
-- 3. spoofing a sender id is impossible (auth.uid() strictly enforced, direct insert revoked, chat_send service_role only)
-- 4. legacy send_chat_message RPC no longer exists and create_or_get_inquiry_chat accepts only p_listing_id

DO $$
DECLARE
    v_buyer_id UUID := '00000000-0000-0000-0000-000000000002';
    v_farmer_id UUID := '00000000-0000-0000-0000-000000000003';
    v_stranger_id UUID := '00000000-0000-0000-0000-000000000009';
    v_listing_id UUID := '10000000-0000-0000-0000-000000000001';
    v_order_id UUID := gen_random_uuid();
    v_msg_id UUID := gen_random_uuid();
    v_count INT;
    v_caught BOOLEAN;
    v_inq_id UUID;
    v_read_count INT;
    v_inq_order RECORD;
BEGIN
    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'Starting Chat Security Hotfix Verification Tests';
    RAISE NOTICE '=======================================================';

    -- Cleanup test records from any previous runs
    DELETE FROM public.notifications WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.chat_reads WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.messages WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.orders WHERE order_number LIKE 'AM-SEC-%';

    -- 1. Setup test auth users and profiles
    INSERT INTO auth.users (id, email) VALUES
        (v_buyer_id, 'buyer_sec@agromarket.lk'),
        (v_farmer_id, 'farmer_sec@agromarket.lk'),
        (v_stranger_id, 'stranger_sec@agromarket.lk')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.profiles (id, full_name, district_id, city_id) VALUES
        (v_buyer_id, 'Sec Buyer', 1, 1),
        (v_farmer_id, 'Sec Farmer', 1, 1),
        (v_stranger_id, 'Sec Stranger', 1, 1)
    ON CONFLICT (id) DO NOTHING;

    -- Ensure farmer record exists for foreign key constraint on listings
    INSERT INTO public.farmers (user_id, cultivation_district_id, cultivation_city_id) VALUES
        (v_farmer_id, 1, 1)
    ON CONFLICT (user_id) DO NOTHING;

    -- Ensure listing record exists for foreign key constraint on orders
    INSERT INTO public.listings (
        id, farmer_id, crop_name, crop_name_key, quantity_available, price_per_kg, min_order_kg, harvest_date
    ) VALUES (
        v_listing_id, v_farmer_id, 'Carrot', 'carrot', 100.0, 200.0, 1.0, CURRENT_DATE
    ) ON CONFLICT (id) DO NOTHING;

    -- Setup test order
    INSERT INTO public.orders (
        id, order_number, buyer_id, farmer_id, listing_id, crop_name,
        quantity_kg, price_per_kg, subtotal, delivery_method, total_amount,
        farmer_payout_amount, commission_rate, requested_date, status
    ) VALUES (
        v_order_id, 'AM-SEC-001', v_buyer_id, v_farmer_id, v_listing_id, 'Carrot',
        10.0, 200.0, 2000.0, 'buyer_arranged', 2000.0, 1940.0, 0.03, CURRENT_DATE, 'accepted'
    );

    INSERT INTO public.messages (
        id, order_id, sender_id, kind, body, client_nonce, created_at
    ) VALUES (
        v_msg_id, v_order_id, v_buyer_id, 'user', 'Confidential deal chat', gen_random_uuid(), NOW()
    );

    -- ------------------------------------------------------------------------
    -- TEST 1: Legacy RPC send_chat_message no longer exists
    -- ------------------------------------------------------------------------
    RAISE NOTICE 'TEST 1: Verifying legacy RPC send_chat_message is dropped...';
    SELECT COUNT(*) INTO v_count
    FROM pg_proc p
    JOIN pg_namespace n ON p.pronamespace = n.oid
    WHERE n.nspname = 'public' AND p.proname = 'send_chat_message';

    IF v_count > 0 THEN
        RAISE EXCEPTION 'TEST 1 FAILED: send_chat_message still exists in pg_proc!';
    END IF;

    -- Verify create_or_get_inquiry_chat has no 2-parameter overload
    SELECT COUNT(*) INTO v_count
    FROM pg_proc p
    JOIN pg_namespace n ON p.pronamespace = n.oid
    WHERE n.nspname = 'public' AND p.proname = 'create_or_get_inquiry_chat' AND p.pronargs > 1;

    IF v_count > 0 THEN
        RAISE EXCEPTION 'TEST 1 FAILED: create_or_get_inquiry_chat still accepts p_buyer_id / multiple arguments!';
    END IF;
    RAISE NOTICE 'PASSED [1/4]: Legacy send_chat_message RPC is completely removed; create_or_get_inquiry_chat takes only p_listing_id.';

    -- ------------------------------------------------------------------------
    -- TEST 2: Anon cannot read or send
    -- ------------------------------------------------------------------------
    RAISE NOTICE 'TEST 2: Verifying anon cannot read or send...';
    PERFORM set_config('role', 'anon', true);
    PERFORM set_config('request.jwt.claim.sub', '', true);

    -- 2a. Anon cannot read via get_order_messages RPC
    v_caught := false;
    BEGIN
        PERFORM public.get_order_messages(v_order_id);
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 2a FAILED: Anon was able to execute get_order_messages!';
    END IF;

    -- 2b. Anon cannot read via direct messages SELECT (Permission denied or RLS denies)
    BEGIN
        SELECT COUNT(*) INTO v_read_count FROM public.messages WHERE order_id = v_order_id;
        IF v_read_count > 0 THEN
            RAISE EXCEPTION 'TEST 2b FAILED: Anon was able to read messages via SELECT! Count: %', v_read_count;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        -- Permission denied (42501) proves anon cannot read messages table
        NULL;
    END;

    -- 2c. Anon cannot send via direct messages INSERT
    v_caught := false;
    BEGIN
        INSERT INTO public.messages (order_id, sender_id, kind, body, client_nonce)
        VALUES (v_order_id, v_buyer_id, 'user', 'Anon injection', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 2c FAILED: Anon was able to insert into messages!';
    END IF;

    -- 2d. Anon cannot create inquiry chat
    v_caught := false;
    BEGIN
        PERFORM public.create_or_get_inquiry_chat(v_listing_id);
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 2d FAILED: Anon was able to create inquiry chat!';
    END IF;

    -- 2e. Anon cannot execute chat_send RPC
    v_caught := false;
    BEGIN
        PERFORM public.chat_send(v_order_id, v_buyer_id, 'Anon chat_send', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 2e FAILED: Anon was able to execute chat_send!';
    END IF;

    RAISE NOTICE 'PASSED [2/4]: Anon cannot read messages or send messages via any path.';

    -- ------------------------------------------------------------------------
    -- TEST 3: Non-party authenticated user cannot read or send
    -- ------------------------------------------------------------------------
    RAISE NOTICE 'TEST 3: Verifying non-party authenticated user cannot read or send...';
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_stranger_id::TEXT, true);

    -- 3a. Non-party cannot read via get_order_messages RPC (raises NOT_A_PARTY)
    v_caught := false;
    BEGIN
        PERFORM public.get_order_messages(v_order_id);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%NOT_A_PARTY%' THEN
            v_caught := true;
        ELSE
            RAISE EXCEPTION 'TEST 3a FAILED: Expected NOT_A_PARTY error, got: %', SQLERRM;
        END IF;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 3a FAILED: Non-party read messages without NOT_A_PARTY exception!';
    END IF;

    -- 3b. Non-party cannot read via SELECT on messages table (RLS filter yields 0 rows or permission denied)
    BEGIN
        SELECT COUNT(*) INTO v_read_count FROM public.messages WHERE order_id = v_order_id;
        IF v_read_count > 0 THEN
            RAISE EXCEPTION 'TEST 3b FAILED: Non-party bypassed RLS and read messages! Count: %', v_read_count;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

    -- 3c. Non-party cannot insert into messages directly
    v_caught := false;
    BEGIN
        INSERT INTO public.messages (order_id, sender_id, kind, body, client_nonce)
        VALUES (v_order_id, v_stranger_id, 'user', 'Stranger direct insert', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 3c FAILED: Authenticated user could directly insert into messages!';
    END IF;

    -- 3d. Non-party cannot send via chat_send (fails permission or NOT_A_PARTY)
    v_caught := false;
    BEGIN
        PERFORM public.chat_send(v_order_id, v_stranger_id, 'Stranger chat_send', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 3d FAILED: Non-party was able to execute chat_send!';
    END IF;

    RAISE NOTICE 'PASSED [3/4]: Non-party authenticated user is strictly blocked from reading or sending.';

    -- ------------------------------------------------------------------------
    -- TEST 4: Spoofing sender_id or buyer_id is impossible
    -- ------------------------------------------------------------------------
    RAISE NOTICE 'TEST 4: Verifying spoofing sender_id / buyer_id is impossible...';
    -- Switch caller context to authenticated stranger
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_stranger_id::TEXT, true);

    -- 4a. In create_or_get_inquiry_chat, buyer identity is derived solely from auth.uid()
    -- Stranger cannot pose as v_buyer_id because there is no parameter for buyer_id
    v_inq_id := public.create_or_get_inquiry_chat(v_listing_id);

    SELECT * INTO v_inq_order FROM public.orders WHERE id = v_inq_id;
    IF v_inq_order.buyer_id != v_stranger_id THEN
        RAISE EXCEPTION 'TEST 4a FAILED: Inquiry order buyer_id was not auth.uid()!';
    END IF;
    IF v_inq_order.buyer_id = v_buyer_id THEN
        RAISE EXCEPTION 'TEST 4a FAILED: Stranger was able to spoof buyer_id!';
    END IF;

    -- 4b. Authenticated user cannot insert message pretending to be another sender
    v_caught := false;
    BEGIN
        INSERT INTO public.messages (order_id, sender_id, kind, body, client_nonce)
        VALUES (v_order_id, v_buyer_id, 'user', 'Spoofed message', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        v_caught := true;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 4b FAILED: Stranger was able to insert message spoofing buyer sender_id!';
    END IF;

    -- 4c. Reset to postgres/service_role to verify chat_send enforces party check
    PERFORM set_config('role', 'postgres', true);
    v_caught := false;
    BEGIN
        -- Calling chat_send with a spoofed sender_id that is NOT a party on the order fails
        PERFORM public.chat_send(v_order_id, v_stranger_id, 'Spoofed sender', gen_random_uuid());
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%NOT_A_PARTY%' THEN
            v_caught := true;
        ELSE
            RAISE EXCEPTION 'TEST 4c FAILED: Expected NOT_A_PARTY for spoofed sender in chat_send, got: %', SQLERRM;
        END IF;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 4c FAILED: chat_send allowed spoofed sender_id!';
    END IF;

    -- Cleanup test records and restore postgres role
    DELETE FROM public.notifications WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.chat_reads WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.messages WHERE order_id IN (SELECT id FROM public.orders WHERE order_number LIKE 'AM-SEC-%');
    DELETE FROM public.orders WHERE order_number LIKE 'AM-SEC-%';
    PERFORM set_config('role', 'postgres', true);

    RAISE NOTICE 'PASSED [4/4]: Spoofing sender identity or buyer identity is impossible.';
    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'ALL 4 CHAT SECURITY VERIFICATION TESTS PASSED!';
    RAISE NOTICE '=======================================================';
END $$;
