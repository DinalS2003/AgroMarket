-- AgroMarket Phase 1: Triggers, Automated Recomputations & Cron Timers

-- 1. Trigger to recompute farmer stats on order terminal / delivered states
CREATE OR REPLACE FUNCTION trigger_order_stats_recompute()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF (TG_OP = 'UPDATE') THEN
        IF (OLD.status <> NEW.status OR OLD.is_on_time IS DISTINCT FROM NEW.is_on_time) THEN
            PERFORM recompute_farmer_stats(NEW.farmer_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_order_stats ON orders;
CREATE TRIGGER trg_order_stats
AFTER UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION trigger_order_stats_recompute();

-- 2. Trigger on review inserted to recompute farmer stats
CREATE OR REPLACE FUNCTION trigger_review_stats_recompute()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    PERFORM recompute_farmer_stats(NEW.farmer_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_review_stats ON reviews;
CREATE TRIGGER trg_review_stats
AFTER INSERT ON reviews
FOR EACH ROW
EXECUTE FUNCTION trigger_review_stats_recompute();

-- 3. Push Dispatch trigger on notifications insert
CREATE OR REPLACE FUNCTION trigger_notify_push_dispatch()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_url TEXT;
    v_anon_key TEXT;
    v_payload JSONB;
BEGIN
    -- Only dispatch if pg_net extension is loaded
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net') THEN
        v_url := 'http://host.docker.internal:54321/functions/v1/push-dispatch';
        v_payload := jsonb_build_object(
            'notification_id', NEW.id,
            'user_id', NEW.user_id,
            'title', NEW.title,
            'body', NEW.body,
            'type', NEW.type,
            'order_id', NEW.order_id
        );

        -- Non-blocking HTTP POST via pg_net
        PERFORM net.http_post(
            url := v_url,
            headers := jsonb_build_object('Content-Type', 'application/json'),
            body := v_payload
        );
    END IF;
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Never fail the transaction if push fails
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_push_dispatch ON notifications;
CREATE TRIGGER trg_push_dispatch
AFTER INSERT ON notifications
FOR EACH ROW
EXECUTE FUNCTION trigger_notify_push_dispatch();

-- 4. Setup pg_cron for minute-interval order timers if pg_cron exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.unschedule('run-order-timers-every-minute');
        PERFORM cron.schedule(
            'run-order-timers-every-minute',
            '* * * * *',
            'SELECT run_order_timers();'
        );
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- pg_cron will be scheduled when running in cloud Supabase
    NULL;
END;
$$;
