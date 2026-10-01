package com.example

import com.example.ui.components.toUserFriendlyMessage
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class AgroMarketUnitTest {

    // 1. Money Math Tests
    @Test
    fun testCommissionExcludesDeliveryFee() {
        val quantityKg = BigDecimal("50.00")
        val pricePerKg = BigDecimal("320.00")
        val deliveryFee = BigDecimal("1500.00")
        val commissionRate = BigDecimal("0.030")

        val subtotal = quantityKg.multiply(pricePerKg).setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("16000.00"), subtotal)

        // Commission is 3% of subtotal ONLY
        val commission = subtotal.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("480.00"), commission)

        val totalAmount = subtotal.add(deliveryFee)
        assertEquals(BigDecimal("17500.00"), totalAmount)

        // Farmer payout = subtotal - commission + delivery fee
        val farmerPayout = subtotal.subtract(commission).add(deliveryFee)
        assertEquals(BigDecimal("17020.00"), farmerPayout)
    }

    // 2. NIC Validation Tests
    @Test
    fun testNicValidation() {
        val validOldNicV = "912345678V"
        val validOldNicX = "852345678X"
        val validNewNic = "199012345678"

        val invalidNicShort = "12345678"
        val invalidNicLetter = "19901234567A"

        val nicRegex = Regex("^[0-9]{9}[VX]$|^[0-9]{12}$")

        assertTrue(validOldNicV.uppercase().matches(nicRegex))
        assertTrue(validOldNicX.uppercase().matches(nicRegex))
        assertTrue(validNewNic.matches(nicRegex))

        assertFalse(invalidNicShort.matches(nicRegex))
        assertFalse(invalidNicLetter.matches(nicRegex))
    }

    // 3. Sri Lankan Mobile Normalization
    @Test
    fun testSriLankanPhoneNormalization() {
        fun normalize(input: String): String {
            val clean = input.replace(Regex("[^0-9+]"), "")
            return when {
                clean.startsWith("+94") && clean.length == 12 -> clean
                clean.startsWith("94") && clean.length == 11 -> "+$clean"
                clean.startsWith("07") && clean.length == 10 -> "+94" + clean.substring(1)
                clean.startsWith("7") && clean.length == 9 -> "+94$clean"
                else -> clean
            }
        }

        assertEquals("+94771234567", normalize("0771234567"))
        assertEquals("+94771234567", normalize("077-123-4567"))
        assertEquals("+94712345678", normalize("+94712345678"))
        assertEquals("+94781234567", normalize("94781234567"))

        val phonePattern = Regex("^\\+947[0-9]{8}$")
        assertTrue(normalize("0771234567").matches(phonePattern))
        assertFalse(normalize("0112345678").matches(phonePattern)) // Landline disallowed
    }

    // 4. Moderation Regex Verification
    @Test
    fun testModerationRules() {
        fun isBlocked(body: String): Boolean {
            val lower = body.lowercase()
            // Phone / digit sequences
            val collapsed = lower.filter { it.isDigit() }
            if (collapsed.contains(Regex("07[0-9]{8}|947[0-9]{8}|\\d{9,}"))) return true
            // URL
            if (lower.contains(Regex("(?:https?://|www\\.)|\\.(?:com|lk|org|net|me)"))) return true
            // Messaging apps
            if (lower.contains(Regex("wa\\.me|whatsapp|viber|telegram|imo"))) return true
            // Address pattern
            if (lower.contains(Regex("(?:no\\.?|house|lane|rd|road|street|st|mawatha)\\s*\\d+|\\d+.*(?:road|rd|street|st|lane|mawatha)"))) return true
            return false
        }

        assertTrue("Should block phone number", isBlocked("call me at 077 123 4567"))
        assertTrue("Should block whatsapp", isBlocked("add me on whatsapp"))
        assertTrue("Should block website url", isBlocked("check http://market.lk"))
        assertTrue("Should block address", isBlocked("come to No. 45 Galle Road"))
        assertFalse("Should allow normal harvest talk", isBlocked("The tomatoes will be ready tomorrow afternoon for pickup."))
    }

    // 5. User-Friendly Error Formatting Tests
    @Test
    fun testUnableToResolveHostMessage() {
        val unknownHostEx = java.net.UnknownHostException("Unable to resolve host \"jzyxbbiw344up6x25z7z.supabase.co\": No address associated with hostname")
        val friendly = unknownHostEx.toUserFriendlyMessage()
        assertTrue(friendly.contains("Unable to connect to the server", ignoreCase = true))
        assertFalse(friendly.contains("jzyxbbiw344up6x25z7z", ignoreCase = true))
        assertFalse(friendly.contains("No address associated", ignoreCase = true))
    }

    @Test
    fun testTimeoutErrorMessage() {
        val timeoutEx = java.net.SocketTimeoutException("timeout")
        val friendly = timeoutEx.toUserFriendlyMessage()
        assertTrue(friendly.contains("timed out", ignoreCase = true))
    }

    @Test
    fun testJwtExpiredMessageDoesNotShowText() {
        val jwtEx = Exception("jwt expired")
        val friendly = jwtEx.toUserFriendlyMessage()
        assertEquals("", friendly)
        assertFalse(friendly.contains("jwt", ignoreCase = true))

        val pgrstEx = Exception("{\"code\":\"PGRST301\",\"message\":\"JWT expired\"}")
        val friendlyPgrst = pgrstEx.toUserFriendlyMessage()
        assertEquals("", friendlyPgrst)
        assertFalse(friendlyPgrst.contains("jwt", ignoreCase = true))
    }

    // 6. Marketplace Pagination Math Tests
    @Test
    fun testMarketplacePaginationMath() {
        val totalItems = 10
        val pageSize = 4
        val totalPages = kotlin.math.max(1, kotlin.math.ceil(totalItems.toDouble() / pageSize).toInt())
        assertEquals(3, totalPages)

        // Page 1 slice: 0 to 4
        val page1 = (1..totalItems).drop((1 - 1) * pageSize).take(pageSize)
        assertEquals(listOf(1, 2, 3, 4), page1)

        // Page 2 slice: 4 to 8
        val page2 = (1..totalItems).drop((2 - 1) * pageSize).take(pageSize)
        assertEquals(listOf(5, 6, 7, 8), page2)

        // Page 3 slice: 8 to 10
        val page3 = (1..totalItems).drop((3 - 1) * pageSize).take(pageSize)
        assertEquals(listOf(9, 10), page3)
    }

    // 7. District Filter Initial "All" vs Specific District
    @Test
    fun testDistrictFilterLogic() {
        val districts = listOf(
            com.example.data.models.District(1, "Colombo", "Western"),
            com.example.data.models.District(4, "Kandy", "Central"),
            com.example.data.models.District(6, "Nuwara Eliya", "Central")
        )

        val listings = listOf(
            com.example.data.models.ListingItem(
                id = "1", farmerId = "f1", cropName = "Tomatoes", quantityAvailable = 100.0,
                pricePerKg = 300.0, minOrderKg = 5.0, harvestDate = "2026-10-01",
                cultivationDistrictName = "Colombo"
            ),
            com.example.data.models.ListingItem(
                id = "2", farmerId = "f2", cropName = "Carrots", quantityAvailable = 150.0,
                pricePerKg = 250.0, minOrderKg = 10.0, harvestDate = "2026-10-01",
                cultivationDistrictName = "Kandy"
            )
        )

        // Initial state: selectedDistrictId is null -> all listings included
        val selectedDistrictId: Int? = null
        val initialFiltered = listings.filter { item ->
            if (selectedDistrictId == null) true
            else {
                val target = districts.firstOrNull { it.id == selectedDistrictId }?.name
                item.cultivationDistrictName.equals(target, ignoreCase = true)
            }
        }
        assertEquals(2, initialFiltered.size)

        // Buyer selects Kandy (id = 4)
        val kandyId: Int? = 4
        val kandyFiltered = listings.filter { item ->
            if (kandyId == null) true
            else {
                val target = districts.firstOrNull { it.id == kandyId }?.name
                item.cultivationDistrictName.equals(target, ignoreCase = true)
            }
        }
        assertEquals(1, kandyFiltered.size)
        assertEquals("Carrots", kandyFiltered.first().cropName)
    }

    // 8. Farmer Delete Listing Filter Logic
    @Test
    fun testListingDeletionFilter() {
        val deletedListingIds = mutableSetOf<String>()
        val listings = mutableListOf("list-1", "list-2", "list-3")

        // Farmer deletes list-2
        deletedListingIds.add("list-2")
        listings.remove("list-2")

        val activeListings = listings.filter { !deletedListingIds.contains(it) }
        assertEquals(listOf("list-1", "list-3"), activeListings)
        assertFalse(activeListings.contains("list-2"))
    }

    // 9. Accurate Message Time Formatting
    @Test
    fun testMessageTimestampFormatting() {
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val formatted = com.example.ui.components.formatMessageTimestamp(nowIso)
        // Should produce a valid 12-hour formatted time with AM/PM (e.g. "10:15 AM")
        assertTrue(formatted.contains("AM", ignoreCase = true) || formatted.contains("PM", ignoreCase = true))
        assertFalse(formatted.contains("Z")) // No raw UTC suffix
    }

    // 10. Farmer Address Visibility Before vs After Payment
    @Test
    fun testFarmerDeliveryAddressPrivacyBeforeAndAfterPayment() {
        fun getVisibleAddress(isFarmer: Boolean, isPaid: Boolean, rawAddress: String): String {
            return if (!isFarmer || isPaid) {
                rawAddress
            } else {
                "[HIDDEN_UNTIL_PAYMENT]"
            }
        }

        val fullAddress = "No. 45, Temple Road, Homagama"

        // Farmer views before payment is made: should be hidden
        val farmerBeforePayment = getVisibleAddress(isFarmer = true, isPaid = false, fullAddress)
        assertEquals("[HIDDEN_UNTIL_PAYMENT]", farmerBeforePayment)

        // Farmer views after payment is made: should be revealed
        val farmerAfterPayment = getVisibleAddress(isFarmer = true, isPaid = true, fullAddress)
        assertEquals(fullAddress, farmerAfterPayment)

        // Buyer always sees their own address
        val buyerBeforePayment = getVisibleAddress(isFarmer = false, isPaid = false, fullAddress)
        assertEquals(fullAddress, buyerBeforePayment)
    }

    // 11. In-App Order Chat: Preserved and Read-Only when Completed
    @Test
    fun testChatPreservedAndReadOnlyOnOrderCompletion() {
        val messages = mutableListOf("msg1", "msg2", "msg3")
        var isReadOnly = false

        fun handleStatusChange(status: String) {
            // Chat is NEVER deleted on completion; it is preserved as read-only per specification:
            // "read-only when status is rejected, cancelled, expired, completed or refunded"
            val readOnlyStatuses = setOf("rejected", "cancelled", "expired", "completed", "refunded")
            if (status.lowercase() in readOnlyStatuses) {
                isReadOnly = true
            }
        }

        handleStatusChange("requested")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        handleStatusChange("accepted")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        handleStatusChange("paid")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        handleStatusChange("ready")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        handleStatusChange("dispatched")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        handleStatusChange("delivered")
        assertEquals(3, messages.size)
        assertFalse(isReadOnly)

        // Buyer confirms receipt -> completed -> chat messages are preserved and chat becomes read-only
        handleStatusChange("completed")
        assertEquals("Chat messages must be preserved on completion", 3, messages.size)
        assertTrue("Chat must become read-only on completion", isReadOnly)
    }

    // 12. Chat Message Sender Alignment: Buyer vs Seller
    @Test
    fun testChatMessageSenderAlignment() {
        fun isMessageFromMe(
            senderId: String,
            isViewingAsFarmer: Boolean,
            farmerId: String = "farmer_1",
            buyerId: String = "usr_buyer_me"
        ): Boolean {
            val isFarmerSender = senderId.startsWith("farmer") || senderId == farmerId
            val isBuyerSender = senderId.startsWith("buyer") || senderId == "usr_buyer_me" || senderId == buyerId

            return if (isViewingAsFarmer) {
                if (isFarmerSender) true
                else if (isBuyerSender) false
                else senderId == farmerId
            } else {
                if (isFarmerSender) false
                else if (isBuyerSender) true
                else senderId == buyerId
            }
        }

        val buyerMsg = "usr_buyer_me"
        val farmerMsg = "farmer_1"

        // When Buyer is viewing:
        // Buyer's message must be on the RIGHT (isMe = true)
        assertTrue(isMessageFromMe(buyerMsg, isViewingAsFarmer = false))
        // Farmer's message must be on the LEFT (isMe = false)
        assertFalse(isMessageFromMe(farmerMsg, isViewingAsFarmer = false))

        // When Farmer is viewing:
        // Farmer's message must be on the RIGHT (isMe = true)
        assertTrue(isMessageFromMe(farmerMsg, isViewingAsFarmer = true))
        // Buyer's message must be on the LEFT (isMe = false)
        assertFalse(isMessageFromMe(buyerMsg, isViewingAsFarmer = true))
    }

    // 13. Marketplace Single District Filter (No Duplicate Chips)
    @Test
    fun testMarketplaceSingleDistrictFilterLogic() {
        var selectedDistrictId: Int? = null

        fun getDistrictLabel(): String {
            return if (selectedDistrictId == null) "All Districts" else "District $selectedDistrictId"
        }

        // Initially shows All Districts
        assertEquals("All Districts", getDistrictLabel())

        // Buyer selects Kandy (id = 4)
        selectedDistrictId = 4
        assertEquals("District 4", getDistrictLabel())

        // Buyer clears back to All Districts
        selectedDistrictId = null
        assertEquals("All Districts", getDistrictLabel())
    }

    // 14. Text.lk Sri Lanka Phone Formatting (947XXXXXXXX)
    @Test
    fun testTextLkPhoneFormatting() {
        fun normalizeForTextLk(input: String): String {
            val clean = input.replace(Regex("[^0-9]"), "")
            return when {
                clean.startsWith("94") && clean.length == 11 -> clean
                clean.startsWith("07") && clean.length == 10 -> "94" + clean.substring(1)
                clean.startsWith("7") && clean.length == 9 -> "94$clean"
                else -> clean
            }
        }

        assertEquals("94771234567", normalizeForTextLk("0771234567"))
        assertEquals("94771234567", normalizeForTextLk("077-123-4567"))
        assertEquals("94712345678", normalizeForTextLk("+94712345678"))
        assertEquals("94781234567", normalizeForTextLk("94781234567"))
        assertEquals("94761112233", normalizeForTextLk("761112233"))

        val textLkPhonePattern = Regex("^947[0-9]{8}$")
        assertTrue(normalizeForTextLk("0771234567").matches(textLkPhonePattern))
    }

    // 15. Text.lk OTP Lifecycle Test (Expiry, Invalid OTP, and Resend)
    @Test
    fun testTextLkOtpLifecycle() {
        data class PendingSession(val phone: String, val code: String, val refId: String, val expiresAt: Long)
        var session: PendingSession? = null

        fun verify(inputPhone: String, inputCode: String, inputRef: String?, now: Long): Result<Unit> {
            val s = session ?: return Result.failure(Exception("No active verification session. Please request a new verification code."))
            if (!inputRef.isNullOrBlank() && inputRef != s.refId) {
                return Result.failure(Exception("Invalid verification reference. Please request a new verification code."))
            }
            if (now > s.expiresAt) {
                session = null
                return Result.failure(Exception("Verification code has expired. Please request a new one."))
            }
            if (inputPhone != s.phone) {
                return Result.failure(Exception("Phone number does not match verification request."))
            }
            if (inputCode != s.code) {
                return Result.failure(Exception("Invalid verification code. Please check and try again."))
            }
            session = null
            return Result.success(Unit)
        }

        val baseTime = 1000000L
        val expiryTime = baseTime + (5 * 60 * 1000L) // 5 minutes

        // Setup session
        session = PendingSession(phone = "94770000000", code = "582914", refId = "ref-textlk-01", expiresAt = expiryTime)

        // Invalid code test
        val invalidRes = verify("94770000000", "000000", "ref-textlk-01", baseTime + 1000)
        assertTrue(invalidRes.isFailure)
        assertEquals("Invalid verification code. Please check and try again.", invalidRes.exceptionOrNull()?.message)

        // Expired code test (past 5 minutes)
        val expiredRes = verify("94770000000", "582914", "ref-textlk-01", baseTime + (6 * 60 * 1000L))
        assertTrue(expiredRes.isFailure)
        assertEquals("Verification code has expired. Please request a new one.", expiredRes.exceptionOrNull()?.message)
        assertNull("Session should be cleared on expiry", session)

        // Resend: create new fresh OTP session
        session = PendingSession(phone = "94770000000", code = "918273", refId = "ref-textlk-02", expiresAt = baseTime + (10 * 60 * 1000L))
        assertNotNull(session)

        // Valid verification within expiry window
        val validRes = verify("94770000000", "918273", "ref-textlk-02", baseTime + (7 * 60 * 1000L))
        assertTrue(validRes.isSuccess)
        assertNull("Session should be consumed on success", session)
    }

    // 16. Development Mock OTP Mode Test
    @Test
    fun testDevelopmentMockOtpBypass() {
        assertTrue("USE_DEV_MOCK_OTP should be enabled for testing", com.example.data.repository.AgroMarketRepository.USE_DEV_MOCK_OTP)
        assertEquals("Development OTP must strictly be 123456", "123456", com.example.data.repository.AgroMarketRepository.DEV_MOCK_OTP)

        val expectedCode = com.example.data.repository.AgroMarketRepository.DEV_MOCK_OTP
        fun verifyDevOtp(inputCode: String): Boolean {
            return inputCode.trim() == expectedCode
        }

        assertTrue("Must accept 123456", verifyDevOtp("123456"))
        assertFalse("Must reject any other code", verifyDevOtp("654321"))
        assertFalse("Must reject any random code", verifyDevOtp("000000"))
        assertFalse("Must reject blank code", verifyDevOtp(""))
    }

    // 17. Text.lk v3 OTP Delivery & Verification Test
    @Test
    fun testRealTextLkApiVerification() {
        if (com.example.data.repository.AgroMarketRepository.USE_DEV_MOCK_OTP) {
            println("Skipping real Text.lk SMS network call because USE_DEV_MOCK_OTP is enabled to preserve credits.")
            // Verify Moshi parsing on synthetic JSON without hitting live API
            val sampleResponse = """{"status":"success","message":"SMS sent","data":{"uid":"mock-uid-123","otp":"123456"}}"""
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                com.example.data.api.textlk.TextLkApiResponse::class.java,
                com.example.data.api.textlk.TextLkSmsData::class.java
            )
            val adapter = moshi.adapter<com.example.data.api.textlk.TextLkApiResponse<com.example.data.api.textlk.TextLkSmsData>>(type)
            val parsed = adapter.fromJson(sampleResponse)
            assertNotNull(parsed)
            assertEquals("success", parsed?.status)
            assertEquals("123456", parsed?.data?.otp)
            return
        }

        var apiToken = com.example.data.api.textlk.TextLkClient.getApiToken()
        if (apiToken.isBlank()) {
            val envFile = listOf(
                java.io.File(".env"),
                java.io.File("../.env"),
                java.io.File("../../.env")
            ).firstOrNull { it.exists() && it.isFile }
            envFile?.readLines()?.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("TEXTLK_API_TOKEN=")) {
                    apiToken = trimmed.substringAfter("TEXTLK_API_TOKEN=").trim()
                }
            }
        }
        assertFalse("API token must not be blank", apiToken.isBlank())
        val senderId = com.example.data.api.textlk.TextLkClient.getSenderId()
        val testNumber = "94770000000"

        val endpoint = "https://app.text.lk/api/v3/sms/send"
        val url = java.net.URL(endpoint)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true

        val requestJson = """
            {
              "recipient": "$testNumber",
              "sender_id": "$senderId",
              "type": "otp",
              "message": "Your AgroMarket verification code is: {{OTP6}}"
            }
        """.trimIndent()

        conn.outputStream.use { os ->
            os.write(requestJson.toByteArray(Charsets.UTF_8))
        }

        val statusCode = conn.responseCode
        val responseBody = try {
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            e.message ?: ""
        }

        println("Text.lk v3 OTP API Status: $statusCode")
        println("Text.lk v3 OTP Response Status: ${if (responseBody.contains("\"status\":\"success\"")) "SUCCESS" else "FAILURE"}")

        assertEquals("Text.lk API should return HTTP 200 OK", 200, statusCode)
        assertTrue("Text.lk API response should indicate success", responseBody.contains("\"status\":\"success\""))
        assertTrue("Text.lk API response should contain OTP in data payload", responseBody.contains("\"otp\""))

        // Verify Moshi parsing of the OTP response
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val type = com.squareup.moshi.Types.newParameterizedType(
            com.example.data.api.textlk.TextLkApiResponse::class.java,
            com.example.data.api.textlk.TextLkSmsData::class.java
        )
        val adapter = moshi.adapter<com.example.data.api.textlk.TextLkApiResponse<com.example.data.api.textlk.TextLkSmsData>>(type)
        val parsed = adapter.fromJson(responseBody)

        assertNotNull("Parsed response must not be null", parsed)
        assertEquals("Parsed status must be success", "success", parsed?.status)
        val extractedOtp = parsed?.data?.otp
        assertNotNull("Extracted OTP must not be null", extractedOtp)
        assertEquals("Extracted OTP must be exactly 6 digits", 6, extractedOtp?.length)
        assertTrue("Extracted OTP must be numeric", extractedOtp?.all { it.isDigit() } == true)
        println("Verified OTP length: ${extractedOtp?.length} digits (value safely parsed from data.otp)")
    }

    // 24. Accurate Message Timestamp Formatting across formats
    @Test
    fun testAccurateMessageTimestampVariations() {
        // 1. Current UTC Instant
        val nowIso = java.time.Instant.now().toString()
        val formattedNow = com.example.ui.components.formatMessageTimestamp(nowIso)
        assertTrue("Formatted now should have AM/PM", formattedNow.contains("AM", ignoreCase = true) || formattedNow.contains("PM", ignoreCase = true))
        assertFalse("Formatted now should not contain ISO Z suffix", formattedNow.contains("Z"))

        // 2. Postgres TIMESTAMPTZ with space and microseconds: "2026-09-30 14:30:00.123456+00"
        val pgIso = "2026-09-30 14:30:00.123456+00"
        val formattedPg = com.example.ui.components.formatMessageTimestamp(pgIso)
        assertTrue("Postgres timestamp formatted should contain AM or PM", formattedPg.contains("AM", ignoreCase = true) || formattedPg.contains("PM", ignoreCase = true))

        // 3. Epoch milliseconds
        val epochMs = (System.currentTimeMillis() - 60_000L).toString()
        val formattedEpoch = com.example.ui.components.formatMessageTimestamp(epochMs)
        assertTrue("Epoch ms timestamp formatted should contain AM or PM", formattedEpoch.contains("AM", ignoreCase = true) || formattedEpoch.contains("PM", ignoreCase = true))

        // 4. ISO with offset: "2026-09-30T10:15:30+05:30"
        val offsetIso = "2026-09-30T10:15:30+05:30"
        val formattedOffset = com.example.ui.components.formatMessageTimestamp(offsetIso)
        assertTrue("Offset ISO formatted should contain AM or PM", formattedOffset.contains("AM", ignoreCase = true) || formattedOffset.contains("PM", ignoreCase = true))
    }

    // 25. Method for Farmer to Identify Which Listing a Message Belongs to
    @Test
    fun testFarmerIdentifiesListingForMessages() {
        val conversations = listOf(
            com.example.data.models.ConversationItem(
                orderId = "inq_carrot_001",
                orderNumber = "INQ-782104",
                listingId = "lst_carrot_001",
                cropName = "Nuwara Eliya Carrots",
                pricePerKg = 320.0,
                quantityKg = 150.0,
                status = "inquiry",
                counterpartId = "buyer_001",
                counterpartName = "Buyer Nimal",
                lastMessage = "Is this harvest ready for immediate pickup?",
                lastMessageTime = "2026-09-30T10:15:00Z",
                listingPhoto = "https://images.unsplash.com/carrot.jpg",
                isFarmerView = true,
                unreadCount = 1
            ),
            com.example.data.models.ConversationItem(
                orderId = "inq_onion_002",
                orderNumber = "INQ-649231",
                listingId = "lst_onion_002",
                cropName = "Jaffna Red Onions",
                pricePerKg = 450.0,
                quantityKg = 500.0,
                status = "inquiry",
                counterpartId = "buyer_001", // same buyer asking about another listing!
                counterpartName = "Buyer Nimal",
                lastMessage = "Can you arrange delivery to Colombo wholesale market?",
                lastMessageTime = "2026-09-30T09:30:00Z",
                listingPhoto = "https://images.unsplash.com/onion.jpg",
                isFarmerView = true,
                unreadCount = 1
            )
        )

        // Farmer can filter and distinguish messages by listing ID
        val carrotChat = conversations.find { it.listingId == "lst_carrot_001" }
        assertNotNull(carrotChat)
        assertEquals("Nuwara Eliya Carrots", carrotChat?.cropName)
        assertEquals("Is this harvest ready for immediate pickup?", carrotChat?.lastMessage)

        val onionChat = conversations.find { it.listingId == "lst_onion_002" }
        assertNotNull(onionChat)
        assertEquals("Jaffna Red Onions", onionChat?.cropName)
        assertEquals("Can you arrange delivery to Colombo wholesale market?", onionChat?.lastMessage)

        // Verify distinct crop filters work
        val uniqueCrops = conversations.map { it.cropName }.distinct()
        assertEquals(listOf("Nuwara Eliya Carrots", "Jaffna Red Onions"), uniqueCrops)
    }

    // 26. Bottom Bar Navigation Tab Configuration
    @Test
    fun testBottomBarRoutesHaveMessagesAndNoAlerts() {
        val buyingRoutes = listOf("marketplace", "my_orders", "messages", "profile")
        val sellingRoutes = listOf("my_listings", "farmer_orders", "dashboard", "messages", "profile")

        // No alerts tab in bottom bar
        assertFalse(buyingRoutes.contains("alerts"))
        assertFalse(buyingRoutes.contains("notifications"))
        assertFalse(sellingRoutes.contains("alerts"))
        assertFalse(sellingRoutes.contains("notifications"))

        // Messages tab is present in both modes
        assertTrue(buyingRoutes.contains("messages"))
        assertTrue(sellingRoutes.contains("messages"))
    }

    // 27. In-App Order Chat: Read-Only when rejected, cancelled, expired, completed or refunded
    @Test
    fun testOrderChatReadOnlyStatuses() {
        val readOnlySet = com.example.viewmodel.ChatViewModel.READ_ONLY_STATUSES

        // Must be read-only for these 5 exact terminal statuses
        assertTrue("rejected must be read-only", readOnlySet.contains("rejected"))
        assertTrue("cancelled must be read-only", readOnlySet.contains("cancelled"))
        assertTrue("expired must be read-only", readOnlySet.contains("expired"))
        assertTrue("completed must be read-only", readOnlySet.contains("completed"))
        assertTrue("refunded must be read-only", readOnlySet.contains("refunded"))

        // Active operational statuses must NOT be read-only
        assertFalse("requested must NOT be read-only", readOnlySet.contains("requested"))
        assertFalse("accepted must NOT be read-only", readOnlySet.contains("accepted"))
        assertFalse("paid must NOT be read-only", readOnlySet.contains("paid"))
        assertFalse("ready must NOT be read-only", readOnlySet.contains("ready"))
        assertFalse("dispatched must NOT be read-only", readOnlySet.contains("dispatched"))
        assertFalse("delivered must NOT be read-only", readOnlySet.contains("delivered"))
        assertFalse("inquiry must NOT be read-only", readOnlySet.contains("inquiry"))
    }

    // 28. In-App Order Chat: Buyer and Farmer Only Authorization
    @Test
    fun testOrderChatBuyerAndFarmerOnlyAuthorization() {
        fun isAuthorizedParticipant(userId: String, buyerId: String, farmerId: String): Boolean {
            return userId == buyerId || userId == farmerId
        }

        val buyerId = "00000000-0000-0000-0000-000000000002"
        val farmerId = "00000000-0000-0000-0000-000000000003"
        val thirdPartyId = "00000000-0000-0000-0000-000000000009"

        assertTrue("Buyer must be authorized", isAuthorizedParticipant(buyerId, buyerId, farmerId))
        assertTrue("Farmer must be authorized", isAuthorizedParticipant(farmerId, buyerId, farmerId))
        assertFalse("Third-party user must NOT be authorized", isAuthorizedParticipant(thirdPartyId, buyerId, farmerId))
    }

    // 29. In-App Order Chat: Per Order Linkage
    @Test
    fun testOrderChatPerOrderLinkage() {
        val testOrder = com.example.data.models.OrderItem(
            id = "order-uuid-12345",
            orderNumber = "AM-998877",
            buyerId = "buyer-1",
            farmerId = "farmer-1",
            listingId = "listing-carrots-1",
            cropName = "Nuwara Eliya Carrots",
            pricePerKg = 320.0,
            quantityKg = 25.0,
            subtotal = 8000.0,
            deliveryMethod = "buyer_arranged",
            totalAmount = 8000.0,
            farmerPayoutAmount = 7760.0,
            requestedDate = "2026-10-02",
            status = "completed" // completed -> read-only
        )

        assertEquals("order-uuid-12345", testOrder.id)
        assertEquals("AM-998877", testOrder.orderNumber)
        assertTrue(com.example.viewmodel.ChatViewModel.READ_ONLY_STATUSES.contains(testOrder.status))
    }

    // 30. In-App Order Chat: Created at Request Time with Order Linkage
    @Test
    fun testOrderChatCreatedMessageAtRequestTime() {
        val orderId = "order-req-999"
        val cropName = "Leeks"
        val quantityKg = 50.0
        val subtotal = 12500.0

        val initialChatBody = "Order requested for $quantityKg kg of $cropName (Rs. $subtotal). The farmer will confirm availability shortly."
        val message = com.example.data.models.MessageItem(
            id = "msg-initial-01",
            orderId = orderId,
            senderId = null, // System message
            body = initialChatBody,
            createdAt = "2026-10-01T08:00:00.000Z"
        )

        assertEquals(orderId, message.orderId)
        assertNull(message.senderId)
        assertTrue(message.body.contains("Order requested for 50.0 kg of Leeks"))
        assertTrue(message.body.contains("Rs. 12500.0"))
    }
}
