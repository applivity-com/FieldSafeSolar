package com.example.fieldsafesolar.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fieldsafesolar.data.db.entity.SafetyReportEntity

/**
 * SafetyReportDao: DAO for SafetyReport database operations
 */
@Dao
interface SafetyReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: SafetyReportEntity)

    @Update
    suspend fun update(report: SafetyReportEntity)

    @Delete
    suspend fun delete(report: SafetyReportEntity)

    @Query("SELECT * FROM safety_reports WHERE reportId = :reportId")
    suspend fun getById(reportId: String): SafetyReportEntity?

    @Query("SELECT * FROM safety_reports ORDER BY createdAt DESC")
    suspend fun getAllReports(): List<SafetyReportEntity>

    @Query("SELECT * FROM safety_reports WHERE inspectionType = :inspectionType ORDER BY createdAt DESC")
    suspend fun getReportsByType(inspectionType: String): List<SafetyReportEntity>

    @Query("UPDATE safety_reports SET syncStatus = :syncStatus WHERE reportId = :reportId")
    suspend fun updateSyncStatus(reportId: String, syncStatus: String)

    @Query("DELETE FROM safety_reports WHERE reportId = :reportId")
    suspend fun deleteById(reportId: String)

    @Query("SELECT COUNT(*) FROM safety_reports")
    suspend fun getReportCount(): Int
}
