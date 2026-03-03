package com.example.guardianeye.data.local

import androidx.room.TypeConverter
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.model.ContactResult
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromAlertType(type: AlertType): String = type.name

    @TypeConverter
    fun toAlertType(name: String): AlertType = try {
        AlertType.valueOf(name)
    } catch (e: Exception) {
        AlertType.UNKNOWN
    }

    @TypeConverter
    fun fromAlertPriority(priority: AlertPriority): String = priority.name

    @TypeConverter
    fun toAlertPriority(name: String): AlertPriority = try {
        AlertPriority.valueOf(name)
    } catch (e: Exception) {
        AlertPriority.LOW
    }

    @TypeConverter
    fun fromContactResultList(value: List<ContactResult>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toContactResultList(value: String?): List<ContactResult>? {
        val listType = object : TypeToken<List<ContactResult>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromTimestamp(value: Timestamp?): Long? {
        return value?.toDate()?.time
    }

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(Date(it)) }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}
