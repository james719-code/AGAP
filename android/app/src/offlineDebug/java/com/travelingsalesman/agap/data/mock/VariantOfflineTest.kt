package com.travelingsalesman.agap.data.mock

import android.content.Context
import androidx.room.withTransaction
import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.core.util.DeviceIdProvider
import com.travelingsalesman.agap.data.local.AgapDatabase
import com.travelingsalesman.agap.data.local.entity.*
import com.travelingsalesman.agap.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object VariantOfflineTest {
    fun create(context: Context, json: Json): OfflineTestDataSource = LocalOfflineTestData(context, json)
}

/** A persistent, in-process fixture service. It never calls Chain.proceed or opens a socket. */
internal class LocalOfflineTestData(private val context: Context, private val json: Json) : OfflineTestDataSource {
    override val accounts = listOf(
        LocalTestAccount("test.citizen", "CITIZEN", "offline123"),
        LocalTestAccount("test.responder", "RESPONDER", "offline123"),
        LocalTestAccount("test.admin", "ADMIN", "offline123")
    )
    private val preferences = context.getSharedPreferences("offline_test_data_v1", Context.MODE_PRIVATE)
    private val state: JSONObject = preferences.getString("state", null)?.let(::JSONObject) ?: initialState()

    // Older preview fixtures used incident statuses for assignments. Normalize only
    // the offline mock so role screens exercise the same actions as the real API.
    init {
        var migrated = false
        rows("incidents").filter { it.optString("status") == "PENDING" }.forEach {
            it.put("status", "RECEIVED")
            migrated = true
        }
        rows("dispatches").filter { it.optString("status") == "DISPATCHED" }.forEach {
            it.put("status", "ASSIGNED")
            migrated = true
        }
        rows("agencies").filter { it.optString("type") == "MDRRMO" }.forEach {
            it.put("type", "DRRMO")
            migrated = true
        }
        if (migrated) save()
    }
    private fun now() = Instant.now().toString()
    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
    private fun rows(key: String) = state.getJSONArray(key).let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
    private fun save() { check(preferences.edit().putString("state", state.toString()).commit()) }
    private fun nextId(): Long = (state.optLong("next_id", 99000) + 1).also { state.put("next_id", it) }
    private fun find(key: String, id: Long) = rows(key).firstOrNull { it.optLong("id") == id }
    private fun add(key: String, value: JSONObject): JSONObject = value.also { state.getJSONArray(key).put(it); save() }
    private fun remove(key: String, id: Long) { state.put(key, JSONArray(rows(key).filter { it.optLong("id") != id })); save() }
    private fun merge(target: JSONObject, changes: JSONObject) {
        changes.keys().forEach { key -> if (!changes.isNull(key)) target.put(key, changes.get(key)) }
        save()
    }

