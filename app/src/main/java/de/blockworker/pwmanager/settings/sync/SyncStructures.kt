package de.blockworker.pwmanager.settings.sync

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import de.blockworker.pwmanager.settings.AppMappingEntity
import de.blockworker.pwmanager.settings.IdentSettingEntity
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.time.Instant

data class SyncRequest(
    val token: String,
    val lastSync: Instant,
    val includeApps: Boolean,
    val idents: List<IdentSettingEntity>,
    val apps: List<AppMappingEntity>
) {
    fun toJson(): String {
        return SyncUtils.gsonInstance.toJson(this)
    }
}

data class SyncResponse(
    val uuid: String,
    val token: String,
    val syncTime: Instant,
    val changedIdents: List<IdentSettingEntity>,
    val changedApps: List<AppMappingEntity>,
    val deletedIdents: List<String>,
    val deletedApps: List<String>,
) {
    companion object {
        fun fromJson(stream: InputStream): SyncResponse? {
            return SyncUtils.gsonInstance.fromJson(
                InputStreamReader(stream, Charsets.UTF_8),
                SyncResponse::class.java
            )
        }
    }

    fun getConfirmation(): SyncConfirmation {
        return SyncConfirmation(uuid, token, syncTime)
    }
}

data class SyncConfirmation(
    val uuid: String,
    val token: String,
    val syncTime: Instant
) {
    fun toJson(): String {
        return SyncUtils.gsonInstance.toJson(this)
    }
}

data class SyncPingResponse(
    val pwmSyncVersion: UInt
) {
    companion object {
        fun fromJson(stream: InputStream): SyncPingResponse? {
            return SyncUtils.gsonInstance.fromJson(
                InputStreamReader(stream, Charsets.UTF_8),
                SyncPingResponse::class.java
            )
        }
    }

    val isValid: Boolean
        get() = SyncUtils.isPingResponseValid(this)
}


private object SyncUtils {

    val gsonInstance = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .create()!!


    fun isPingResponseValid(response: SyncPingResponse): Boolean {
        return response.pwmSyncVersion == 1u
    }


    private class InstantAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {

        override fun serialize(
            src: Instant?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src!!.toString())
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Instant {
            return Instant.parse(json!!.asJsonPrimitive.asString)
        }

    }
}
