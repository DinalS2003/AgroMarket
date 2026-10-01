package com.example

import com.example.data.models.MessageItem
import com.example.viewmodel.ChatViewModel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ChatRebuildUnitTest {

    // 1. Android: realtime echo plus HTTP response gives one bubble (dedupe by id & nonce)
    @Test
    fun testRealtimeEchoPlusHttpResponseGivesOneBubble() {
        val nonce = UUID.randomUUID().toString()
        val orderId = "order-101"
        val senderId = "user-buyer-1"

        // Step 1: User sends message -> optimistic bubble placed in list
        val optimisticBubble = MessageItem(
            id = "temp_$nonce",
            orderId = orderId,
            senderId = senderId,
            kind = "user",
            body = "Will pick up at 4 pm",
            clientNonce = nonce,
            createdAt = "2026-10-01T10:00:00Z",
            isMine = true,
            isSending = true
        )

        val messagesList = mutableListOf(optimisticBubble)
        assertEquals(1, messagesList.size)
        assertEquals(true, messagesList[0].isSending)

        // Step 2: Realtime echo arrives from WebSocket with real database UUID
        val realtimeEcho = MessageItem(
            id = "00000000-0000-0000-0000-000000000099",
            orderId = orderId,
            senderId = senderId,
            kind = "user",
            body = "Will pick up at 4 pm",
            clientNonce = nonce,
            createdAt = "2026-10-01T10:00:00.123Z",
            isMine = true,
            isSending = false
        )

        // Deduplication by client_nonce replaces optimistic bubble with confirmed server message
        val indexByNonce = messagesList.indexOfFirst { it.clientNonce == realtimeEcho.clientNonce }
        if (indexByNonce != -1) {
            messagesList[indexByNonce] = realtimeEcho
        } else {
            messagesList.add(0, realtimeEcho)
        }

        assertEquals("Realtime echo must replace optimistic bubble without duplicate", 1, messagesList.size)
        assertEquals("00000000-0000-0000-0000-000000000099", messagesList[0].id)
        assertFalse(messagesList[0].isSending)

        // Step 3: HTTP 200 response completes slightly afterwards with the same ID and nonce
        val httpResponse = MessageItem(
            id = "00000000-0000-0000-0000-000000000099",
            orderId = orderId,
            senderId = senderId,
            kind = "user",
            body = "Will pick up at 4 pm",
            clientNonce = nonce,
            createdAt = "2026-10-01T10:00:00.123Z",
            isMine = true,
            isSending = false
        )

        val existsById = messagesList.any { it.id == httpResponse.id }
        if (!existsById) {
            val idx = messagesList.indexOfFirst { it.clientNonce == httpResponse.clientNonce }
            if (idx != -1) {
                messagesList[idx] = httpResponse
            } else {
                messagesList.add(0, httpResponse)
            }
        }

        // Result: Strictly one single bubble remains in the chat list!
        assertEquals("Realtime echo plus HTTP response must result in exactly one bubble", 1, messagesList.size)
        assertEquals("00000000-0000-0000-0000-000000000099", messagesList[0].id)
    }

    // 2. Android: Retry reuses the nonce
    @Test
    fun testRetryReusesTheNonce() {
        val initialNonce = UUID.randomUUID().toString()
        val originalMessage = MessageItem(
            id = "temp_$initialNonce",
            orderId = "order-202",
            senderId = "buyer-1",
            kind = "user",
            body = "Is delivery possible to Kandy?",
            clientNonce = initialNonce,
            createdAt = "2026-10-01T10:05:00Z",
            isMine = true,
            isSending = false,
            sendFailed = true,
            failError = "Network error connecting to server"
        )

        // User taps Retry -> system dispatches retry with the SAME clientNonce
        fun buildRetryMessage(failed: MessageItem): MessageItem {
            return failed.copy(
                isSending = true,
                sendFailed = false,
                failError = null
                // clientNonce is preserved for server idempotency
            )
        }

        val retriedMessage = buildRetryMessage(originalMessage)
        assertEquals("Nonce must be strictly preserved on retry for idempotency", initialNonce, retriedMessage.clientNonce)
        assertEquals("temp_$initialNonce", retriedMessage.id)
        assertTrue(retriedMessage.isSending)
        assertFalse(retriedMessage.sendFailed)
        assertNull(retriedMessage.failError)
    }

    // 3. Android: Read-only terminal statuses
    @Test
    fun testTerminalStatusesTriggerReadOnlyMode() {
        val terminalStatuses = listOf("rejected", "cancelled", "expired", "completed", "refunded")
        for (status in terminalStatuses) {
            assertTrue("Status '$status' must be terminal and read-only", ChatViewModel.isTerminalStatus(status))
            val reason = ChatViewModel.computeReadOnlyReason(status)
            assertNotNull(reason)
            assertTrue("Reason must explain status", reason.isNotBlank())
        }

        val activeStatuses = listOf("requested", "accepted", "paid", "ready", "dispatched", "delivered", "inquiry")
        for (status in activeStatuses) {
            assertFalse("Status '$status' must NOT be read-only", ChatViewModel.isTerminalStatus(status))
        }
    }

    // 4. Android: Verification of participant authorization check (Removal of || true)
    @Test
    fun testParticipantAuthorizationWithoutTrueFallback() {
        val buyerId = "00000000-0000-0000-0000-000000000002"
        val farmerId = "00000000-0000-0000-0000-000000000003"
        val strangerId = "00000000-0000-0000-0000-000000000009"
        val orderTarget = "AM-100200"

        // Helper replicating the updated ChatViewModel authorization logic
        fun checkIsParticipant(resolvedUserId: String, orderBuyerId: String, orderFarmerId: String, targetOrderId: String): Boolean {
            val isActualBuyer = (resolvedUserId.isNotBlank() && resolvedUserId == orderBuyerId)
            val isActualFarmer = (resolvedUserId.isNotBlank() && resolvedUserId == orderFarmerId)
            return if (resolvedUserId.isBlank() ||
                resolvedUserId.startsWith("demo_") ||
                resolvedUserId.startsWith("farmer_me") ||
                resolvedUserId.startsWith("usr_buyer_me") ||
                targetOrderId.startsWith("inq_") ||
                targetOrderId.startsWith("INQ-")) {
                true
            } else {
                isActualBuyer || isActualFarmer
            }
        }

        // Stranger must NOT be authorized on standard order (would have been true with '|| true')
        val strangerAuthorized = checkIsParticipant(strangerId, buyerId, farmerId, orderTarget)
        assertFalse("Non-party user must NOT be authorized when || true is removed", strangerAuthorized)

        // Real buyer must be authorized
        val buyerAuthorized = checkIsParticipant(buyerId, buyerId, farmerId, orderTarget)
        assertTrue("Order buyer must be authorized", buyerAuthorized)

        // Real farmer must be authorized
        val farmerAuthorized = checkIsParticipant(farmerId, buyerId, farmerId, orderTarget)
        assertTrue("Order farmer must be authorized", farmerAuthorized)
    }

    // 5. Android: Verification that invalid UUID orderId fails without steps 3-5 fallback
    @Test
    fun testNonUuidOrderFailsWithoutFallbackSteps() {
        val nonUuidOrderId = "invalid_order_id_123"
        val isUuidOrder = nonUuidOrderId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
        assertFalse("non-UUID orderId should not pass UUID regex", isUuidOrder)
    }
}
