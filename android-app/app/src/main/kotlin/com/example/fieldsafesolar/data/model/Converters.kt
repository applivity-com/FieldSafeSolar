package com.example.fieldsafesolar.data.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String>? {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromDecision(value: SafetyReport.Decision?): String? {
        return value?.name
    }

    @TypeConverter
    fun toDecision(value: String?): SafetyReport.Decision? {
        return value?.let { SafetyReport.Decision.valueOf(it) }
    }

    @TypeConverter
    fun fromSeverity(value: SafetyReport.Severity?): String? {
        return value?.name
    }

    @TypeConverter
    fun toSeverity(value: String?): SafetyReport.Severity? {
        return value?.let { SafetyReport.Severity.valueOf(it) }
    }

    @TypeConverter
    fun fromSyncStatus(value: SafetyReport.SyncStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SafetyReport.SyncStatus? {
        return value?.let { SafetyReport.SyncStatus.valueOf(it) }
    }

    @TypeConverter
    fun fromInspectionType(value: InspectionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toInspectionType(value: String?): InspectionType? {
        return value?.let { InspectionType.valueOf(it) }
    }

    @TypeConverter
    fun fromEvidenceList(value: List<SafetyReport.Evidence>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toEvidenceList(value: String): List<SafetyReport.Evidence>? {
        val type = object : TypeToken<List<SafetyReport.Evidence>>() {}.type
        return gson.fromJson(value, type)
    }
}