    private fun initialState(): JSONObject {
        val timestamp = now()
        val device = DeviceIdProvider(context).getDeviceId()
        val target = BuildConfig.DEFAULT_TARGET_ID
        val users = accounts.mapIndexed { index, account -> obj(
            "id" to 91001L + index, "username" to account.username, "role" to account.role,
            "name" to "Test ${account.role.lowercase().replaceFirstChar { it.titlecase() }}",
            "agency_id" to if (index == 0) null else 81001L,
            "email" to "${account.username}@example.test", "phone_number" to "00000000000",
            "medical_info" to "Sample profile for offline testing only", "is_verified" to true,
            "account_status" to "APPROVED", "device_id" to device, "created_at" to timestamp
        ) }
        val incidents = listOf("MEDICAL", "FIRE", "ACCIDENT").mapIndexed { index, type -> obj(
            "id" to 71001L + index, "incident_type" to type,
            "packet_hash" to (71001L + index).toString(16).padStart(12, '0'),
            "incident_uuid" to "offline-test-incident-${index + 1}",
            "packet_uuid" to "offline-test-packet-${index + 1}",
            "target_id" to obj("raw" to target),
            "latitude" to 13.1391 + index * 0.002, "longitude" to 123.7438 + index * 0.002,
            "status" to listOf("RECEIVED", "DISPATCHED", "RESOLVED")[index],
            "channel" to "BLE_MESH", "delivery_path" to "BLE_RELAY",
            "sender_device_id" to device, "event_timestamp" to timestamp, "created_at" to timestamp,
            "dispatched_unit_id" to if (index == 1) 61001L else null
        ) }
        return obj(
            "next_id" to 99000, "users" to JSONArray(users),
            "agencies" to JSONArray(listOf(obj(
                "id" to 81001L, "code" to "TEST-RESCUE", "name" to "Offline Test Rescue Agency",
                "type" to "DRRMO", "region_code" to "05", "province_code" to "05",
                "muni_code" to "12", "barangay_code" to "01", "contact_number" to "00000000000",
                "latitude" to 13.1391, "longitude" to 123.7438
            ))),
            "units" to JSONArray(listOf(obj("id" to 61001L, "agency_id" to 81001L,
                "unit_callsign" to "TEST RESCUE 1", "target_id" to target, "device_id" to device,
                "status" to "DISPATCHED", "current_lat" to 13.1391, "current_lon" to 123.7438,
                "last_seen_at" to timestamp))),
            "incidents" to JSONArray(incidents),
            "dispatches" to JSONArray(listOf(obj("id" to 51001L, "incident_id" to 71002L,
                "unit_id" to 61001L, "status" to "ASSIGNED", "dispatched_at" to timestamp,
                "unit_callsign" to "TEST RESCUE 1", "agency_name" to "Offline Test Rescue Agency",
                "responder_name" to "Test Responder"))),
            "contacts" to JSONArray(users.map { user -> obj("id" to user.getLong("id"),
                "user_id" to user.getLong("id"), "contact_name" to "Sample Emergency Contact",
                "phone_number" to "00000000000", "relationship" to "Test contact", "is_primary" to true) }),
            "devices" to JSONArray(users.map { user -> obj("id" to user.getLong("id"),
                "user_id" to user.getLong("id"), "device_hardware_id" to device,
                "device_name" to "Offline test phone", "platform" to "ANDROID", "created_at" to timestamp) }),
            "notifications" to JSONArray(users.map { user -> obj("id" to user.getLong("id"),
                "user_id" to user.getLong("id"), "title" to "Offline test is ready",
                "message" to "Sample incidents and profiles are stored on this phone. No alerts are transmitted.",
                "type" to "ALERT", "is_read" to false, "created_at" to timestamp) }),
            "alerts" to JSONArray(listOf(obj("id" to 41001L, "uuid" to "offline-test-alert-1",
                "author_id" to 91003L, "author_name" to "Test Admin", "title" to "Sample community advisory",
                "body" to "This is an offline test advisory. There is no real emergency.", "category" to "GENERAL",
                "severity" to "INFO", "is_active" to true, "published_at" to timestamp, "created_at" to timestamp))),
            "messages" to JSONArray(listOf(obj("id" to 31001L, "incident_id" to 71002L,
                "sender_user_id" to 91003L, "sender_name" to "Test Admin", "sender_role" to "ADMIN",
                "custom_message" to "Sample assignment: check the incident details and response controls.",
                "channel" to "BLE_MESH", "sent_at" to timestamp)))
        )
    }

