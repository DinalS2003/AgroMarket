package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun formatLkr(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "Rs. ${formatter.format(amount)}"
}

fun parseTimestampToDate(rawTimestamp: String?): Date? {
    if (rawTimestamp.isNullOrBlank()) return null
    val raw = rawTimestamp.trim()

    // 1. Numeric epoch timestamp
    raw.toLongOrNull()?.let { epoch ->
        val millis = if (epoch < 10_000_000_000L) epoch * 1000L else epoch
        return Date(millis)
    }

    // 2. High-precision java.time parsing (handles UTC, offsets, microseconds)
    try {
        val instant = java.time.Instant.parse(raw)
        return Date(instant.toEpochMilli())
    } catch (_: Exception) {}

    try {
        val odt = java.time.OffsetDateTime.parse(raw)
        return Date(odt.toInstant().toEpochMilli())
    } catch (_: Exception) {}

    // 3. Normalize Postgres ISO string (e.g. "2026-09-30 17:25:34.123456+00")
    var normalized = raw.replace(" ", "T")
    if (Regex("[+-]\\d{2}$").containsMatchIn(normalized)) {
        normalized += ":00"
    }

    try {
        val odt = java.time.OffsetDateTime.parse(normalized)
        return Date(odt.toInstant().toEpochMilli())
    } catch (_: Exception) {}

    // Truncate microseconds to 3 digits for SimpleDateFormat compatibility
    val microMatch = Regex("(\\.\\d{3})\\d+([Z+-].*)?$").find(normalized)
    val cleanedForSdf = if (microMatch != null) {
        val keep = microMatch.groupValues[1]
        val suffix = microMatch.groupValues[2]
        normalized.substring(0, microMatch.range.first) + keep + suffix
    } else {
        normalized
    }

    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    for (pat in patterns) {
        try {
            val sdf = SimpleDateFormat(pat, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val d = sdf.parse(cleanedForSdf)
            if (d != null) return d
        } catch (_: Exception) {}
    }

    for (pat in patterns) {
        try {
            val sdf = SimpleDateFormat(pat, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val d = sdf.parse(normalized)
            if (d != null) return d
        } catch (_: Exception) {}
    }

    return null
}

fun formatMessageTimestamp(rawTimestamp: String?): String {
    if (rawTimestamp.isNullOrBlank()) return ""
    val parsedDate = parseTimestampToDate(rawTimestamp) ?: run {
        val timeRegex = Regex("(\\d{1,2}:\\d{2})")
        val match = timeRegex.find(rawTimestamp)
        return match?.value ?: ""
    }

    return try {
        val nowCal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = parsedDate }

        val isToday = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

        val isYesterday = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        when {
            isToday -> timeFormat.format(parsedDate)
            isYesterday -> "Yesterday, ${timeFormat.format(parsedDate)}"
            nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> {
                val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                dateFmt.format(parsedDate)
            }
            else -> {
                val dateFmt = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())
                dateFmt.format(parsedDate)
            }
        }
    } catch (_: Exception) {
        try {
            val fallback = SimpleDateFormat("h:mm a", Locale.getDefault())
            fallback.format(parsedDate)
        } catch (_: Exception) {
            ""
        }
    }
}

@Composable
fun OfflineBanner(isOffline: Boolean) {
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().testTag("offline_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = "Offline",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You are currently offline. Showing cached information.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun TopFarmerBadge() {
    Surface(
        color = AgroBadgeTop.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.testTag("top_farmer_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Top Farmer",
                tint = AgroBadgeTop,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "TOP FARMER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = AgroBadgeTop
                )
            )
        }
    }
}

