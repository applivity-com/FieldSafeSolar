package com.applivity.fieldsafesolar.data.db.converters

import androidx.room.TypeConverter
import java.time.Instant

/**
 * InstantConverter: Room TypeConverter for java.time.Instant
 */
class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? = 
        epochMilli?.let { Instant.ofEpochMilli(it) }
}
