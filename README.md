# AgroMarket (ශ්‍රී ලංකා ගොවිපොළ)

Production-grade Sri Lankan agricultural marketplace connecting farmers and buyers directly. Farmers list fresh harvest; buyers order with guaranteed delivery coordination; all payments, communication, and fulfillment remain strictly within the platform.

---

## 1. System Architecture

AgroMarket is composed of three interconnected tiers:
1. **Supabase Cloud Backend (`/supabase`)**:
   - PostgreSQL 15 with Row Level Security (RLS) on all tables (default deny).
   - Atomic state transitions via `SECURITY DEFINER` stored procedures using `SELECT ... FOR UPDATE` row locks.
   - Server-side moderated in-app chat with leakage prevention.
   - PayHere payment gateway webhook verification (`payhere-notify`) and SMS OTP dispatch (`send-sms-hook`).
   - Automated order lifecycle cron jobs via `pg_cron` (`run_order_timers()`).
2. **Android Native Application (`/app`, symlinked `/android`)**:
   - Modern Kotlin with Jetpack Compose (Material 3) in portrait orientation.
   - Dual-persona interface with instant toggle between **Buying Mode** and **Selling Mode** in the app bar.
   - Strict privacy safeguards with `FLAG_SECURE` window protection on confidential order views.
   - Offline banner, loading skeletons, and retry error handlers on every screen.
3. **Web Administration Dashboard (`/admin-web`)**:
   - React 18 + Vite + TypeScript + Tailwind CSS.
   - Real-time KPI cards and Recharts analytics.
   - Dispute mediation room with verified chat transcripts, PayHere refund tracking, and CSV payout export.

---

## 2. Environment Variables & Secrets Reference

### Backend (`/supabase/.env`)
| Variable Name | Required | Default / Format | Description |
| :--- | :--- | :--- | :--- |
| `SUPABASE_URL` | Yes | `http://127.0.0.1:54321` | Base URL of the Supabase project instance |
| `SUPABASE_ANON_KEY` | Yes | `eyJ...` | Public client anonymous access token |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | `eyJ...` | Superuser secret token for Edge Functions |
| `SEND_SMS_HOOK_SECRET` | Yes | String | Signing secret to authenticate Supabase Auth hook requests |
| `OTP_MODE` | Yes | `mock` / `live` | `mock` prints OTP to function logs; `live` invokes SMS API |
| `OTP_API_URL` | Live only | `https://api.sms-provider.lk/send` | Sri Lankan SMS gateway HTTP endpoint |
| `OTP_API_KEY` | Live only | String | SMS gateway bearer/API key |
| `OTP_SENDER_ID` | Live only | `AgroMarket` | Approved Sender ID registered with TRCSL |
| `PAYHERE_MODE` | Yes | `sandbox` / `live` | PayHere payment gateway operating mode |
| `PAYHERE_MERCHANT_ID` | Yes | `1234567` | PayHere merchant account identifier |
| `PAYHERE_MERCHANT_SECRET` | Yes | String | PayHere secret key used for MD5 signature calculation |
| `PAYHERE_NOTIFY_URL` | Yes | `https://.../payhere-notify` | Public webhook callback URL for server notifications |
| `FCM_SERVICE_ACCOUNT_JSON` | Optional | JSON string | Google Cloud service account JSON for FCM HTTP v1 |

### Android Application (`BuildConfig` / `local.properties` / `.env`)
| Property Name | Default Value | Description |
| :--- | :--- | :--- |
| `SUPABASE_URL` | `https://jzyxbbiw344up6x25z7z.supabase.co` | Remote Supabase project URL |
| `SUPABASE_ANON_KEY` | `eyJ...` | Anonymous API key |
| `PAYHERE_MERCHANT_ID` | `1234567` | Merchant identifier configured for checkout SDK |
| `PAYHERE_SANDBOX` | `true` | Enables sandbox gateway test environment |
| `PICKME_PACKAGE_ID` | `com.pickme.passenger` | Android package identifier for PickMe delivery launch |
| `PICKME_STORE_URL` | Google Play Store URL | Fallback link when PickMe application is not installed |

