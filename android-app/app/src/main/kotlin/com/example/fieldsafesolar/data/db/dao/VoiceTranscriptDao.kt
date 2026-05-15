package com.example.fieldsafesolar.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldsafesolar.data.db.entity.VoiceTranscriptEntity

/**
 * VoiceTranscriptDao: DAO for VoiceTranscript database operations
 */
@Dao
interface VoiceTranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: VoiceTranscriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transcripts: List<VoiceTranscriptEntity>)

    @Delete
    suspend fun delete(transcript: VoiceTranscriptEntity)

    @Query("SELECT * FROM voice_transcripts WHERE transcriptId = :transcriptId")
    suspend fun getById(transcriptId: String): VoiceTranscriptEntity?

    @Query("SELECT * FROM voice_transcripts WHERE reportId = :reportId ORDER BY timestamp DESC")
    suspend fun getByReportId(reportId: String): List<VoiceTranscriptEntity>

    @Query("SELECT * FROM voice_transcripts WHERE reportId = :reportId AND responseDecision = :decision")
    suspend fun getByReportAndDecision(reportId: String, decision: String): List<VoiceTranscriptEntity>

    @Query("DELETE FROM voice_transcripts WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)

    @Query("SELECT COUNT(*) FROM voice_transcripts WHERE reportId = :reportId")
    suspend fun getTranscriptCountByReport(reportId: String): Int

    @Query("SELECT AVG(confidence) FROM voice_transcripts WHERE reportId = :reportId")
    suspend fun getAverageConfidenceByReport(reportId: String): Float?
}
