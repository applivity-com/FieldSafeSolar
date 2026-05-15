package com.example.fieldsafesolar.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fieldsafesolar.data.db.converters.InstantConverter
import java.time.Instant

/**
 * VoiceTranscriptEntity: Room entity for voice interaction transcripts
 */
@Entity(
    tableName = "voice_transcripts",
    foreignKeys = [
        ForeignKey(
            entity = SafetyReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(InstantConverter::class)
data class VoiceTranscriptEntity(
    @PrimaryKey
    val transcriptId: String,
    val reportId: String,
    val userSpeech: String,
    val aiResponse: String,
    val audioFileUri: String,
    val responseDecision: String?, // PASS, WARN, FAIL, STOP_WORK
    val confidence: Float,
    val timestamp: Instant
) {
    companion object {
        fun create(
            reportId: String,
            userSpeech: String,
            aiResponse: String,
            audioFileUri: String,
            decision: String? = null,
            confidence: Float = 0.8f
        ): VoiceTranscriptEntity {
            return VoiceTranscriptEntity(
                transcriptId = "${reportId}_${System.currentTimeMillis()}",
                reportId = reportId,
                userSpeech = userSpeech,
                aiResponse = aiResponse,
                audioFileUri = audioFileUri,
                responseDecision = decision,
                confidence = confidence,
                timestamp = Instant.now()
            )
        }
    }
}
