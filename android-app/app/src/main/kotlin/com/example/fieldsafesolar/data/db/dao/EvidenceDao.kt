package com.example.fieldsafesolar.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fieldsafesolar.data.db.entity.EvidenceEntity

/**
 * EvidenceDao: DAO for Evidence database operations
 */
@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(evidence: EvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(evidence: List<EvidenceEntity>)

    @Delete
    suspend fun delete(evidence: EvidenceEntity)

    @Query("SELECT * FROM evidence WHERE evidenceId = :evidenceId")
    suspend fun getById(evidenceId: String): EvidenceEntity?

    @Query("SELECT * FROM evidence WHERE reportId = :reportId ORDER BY createdAt DESC")
    suspend fun getByReportId(reportId: String): List<EvidenceEntity>

    @Query("SELECT * FROM evidence WHERE reportId = :reportId AND stepId = :stepId")
    suspend fun getByReportAndStep(reportId: String, stepId: String): List<EvidenceEntity>

    @Query("DELETE FROM evidence WHERE reportId = :reportId")
    suspend fun deleteByReportId(reportId: String)

    @Query("SELECT COUNT(*) FROM evidence WHERE reportId = :reportId")
    suspend fun getEvidenceCountByReport(reportId: String): Int
}
