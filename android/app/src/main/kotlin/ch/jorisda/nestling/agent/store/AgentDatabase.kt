package ch.jorisda.nestling.agent.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingHourRow::class, CarryOverRow::class, RawEventRow::class],
    version = 1,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao

    companion object {
        @Volatile
        private var instance: AgentDatabase? = null

        fun get(context: Context): AgentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AgentDatabase::class.java,
                "nestling-agent.db",
            ).build().also { instance = it }
        }
    }
}
