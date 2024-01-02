package de.blockworker.pwmanager.settings.sync

import android.os.Looper
import de.blockworker.pwmanager.settings.AppPackage
import de.blockworker.pwmanager.settings.SettingDao
import de.blockworker.pwmanager.settings.SettingIdentifier
import de.blockworker.pwmanager.settings.SyncInfoEntity
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object SyncManager {

    @Volatile private var syncInProgress: Boolean = false
    private var lastSyncSuccessful: Boolean = true

    val isSyncInProgress: Boolean
        get() = syncInProgress

    val wasLastSyncSuccessful: Boolean
        get() = lastSyncSuccessful

    fun testSyncConnection(info: SyncInfoEntity, callback: (Boolean) -> Unit) {
        if (Looper.getMainLooper().isCurrentThread)
            throw RuntimeException("Do not run sync functions on the main thread.")

        if (!info.isValid) {
            callback(false)
            return
        }

        val url: URL
        try {
            url = URL("https", info.serverHost, info.serverPort, "ping")
        } catch (_: MalformedURLException) {
            callback(false)
            return
        }

        val connection = url.openConnection() as HttpsURLConnection
        var result: Boolean
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val response = SyncPingResponse.fromJson(connection.inputStream)
            result = connection.responseCode == HttpsURLConnection.HTTP_OK
                    && response?.isValid == true
        } catch (e: Exception) {
            result = false
        } finally {
            connection.disconnect()
        }

        callback(result)
    }

    fun sync(dao: SettingDao, autoSyncOnly: Boolean = true, callback: (Boolean) -> Unit = {}) {
        if (Looper.getMainLooper().isCurrentThread)
            throw RuntimeException("Do not run sync functions on the main thread.")

        if (syncInProgress) {
            callback(false)
            return
        }

        syncInProgress = true

        val info = dao.getSyncInfo()
        if (info == null || (autoSyncOnly && !info.autoSync)) {
            callback(false)
            syncInProgress = false
            return
        }

        testSyncConnection(info) { testSuccess ->
            if (!testSuccess) {
                lastSyncSuccessful = false
                callback(false)
                syncInProgress = false
                return@testSyncConnection
            }

            val syncUrl: URL
            val confirmUrl: URL
            try {
                syncUrl = URL("https", info.serverHost, info.serverPort, "sync")
                confirmUrl = URL("https", info.serverHost, info.serverPort, "confirm")
            } catch (_: MalformedURLException) {
                lastSyncSuccessful = false
                callback(false)
                syncInProgress = false
                return@testSyncConnection
            }

            val request = SyncRequest(
                token = info.token,
                lastSync = info.lastSync,
                includeApps = true,
                idents = dao.getAllIdents(),
                apps = dao.getAllApps()
            )

            val syncConnection = syncUrl.openConnection() as HttpsURLConnection
            var response: SyncResponse?
            try {
                syncConnection.doOutput = true
                syncConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val json = request.toJson()
                val jsonBytes = Charsets.UTF_8.encode(json).array()
                syncConnection.setFixedLengthStreamingMode(jsonBytes.size)
                syncConnection.outputStream.write(jsonBytes)

                response = SyncResponse.fromJson(syncConnection.inputStream)
            } catch (_: Exception) {
                response = null
            } finally {
                syncConnection.disconnect()
            }

            if (response == null) {
                lastSyncSuccessful = false
                callback(false)
                syncInProgress = false
                return@testSyncConnection
            }

            val confirmation = response.getConfirmation()

            val confirmConnection = confirmUrl.openConnection() as HttpsURLConnection
            var success: Boolean
            try {
                confirmConnection.doOutput = true
                confirmConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val json = confirmation.toJson()
                val jsonBytes = Charsets.UTF_8.encode(json).array()
                confirmConnection.setFixedLengthStreamingMode(jsonBytes.size)
                confirmConnection.outputStream.write(jsonBytes)

                success = confirmConnection.responseCode == HttpsURLConnection.HTTP_OK
            } catch (_: Exception) {
                success = false
            } finally {
                syncConnection.disconnect()
            }

            if (!success) {
                lastSyncSuccessful = false
                callback(false)
                syncInProgress = false
                return@testSyncConnection
            }

            dao.deleteIdents(*response.deletedIdents.map { SettingIdentifier(it) }.toTypedArray())
            dao.insertIdentSettings(*response.changedIdents.toTypedArray())
            dao.deleteApps(*response.deletedApps.map { AppPackage(it) }.toTypedArray())
            dao.insertAppMappings(*response.changedApps.toTypedArray())

            info.lastSync = response.syncTime
            dao.setSyncInfo(info)

            lastSyncSuccessful = true
            callback(true)
        }

        syncInProgress = false
    }

}