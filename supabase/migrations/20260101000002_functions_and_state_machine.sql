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

-- 2. Register Profile (Step 1 Onboarding)
CREATE OR REPLACE FUNCTION register_profile(
    p_full_name TEXT,
    p_nic TEXT,
    p_phone_e164 TEXT,
    p_district_id INT,
    p_city_id INT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_normalized_nic TEXT;
BEGIN
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
    p_account_number TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID := auth.uid();
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
