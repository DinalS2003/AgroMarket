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
