package ch.jorisda.schirmziit.agent.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PendingHourRow::class,
        CarryOverRow::class,
        RawEventRow::class,
        PlaybackCarryRow::class,
        PlaybackEventRow::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao

    companion object {
        /**
         * Additive, and deliberately not fallbackToDestructiveMigration: that
         * would drop pending_hours on upgrade, and every unsent day with it.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SupportSQLiteDatabase) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_carry (" +
                        "id INTEGER NOT NULL PRIMARY KEY, " +
                        "playing TEXT, " +
                        "screenOff INTEGER NOT NULL, " +
                        "sinceMillis INTEGER)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SupportSQLiteDatabase) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_events (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "atMillis INTEGER NOT NULL, " +
                        "packageName TEXT NOT NULL, " +
                        "started INTEGER NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: AgentDatabase? = null

        fun get(context: Context): AgentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AgentDatabase::class.java,
                "schirmziit-agent.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