### Admin Dashboard (`/admin-web/.env`)
| Variable Name | Default Value | Description |
| :--- | :--- | :--- |
| `VITE_SUPABASE_URL` | `http://127.0.0.1:54321` | Supabase API endpoint |
| `VITE_SUPABASE_ANON_KEY` | Public key | Supabase anonymous API key |

---

## 3. Local Setup & Execution Guide

### 3.1 Backend Deployment (Supabase CLI)
```bash
# 1. Install Supabase CLI
npm install -g supabase

# 2. Start local containerized Supabase stack
cd supabase
supabase start

# 3. Apply schema migrations, functions, and seed data
supabase db reset

# 4. Run database verification test suite
psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" -f tests/agromarket_tests.sql
```

### 3.2 Android App Development
```bash
# Build the application APK
gradle :app:assembleDebug

# Run unit tests and Robolectric tests
gradle :app:testDebugUnitTest
```

### 3.3 Admin Web Dashboard Development
```bash
cd admin-web
npm install
npm run dev
# Dashboard launches at http://localhost:3000
```
Default administrator login from `seed.sql`:
- **Email:** `admin@agromarket.lk`
- **Password:** `Password123!`

---

## 4. Manual QA Verification Checklist

| # | Acceptance Criterion | Test Step | Expected Result | Pass/Fail |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Pre-harvest order blocking** | Select a crop with harvest date 3 days in the future. | "Request Order" button is disabled with label `"Available from <date>"`. Server rejects manual API requests with error. | **PASS** |
| 2 | **Stock reservation & restoration** | Place order for 50 kg; farmer accepts; buyer cancels or payment expires. | Farmer accept immediately deducts 50 kg from available listing stock. Cancellation or 2-hour payment expiry adds 50 kg back. | **PASS** |
| 3 | **Oversell prevention** | Place simultaneous order requests exceeding available stock. | Database row lock `FOR UPDATE` prevents race condition; second accept fails with `"Not enough stock available"`. | **PASS** |
| 4 | **Zero-leakage privacy enforcement** | Query `user_private`, `farmer_private`, or `order_private_details` using regular user token. | Database RLS policy returns 0 rows. Mobile numbers, NICs, cultivation addresses, and bank accounts are unreadable by non-admins. | **PASS** |
| 5 | **Lifecycle address reveal & unmasking** | Track order from `requested` -> `paid` -> `delivered`. | Delivery address is hidden in `requested`/`accepted`; becomes readable to farmer in `paid`/`ready`/`dispatched`; permanently disappears upon `delivered`. | **PASS** |
| 6 | **Commission calculation formula** | Inspect order totals for Rs. 16,000 subtotal with Rs. 1,500 delivery fee. | Commission is exactly Rs. 480.00 (3% of subtotal only). Buyer pays Rs. 17,500.00; farmer receives Rs. 17,020.00 (100% of delivery fee). | **PASS** |
| 7 | **PayHere webhook signature validation** | Send forged HTTP POST to `payhere-notify` with invalid MD5 hash or altered amount. | Webhook returns HTTP 400 Bad Request; order remains in `accepted` status and is never marked paid. | **PASS** |
| 8 | **Chat moderation & leak interception** | Send message containing `"call me on 077 123 4567"` or `"wa.me/..."` or `"No. 45 Galle Road"`. | Message is blocked client & server side; warning banner appears; original content is logged in `flagged_messages` for admin review. | **PASS** |
| 9 | **Reliability & Top Farmer evaluation** | Complete orders with prompt delivery. | Farmer stats trigger recalculates fulfillment rate (50%), on-time rate (30%), and rating (20%). Top Farmer badge is granted at 20+ orders & >=4.5 rating. | **PASS** |
| 10 | **Admin dispute adjudication & payouts** | Open `/payouts` and `/disputes` in the admin dashboard. | Admin can multi-select pending payouts and export CSV; inspect chat transcript for disputed orders; execute full refund, partial refund, or farmer release. | **PASS** |
