package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Incident message returned by the AGAP backend.
 *
 * This DTO intentionally supports BOTH:
 *
 * NEW backend fields:
 * - sender_user_id
 * - receiver_user_id
 * - predefined_message_id
 * - custom_message
 * - channel
 * - sent_at
 * - received_at
 * - predefined_code
 * - message_en
 *
 * and the OLD Android/backend fields:
 * - sender_id
 * - sender_role
 * - message_content
 * - message_type
 * - created_at
 *
 * This prevents MissingFieldException when the backend/API version changes.
 */
@Serializable
data class IncidentMessageDto(

    @SerialName("id")
    val id: Long = 0L,

    @SerialName("incident_id")
    val incidentId: Long = 0L,

    // -------------------------------------------------------------------------
    // CURRENT BACKEND FORMAT
    // -------------------------------------------------------------------------

    @SerialName("sender_user_id")
    val senderUserId: Long? = null,

    @SerialName("receiver_user_id")
    val receiverUserId: Long? = null,

    @SerialName("predefined_message_id")
    val predefinedMessageId: Long? = null,

    @SerialName("custom_message")
    val customMessage: String? = null,

    @SerialName("channel")
    val channel: String = "ONLINE",

    @SerialName("sent_at")
    val sentAt: String? = null,

    @SerialName("received_at")
    val receivedAt: String? = null,

    @SerialName("predefined_code")
    val predefinedCode: String? = null,

    @SerialName("message_en")
    val messageEn: String? = null,

    @SerialName("sender_name")
    private val apiSenderName: String? = null,

    @SerialName("receiver_name")
    val receiverName: String? = null,

    @SerialName("client_message_id")
    val clientMessageId: String? = null,

    // -------------------------------------------------------------------------
    // LEGACY FORMAT
    //
    // Keep these optional so older backend responses can still be decoded.
    // -------------------------------------------------------------------------

    @SerialName("sender_id")
    private val legacySenderId: Long? = null,

    @SerialName("sender_role")
    private val legacySenderRole: String? = null,

    @SerialName("message_content")
    private val legacyMessageContent: String? = null,

    @SerialName("message_type")
    private val legacyMessageType: String? = null,

    @SerialName("created_at")
    private val legacyCreatedAt: String? = null
) {

    /**
     * Compatibility property used by older Android UI/repository code.
     */
    val senderId: Long
        get() = senderUserId ?: legacySenderId ?: 0L

    /**
     * Compatibility property used by both old and new incident UI.
     */
    val senderName: String
        get() = apiSenderName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: if (senderId > 0L) {
                "User #$senderId"
            } else {
                "AGAP System"
            }

    /**
     * The current backend returns the authenticated sender role. Older payloads
     * may omit it; never guess COMMAND because that makes responder messages
     * appear to have been sent by an administrator.
     */
    val senderRole: String
        get() = legacySenderRole
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "UNKNOWN"

    /**
     * Resolve the visible message text in this order:
     *
     * 1. Server predefined English message
     * 2. Server custom message
     * 3. Legacy message_content
     * 4. Predefined catalog code
     * 5. Safe fallback
     */
    val messageContent: String
        get() = messageEn
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: customMessage
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: legacyMessageContent
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: predefinedCode
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: "Operational update"

    /**
     * Compatibility message type.
     */
    val messageType: String
        get() = legacyMessageType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: if (predefinedMessageId != null) {
                "STATUS"
            } else {
                "CHAT"
            }

    /**
     * Current backend uses sent_at.
     * Older versions used created_at.
     */
    val createdAt: String
        get() = sentAt
            ?.takeIf { it.isNotBlank() }
            ?: legacyCreatedAt
                ?.takeIf { it.isNotBlank() }
            ?: ""
}

/**
 * Request used when sending an incident message.
 *
 * Supports both:
 *
 * OLD:
 * SendMessageRequestDto("message text")
 *
 * NEW:
 * SendMessageRequestDto(
 *     predefinedMessageId = 4L,
 *     channel = "ONLINE"
 * )
 *
 * The Android UI should normally use predefinedMessageId now.
 */
@Serializable
data class SendMessageRequestDto(

    @SerialName("custom_message")
    val messageContent: String? = null,

    @SerialName("message_type")
    val messageType: String = "CHAT",

    @SerialName("predefined_message_id")
    val predefinedMessageId: Long? = null,

    @SerialName("channel")
    val channel: String = "ONLINE",

    @SerialName("client_message_id")
    val clientMessageId: String? = null
)

/**
 * Persisted payload used when a predefined incident reply cannot immediately
 * reach the server.
 */
@Serializable
data class PendingIncidentReplyPayload(

    @SerialName("incident_key")
    val incidentKey: String,

    @SerialName("packet_hash")
    val packetHash: String? = null,

    @SerialName("predefined_message_id")
    val predefinedMessageId: Long,

    @SerialName("local_message_id")
    val localMessageId: String,

    @SerialName("sender_role")
    val senderRole: String
)