package com.example.guardianeye.utils

import android.net.Uri
import com.example.guardianeye.model.Alert
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.Date

object AlertSerializer {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Timestamp::class.java, TimestampAdapter())
        .create()

    fun toJson(alert: Alert): String {
        return Uri.encode(gson.toJson(alert))
    }

    fun fromJson(json: String?): Alert? {
        if (json.isNullOrEmpty()) return null
        return try {
            gson.fromJson(Uri.decode(json), Alert::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private class TimestampAdapter : TypeAdapter<Timestamp>() {
        override fun write(out: JsonWriter, value: Timestamp?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value.toDate().time)
            }
        }

        override fun read(`in`: JsonReader): Timestamp? {
            return try {
                val time = `in`.nextLong()
                Timestamp(Date(time))
            } catch (e: Exception) {
                null
            }
        }
    }
}
