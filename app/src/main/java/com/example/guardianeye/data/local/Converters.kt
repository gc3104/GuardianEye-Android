package com.example.guardianeye.data.local

import androidx.room.TypeConverter
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.AlertType
import com.google.firebase.Timestamp
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Timestamp?): Long? {
        return value?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(Date(it)) }
    }

    @TypeConverter
    fun fromAlertType(value: AlertType): String {
        return value.name
    }

    @TypeConverter
    fun toAlertType(value: String): AlertType {
        return try {
            AlertType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AlertType.UNKNOWN
        }
    }

    @TypeConverter
    fun fromAlertPriority(value: AlertPriority): String {
        return value.name
    }

    @TypeConverter
    fun toAlertPriority(value: String): AlertPriority {
        return try {
            AlertPriority.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AlertPriority.MEDIUM
        }
    }
}
