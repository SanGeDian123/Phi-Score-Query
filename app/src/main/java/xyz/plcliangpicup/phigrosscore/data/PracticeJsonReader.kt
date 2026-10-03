package xyz.plcliangpicup.phigrosscore.data

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import kotlinx.serialization.json.*

/** Only one event/note is materialized at a time, never the complete RPE object tree. */
internal fun practiceJsonReader(reader: Reader) = JsonReader(reader).apply { strictness = Strictness.STRICT }

internal fun JsonReader.practiceObject(): JsonObject {
    val fields = mutableMapOf<String, JsonElement>()
    beginObject()
    while (hasNext()) fields[nextName()] = practiceValue()
    endObject()
    return JsonObject(fields)
}

private fun JsonReader.practiceValue(): JsonElement = when (peek()) {
    JsonToken.BEGIN_OBJECT -> practiceObject()
    JsonToken.BEGIN_ARRAY -> {
        val values = mutableListOf<JsonElement>()
        beginArray()
        while (hasNext()) values += practiceValue()
        endArray()
        JsonArray(values)
    }
    JsonToken.NULL -> { nextNull(); JsonNull }
    JsonToken.BOOLEAN -> JsonPrimitive(nextBoolean())
    // Numeric helpers read content, retaining its original precision and integer spelling.
    JsonToken.NUMBER, JsonToken.STRING -> JsonPrimitive(nextString())
    else -> error("谱面 JSON 格式无效")
}

internal inline fun JsonReader.practiceArray(block: () -> Unit) {
    if (peek() == JsonToken.NULL) { nextNull(); return }
    beginArray()
    while (hasNext()) block()
    endArray()
}

internal fun JsonReader.practiceEndDocument() {
    check(peek() == JsonToken.END_DOCUMENT) { "谱面 JSON 末尾存在无效数据" }
}