    override suspend fun prepareDatabase(database: AgapDatabase) = withContext(Dispatchers.IO) {
        // Seed once: edits and queued reports must survive sign-out and account switching.
        if (preferences.getBoolean("room_seeded", false)) {
            // Repair the two legacy seeded records without reseeding or deleting any local reports.
            val canonical = synchronized(this@LocalOfflineTestData) {
                find("incidents", 71001L)?.optString("status") to find("agencies", 81001L)?.optString("type")
            }
            database.withTransaction {
                database.incidentDao().getIncidentById("71001")?.takeIf {
                    it.status == "PENDING" && canonical.first == "RECEIVED"
                }?.let { database.incidentDao().updateIncidentStatus(it.incidentId, "RECEIVED") }
                database.agencyDao().getAgencyById(81001L)?.takeIf {
                    it.type == "MDRRMO" && canonical.second == "DRRMO"
                }?.let { database.agencyDao().insertAgency(it.copy(type = "DRRMO")) }
            }
            return@withContext
        }
        val snapshots = synchronized(this@LocalOfflineTestData) { JSONObject(state.toString()) }
        database.withTransaction {
            val incidents = snapshots.getJSONArray("incidents")
            database.incidentDao().insertIncidents((0 until incidents.length()).map { index ->
                val dto = json.decodeFromString<IncidentDto>(incidents.getJSONObject(index).toString())
                IncidentEntity(dto.id.toString(), dto.targetId?.raw.orEmpty(), dto.incidentType,
                    dto.latitude, dto.longitude, timestamp = System.currentTimeMillis() - index * 60_000,
                    status = dto.status, description = "Offline test incident", packetHash = dto.packetHash,
                    logicalId = dto.packetHash, serverIncidentId = dto.id, channel = "BLE_MESH",
                    deliveryPath = "BLE_RELAY", senderDeviceId = dto.senderDeviceId,
                    dispatchedUnitId = dto.dispatchedUnitId)
            })
            val agency = snapshots.getJSONArray("agencies").getJSONObject(0)
            database.agencyDao().insertAgency(AgencyEntity(agency.getLong("id"), agency.getString("code"),
                agency.getString("name"), agency.getString("type"), latitude = 13.1391, longitude = 123.7438))
            database.responderDao().insertResponder(ResponderUnitEntity(61001L, 81001L,
                targetId = BuildConfig.DEFAULT_TARGET_ID, unitCallsign = "TEST RESCUE 1",
                deviceId = DeviceIdProvider(context).getDeviceId(), status = "DISPATCHED", currentLat = 13.1391, currentLon = 123.7438))
            val catalog = JSONObject(context.assets.open("agap/catalogs.json").bufferedReader().use { it.readText() })
            val types = catalog.getJSONArray("emergency_types")
            database.emergencyTypeDao().insertEmergencyTypes((0 until types.length()).map { index ->
                val item = types.getJSONObject(index)
                EmergencyTypeEntity(item.getLong("id"), item.getString("code"), item.getString("name"),
                    protocolCode = item.optInt("protocol_code"))
            })
            val messages = catalog.getJSONArray("predefined_messages")
            database.predefinedMessageDao().insertMessages((0 until messages.length()).map { index ->
                val item = messages.getJSONObject(index)
                PredefinedMessageEntity(item.getInt("id"), item.getString("code"),
                    protocolCode = item.optInt("protocol_code"), messageText = item.optString("text"),
                    messageType = item.optString("message_type", "STATUS"))
            })
            database.messageDao().insertMessage(IncidentMessageEntity("offline-test-message-1", "71002", 91003L,
                "Test Admin", "ADMIN", "Sample assignment: check the incident details and response controls.",
                "CHAT", System.currentTimeMillis(), channel = "BLE_MESH"))
        }
        check(preferences.edit().putBoolean("room_seeded", true).commit())
    }

