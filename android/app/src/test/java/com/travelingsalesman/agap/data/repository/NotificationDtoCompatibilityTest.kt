package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.remote.dto.MarkReadResponseDto
import com.travelingsalesman.agap.data.remote.dto.NotificationDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationDtoCompatibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun backendShapedNotification_mapsToTheExistingLocalPresentationModel() {
        val dto = json.decodeFromString<NotificationDto>(
            """
            {
              "id": 41,
              "user_id": 9,
              "incident_id": 72,
              "type": "DISPATCH_ASSIGNMENT",
              "payload": "Your unit R-12 has been dispatched to incident #72 (MEDICAL)",
              "is_read": false,
              "sent_at": "2026-08-11T10:22:33.456Z"
            }
            """.trimIndent()
        )

        val entity = dto.toNotificationEntity { raw ->
            assertEquals("2026-08-11T10:22:33.456Z", raw)
            1234L
        }

        assertEquals("New dispatch assignment", entity.title)
        assertEquals("Your unit R-12 has been dispatched to incident #72 (MEDICAL)", entity.message)
        assertEquals("72", entity.referenceId)
        assertEquals(1234L, entity.createdAt)
        assertFalse(entity.isRead)
    }

    @Test
    fun markReadPayloadWithoutSuccessField_isStillDecodable() {
        val response = json.decodeFromString<MarkReadResponseDto>(
            """{"message":"marked as read"}"""
        )

        assertEquals(true, response.success)
        assertEquals("marked as read", response.message)
    }
}
