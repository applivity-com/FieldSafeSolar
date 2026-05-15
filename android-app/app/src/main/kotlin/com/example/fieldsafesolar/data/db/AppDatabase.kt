package com.example.fieldsafesolar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.fieldsafesolar.data.db.converters.InstantConverter
import com.example.fieldsafesolar.data.db.dao.EvidenceDao
import com.example.fieldsafesolar.data.db.dao.SafetyReportDao
import com.example.fieldsafesolar.data.db.dao.VoiceTranscriptDao
import com.example.fieldsafesolar.data.db.entity.EvidenceEntity
import com.example.fieldsafesolar.data.db.entity.SafetyReportEntity
import com.example.fieldsafesolar.data.db.entity.VoiceTranscriptEntity

/**
 * AppDatabase: Room database for FieldSafe Solar
 * Stores: SafetyReports, Evidence photos, Voice transcripts
 */
@Database(
    entities = [
        SafetyReportEntity::class,
        EvidenceEntity::class,
        VoiceTranscriptEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun safetyReportDao(): SafetyReportDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun voiceTranscriptDao(): VoiceTranscriptDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fieldsafe_solar.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}