    @Synchronized
    override fun respond(request: Request, token: String?): Response {
        val path = request.url.encodedPath.substringAfter("/api/v1/").trim('/')
        val parts = path.split('/')
        val method = request.method
        val body = request.body?.let { value -> Buffer().also { value.writeTo(it) }.readUtf8() }
            ?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        fun reply(value: Any = JSONObject(), code: Int = 200): Response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Offline test")
            .body(value.toString().toResponseBody("application/json".toMediaType())).build()
        fun failure(message: String, code: Int = 400) = reply(obj("error" to message, "message" to message), code)
        fun array(values: List<JSONObject>) = JSONArray(values)
        fun localUser(id: Long): JSONObject? = find("users", id)?.let { user ->
            JSONObject(user.toString()).put("emergency_contacts", array(rows("contacts").filter { it.optLong("user_id") == id }))
                .put("devices", array(rows("devices").filter { it.optLong("user_id") == id }))
        }
        if (path == "auth/login") {
            val username = body.optString("username")
            val user = rows("users").firstOrNull { it.optString("username") == username }
                ?: return failure("Use an offline test account shown above.", 401)
            val password = accounts.firstOrNull { it.username == username }?.password ?: user.optString("test_password")
            if (body.optString("password") != password) return failure("Incorrect offline test password.", 401)
            return reply(obj("token" to "offline-test:${user.getLong("id")}",
                "expires_at" to "2099-01-01T00:00:00Z", "user" to localUser(user.getLong("id"))))
        }
        if (path == "auth/register") {
            if (rows("users").any { it.optString("username") == body.optString("username") }) return failure("Username already exists in this test build.", 409)
            if (body.optString("role") !in listOf("CITIZEN", "RESPONDER")) return failure("Choose Citizen or Responder.")
            val user = JSONObject(body.toString()).put("id", nextId()).put("test_password", body.optString("password"))
                .put("is_verified", true).put("account_status", "APPROVED").put("created_at", now())
            user.remove("password")
            add("users", user)
            return reply(obj("message" to "Local test account created", "user" to user), 201)
        }
        val userId = token?.removePrefix("offline-test:")?.toLongOrNull()
        val user = userId?.let(::localUser)
        // Agency catalog is also used by the public registration form.
        if (parts.first() == "agencies" && method == "GET") {
            return reply(parts.getOrNull(1)?.toLongOrNull()?.let { find("agencies", it) } ?: array(rows("agencies")))
        }
        if (user == null || userId == null) return failure("Sign in to a local test account.", 401)
        if (path == "users/me") {
            if (method == "PUT") merge(find("users", userId)!!, body)
            return reply(localUser(userId)!!)
        }
        if (path.startsWith("users/me/contacts") || path.startsWith("users/me/devices")) {
            val key = parts[2]
            val id = parts.getOrNull(3)?.toLongOrNull()
            val owned = rows(key).filter { it.optLong("user_id") == userId }
            if (method == "GET") return reply(array(owned))
            if (method == "POST") return reply(add(key, JSONObject(body.toString()).put("id", nextId()).put("user_id", userId)), 201)
            if (id == null || owned.none { it.optLong("id") == id }) return failure("Test record not found", 404)
            if (method == "DELETE") { remove(key, id); return reply() }
            val record = find(key, id)!!
            if (parts.last() == "primary") {
                owned.forEach { it.put("is_primary", it.optLong("id") == id) }; save()
            } else merge(record, body)
            return reply(record)
        }
        if (path == "users") return reply(array(rows("users")))
        if (parts.take(2) == listOf("admin", "users")) {
            val id = parts.getOrNull(2)?.toLongOrNull() ?: return failure("Missing user", 404)
            val record = find("users", id) ?: return failure("Unknown test user", 404)
            if (method == "DELETE") { remove("users", id); return reply() }
            if (method == "PUT") {
                when (parts.last()) {
                    "verify" -> record.put("is_verified", true).put("account_status", "APPROVED")
                    "status" -> record.put("account_status", body.optString("status"))
                    else -> merge(record, body)
                }; save()
            }
            return reply(record)
        }
        if (path == "incidents/dispatches") return reply(JSONObject().apply {
            rows("dispatches").groupBy { it.getLong("incident_id") }.forEach { (id, values) -> put(id.toString(), array(values)) }
        })
        if (parts.first() == "incidents") {
            val id = parts.getOrNull(1)?.toLongOrNull()
            if (id == null) {
                val incidents = rows("incidents").filter { incident ->
                    listOf("status", "type").all { query -> request.url.queryParameter(query).isNullOrBlank() ||
                        incident.optString(if (query == "type") "incident_type" else query) == request.url.queryParameter(query) }
                }
                return reply(obj("count" to incidents.size, "incidents" to array(incidents)))
            }
            val incident = find("incidents", id) ?: return failure("Unknown test incident", 404)
            when (parts.getOrNull(2)) {
                null -> return reply(incident)
                "status" -> {
                    incident.put("status", body.getString("status")); save()
                    return reply(obj("status" to "success", "incident_id" to id, "new_status" to body.getString("status")))
                }
                "available-rescue-units" -> return reply(array(rows("units")))
                "dispatch" -> {
                    val unit = find("units", body.getLong("unit_id")) ?: return failure("Unknown test unit", 404)
                    val dispatch = add("dispatches", obj("id" to nextId(), "incident_id" to id, "unit_id" to unit.getLong("id"),
                        "status" to "ASSIGNED", "dispatched_at" to now(), "unit_callsign" to unit.getString("unit_callsign"),
                        "agency_name" to "Offline Test Rescue Agency", "responder_name" to "Test Responder"))
                    incident.put("status", "DISPATCHED").put("dispatched_unit_id", unit.getLong("id")); save()
                    return reply(dispatch, 201)
                }
                "dispatches" -> {
                    val dispatchId = parts.getOrNull(3)?.toLongOrNull()
                    if (dispatchId == null) return reply(array(rows("dispatches").filter { it.optLong("incident_id") == id }))
                    val record = find("dispatches", dispatchId) ?: return failure("Unknown test dispatch", 404)
                    record.put("status", body.getString("status")); save()
                    return reply(obj("status" to "success", "dispatch_id" to dispatchId, "new_status" to body.getString("status")))
                }
                "messages" -> {
                    if (method == "GET") return reply(array(rows("messages").filter { it.optLong("incident_id") == id }))
                    return reply(add("messages", JSONObject(body.toString()).put("id", nextId()).put("incident_id", id)
                        .put("sender_user_id", userId).put("sender_name", user.optString("name"))
                        .put("sender_role", user.getString("role")).put("sent_at", now())), 201)
                }
            }
        }
        if (parts.first() == "rescue-units") {
            if (path == "rescue-units/me/dispatches") return reply(array(rows("dispatches")))
            val id = if (parts.getOrNull(1) == "me") 61001L else parts.getOrNull(1)?.toLongOrNull()
            if (id == null && method == "GET") return reply(array(rows("units")))
            if (id == null && method == "POST") return reply(add("units", JSONObject(body.toString()).put("id", nextId())), 201)
            val unit = id?.let { find("units", it) } ?: return failure("Unknown test unit", 404)
            if (method == "DELETE") { remove("units", unit.getLong("id")); return reply() }
            if (method == "PUT") {
                if (parts.last() == "location") {
                    unit.put("current_lat", body.optDouble("latitude")).put("current_lon", body.optDouble("longitude"))
                    save(); return reply(obj("status" to "success", "responder_id" to id))
                }
                merge(unit, body)
            }
            return reply(unit)
        }
        if (parts.first() == "agencies") {
            val id = parts.getOrNull(1)?.toLongOrNull()
            if (method == "POST") return reply(add("agencies", JSONObject(body.toString()).put("id", nextId())), 201)
            val record = id?.let { find("agencies", it) } ?: return failure("Unknown test agency", 404)
            if (method == "DELETE") { remove("agencies", record.getLong("id")); return reply() }
            merge(record, body); return reply(record)
        }
        if (parts.first() == "notifications") {
            if (method == "GET") return reply(array(rows("notifications").filter { it.optLong("user_id") == userId }))
            parts.getOrNull(1)?.toLongOrNull()?.let { find("notifications", it)?.put("is_read", true) }; save()
            return reply(obj("success" to true))
        }
        if (parts.first() == "alerts") {
            if (method == "GET") return reply(obj("alerts" to array(rows("alerts")), "total" to rows("alerts").size, "limit" to 50, "offset" to 0))
            if (method == "DELETE") { parts.getOrNull(1)?.toLongOrNull()?.let { remove("alerts", it) }; return reply() }
            return reply(add("alerts", JSONObject(body.toString()).put("id", nextId()).put("uuid", "offline-test-${nextId()}")
                .put("author_id", userId).put("author_name", user.optString("name")).put("published_at", now()).put("created_at", now())), 201)
        }
        if (path == "messages/catalog" || path == "emergency-types") {
            val catalog = JSONObject(context.assets.open("agap/catalogs.json").bufferedReader().use { it.readText() })
            if (path == "emergency-types") return reply(catalog.getJSONArray("emergency_types"))
            val items = catalog.getJSONArray("predefined_messages")
            for (index in 0 until items.length()) items.getJSONObject(index).apply {
                put("message_en", optString("text")); put("message_text", optString("text")); put("message_type", optString("message_type", "STATUS"))
            }
            return reply(items)
        }
        if (path == "maps/routing-graph/metadata") return reply(obj("available" to false))
        return failure("This operation is not available in the offline test build: $method $path", 501)
    }
}