@Composable
fun RatingStars(
    rating: Double,
    reviewCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Rating",
            tint = AgroGoldStar,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = String.format(Locale.US, "%.1f", rating),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (reviewCount != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($reviewCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OrderStatusChip(status: String) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "requested" -> Triple(Color(0xFFFFF9C4), Color(0xFFF57F17), "Requested")
        "accepted" -> Triple(Color(0xFFE1F5FE), Color(0xFF0288D1), "Accepted - Awaiting Payment")
        "paid" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Paid - Preparing")
        "ready" -> Triple(Color(0xFFE0F2F1), Color(0xFF00796B), "Harvest Ready")
        "dispatched" -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "Dispatched")
        "delivered" -> Triple(Color(0xFFC8E6C9), Color(0xFF1B5E20), "Delivered")
        "completed" -> Triple(Color(0xFFDCEDC8), Color(0xFF33691E), "Completed")
        "cancelled" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Cancelled")
        "rejected" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Rejected")
        "expired" -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "Expired")
        "disputed" -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "Disputed")
        "refunded" -> Triple(Color(0xFFE0E0E0), Color(0xFF424242), "Refunded")
        else -> Triple(Color(0xFFEEEEEE), Color(0xFF424242), status.replaceFirstChar { it.uppercase() })
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorRetryView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

fun Throwable?.toUserFriendlyMessage(defaultMessage: String = "Something went wrong. Please try again."): String {
    if (this == null) return defaultMessage
    return friendlyMessageFromString(this.message, defaultMessage)
}

fun friendlyMessageFromString(rawMsg: String?, defaultMessage: String = "Something went wrong. Please try again."): String {
    val msg = rawMsg?.trim() ?: return defaultMessage
    return when {
        msg.contains("type variable", ignoreCase = true) ||
        msg.contains("wildcard", ignoreCase = true) ||
        msg.contains("IllegalArgumentException", ignoreCase = true) ->
            "Unable to complete registration. Please try again."

        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No address associated", ignoreCase = true) ||
        msg.contains("UnknownHostException", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ->
            "Unable to connect to the server. Please check your internet connection."

        msg.contains("timeout", ignoreCase = true) ||
        msg.contains("SocketTimeoutException", ignoreCase = true) ->
            "The request timed out. Please check your connection and try again."

        msg.contains("401", ignoreCase = true) ||
        msg.contains("Unauthorized", ignoreCase = true) ||
        msg.contains("Session Expired", ignoreCase = true) ||
        msg.contains("PGRST301", ignoreCase = true) ||
        msg.contains("token expired", ignoreCase = true) ||
        msg.contains("jwt expired", ignoreCase = true) ||
        msg.contains("jwt", ignoreCase = true) ||
        msg.contains("invalid claim", ignoreCase = true) -> {
            try { com.example.data.api.ApiClient.notifySessionExpired() } catch (ignored: Exception) {}
            ""
        }

        msg.contains("403", ignoreCase = true) ||
        msg.contains("Forbidden", ignoreCase = true) ->
            "You do not have permission to perform this action."

        msg.contains("404", ignoreCase = true) ||
        msg.contains("Not Found", ignoreCase = true) ->
            "The requested information was not found."

        msg.contains("500", ignoreCase = true) ||
        msg.contains("Internal Server", ignoreCase = true) ->
            "Server error occurred. Please try again in a moment."

        msg.contains("duplicate key", ignoreCase = true) ||
        msg.contains("already exists", ignoreCase = true) ->
            "This information is already registered in our system."

        msg.contains("NIC", ignoreCase = true) ->
            "Please provide a valid Sri Lankan NIC number (9 digits with V/X or 12 digits)."

        else -> {
            val clean = msg.replace(Regex("http[^\\s]+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[a-zA-Z0-9_.]+\\.[a-zA-Z0-9_]+Exception:"), "")
                .trim()
            if (clean.length > 100 || clean.contains("{") || clean.contains("}")) {
                defaultMessage
            } else if (clean.isNotBlank()) {
                clean
            } else {
                defaultMessage
            }
        }
    }
}

/**
 * Encodes an image from Android gallery Uri into a compressed JPEG Base64 Data URL
 * suitable for database storage and instant rendering with Coil AsyncImage.
 */
fun encodeImageUriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (originalBitmap == null) return null

        val maxDim = 800
        val w = originalBitmap.width
        val h = originalBitmap.height
        val scaled = if (w > maxDim || h > maxDim) {
            val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
            android.graphics.Bitmap.createScaledBitmap(originalBitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
        } else {
            originalBitmap
        }

        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
        val bytes = out.toByteArray()
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

