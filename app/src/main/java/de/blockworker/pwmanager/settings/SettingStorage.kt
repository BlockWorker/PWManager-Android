package de.blockworker.pwmanager.settings

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.google.gson.annotations.SerializedName
import java.time.Instant

@Entity(tableName = "ident_settings")
data class IdentSettingEntity(
    @SerializedName("ident") @PrimaryKey val identifier: String,
    @SerializedName("iter") val iteration: Int,
    val symbols: String,
    val longpw: Boolean,
    var timestamp: Instant = Instant.EPOCH.plusSeconds(1)
)

data class SettingIdentifier(
    val identifier: String
)

@Entity(tableName = "app_mappings")
data class AppMappingEntity(
    @PrimaryKey val pkg: String,
    @SerializedName("ident") val identifier: String,
    var timestamp: Instant = Instant.EPOCH.plusSeconds(1)
)

data class AppPackage(
    val pkg: String
)

@Entity(tableName = "sync_info")
data class SyncInfoEntity(
    val serverHost: String = "",
    val serverPort: Int = 0,
    val token: String = "",
    var autoSync: Boolean = true,
    var lastSync: Instant = Instant.EPOCH,
    @PrimaryKey val keyAlwaysZero: Int = 0
) {
    val isValid: Boolean
        get() = serverHost.isNotEmpty() && serverPort > 0 && token.isNotEmpty()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM ident_settings")
    fun getAllIdents(): List<IdentSettingEntity>

    @Query("SELECT * FROM app_mappings")
    fun getAllApps(): List<AppMappingEntity>

    @Query("SELECT * FROM ident_settings WHERE identifier = :identifier")
    fun getSettingForIdent(identifier: String): IdentSettingEntity?

    @Query("SELECT * FROM app_mappings WHERE pkg = :pkg")
    fun getIdentForApp(pkg: String): AppMappingEntity?

    @Query("SELECT COUNT(1) FROM ident_settings WHERE identifier = :identifier")
    fun hasIdentifier(identifier: String): Int

    @Query("SELECT * FROM sync_info WHERE keyAlwaysZero = 0")
    fun getSyncInfo(): SyncInfoEntity?

    @Upsert
    fun _internal_insertIdentSettings(vararg settings: IdentSettingEntity)

    fun insertIdentSettings(vararg settings: IdentSettingEntity) {
        val ts = Instant.now()
        for (s in settings) s.timestamp = ts
        _internal_insertIdentSettings(*settings)
    }

    @Upsert
    fun _internal_insertAppMappings(vararg settings: AppMappingEntity)

    fun insertAppMappings(vararg settings: AppMappingEntity) {
        val ts = Instant.now()
        for (s in settings) s.timestamp = ts
        _internal_insertAppMappings(*settings)
    }

    @Upsert
    fun setSyncInfo(info: SyncInfoEntity)

    @Delete(entity = IdentSettingEntity::class)
    fun deleteIdents(vararg identifiers: SettingIdentifier)

    @Delete(entity = AppMappingEntity::class)
    fun deleteApps(vararg pkgs: AppPackage)
}

@Database(entities = [IdentSettingEntity::class, AppMappingEntity::class, SyncInfoEntity::class], version = 1)
@TypeConverters(RoomConverters::class)
private abstract class SettingDatabase : RoomDatabase() {
    abstract fun settingDao(): SettingDao
}

object SettingStorage {
    private var db: SettingDatabase? = null

    fun getSettingDao(context: Context): SettingDao {
        if (db == null) {
            db = Room.databaseBuilder(
                context, SettingDatabase::class.java, "settings-db"
            ).fallbackToDestructiveMigration().build()
        }

        return db!!.settingDao()
    }
}

class RoomConverters {
    @TypeConverter
    fun fromIso(str: String?): Instant? {
        return str?.let { Instant.parse(str) }
    }

    @TypeConverter
    fun toIso(inst: Instant?): String? {
        return inst?.toString()
    }
}
