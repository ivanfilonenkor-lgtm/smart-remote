package app.smartremote.android.protocol

import java.util.HexFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun clientHelloFixtureHasProtocolOne() {
        val fixture = resource("client-hello.json")
        val objectValue = json.parseToJsonElement(fixture).jsonObject
        assertEquals("client_hello", objectValue["type"]?.toString()?.trim('"'))
        assertEquals("1", objectValue["protocol"].toString())
    }

    @Test
    fun binaryEncoderMatchesRustFixture() {
        val frame = RealtimeFrame(
            kind = RealtimeKind.Pointer,
            streamId = 42u,
            sequence = 7u,
            clientTimestampUs = 1_000_000uL,
            cumulativeX = 1.5f,
            cumulativeY = -2.25f,
        )
        assertEquals(
            resource("realtime-pointer-v1.hex").trim(),
            HexFormat.of().formatHex(frame.encode()),
        )
        assertEquals(REALTIME_FRAME_SIZE, frame.encode().size)
        assertTrue(frame.encode().sliceArray(26..29).all { it == 0.toByte() })
    }

    @Test
    fun gyroCapabilityUsesProtocolOneWithoutChangingTheWireFormat() {
        val encoded = json.encodeToString(
            ClientHello(
                clientId = "550e8400-e29b-41d4-a716-446655440000",
                deviceName = "Air Mouse phone",
                capabilities = listOf("pointer", "gyro_pointer"),
            ),
        )
        val value = json.parseToJsonElement(encoded).jsonObject
        assertEquals("1", value["protocol"].toString())
        assertTrue(value["capabilities"].toString().contains("gyro_pointer"))
    }

    @Test
    fun keyboardControlFixtureKeepsUnicodeText() {
        val value = json.parseToJsonElement(resource("control-type-text.json")).jsonObject
        assertEquals("type_text", value["action"]?.toString()?.trim('"'))
        assertEquals(
            "Привет, Windows! 👋",
            value["payload"]?.jsonObject?.get("text")?.toString()?.trim('"'),
        )
    }

    @Test
    fun keyboardControlSerializesUnicodeText() {
        val encoded = json.encodeToString(
            ControlMessage(
                requestId = "550e8400-e29b-41d4-a716-446655440001",
                action = "type_text",
                payload = kotlinx.serialization.json.JsonObject(
                    mapOf("text" to JsonPrimitive("Тест 👋")),
                ),
            ),
        )
        val value = json.parseToJsonElement(encoded).jsonObject
        assertEquals("Тест 👋", value["payload"]?.jsonObject?.get("text")?.toString()?.trim('"'))
    }

    @Test
    fun animeStateFixtureUsesTheSharedOptionalV1Shape() {
        val value = json.parseToJsonElement(resource("anime-state.json")).jsonObject
        assertEquals("anime_state", value["type"]?.toString()?.trim('"'))
        assertEquals("animevost", value.getValue("site_id").jsonPrimitive.content)
        assertEquals("AnimeVost", value.getValue("site_name").jsonPrimitive.content)
        assertEquals(4, value.getValue("episode").jsonPrimitive.int)
        assertEquals(12, value.getValue("episode_count").jsonPrimitive.int)
        assertTrue(value.getValue("auto_mode").jsonPrimitive.boolean)
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)).readText()
}
