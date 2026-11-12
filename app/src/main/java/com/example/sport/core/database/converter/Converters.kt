package com.example.sport.core.database.converter

import androidx.room.TypeConverter
import com.example.sport.core.model.MachineSetupParam
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Istanza JSON condivisa dal layer dati (converter Room e parsing del catalogo). */
internal val SportJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * TypeConverter Room. Gli enum del package `model` non servono qui: Room li salva
 * automaticamente come TEXT con il nome della costante.
 *
 * I converter sono non-null: Room gestisce da sé le colonne nullable.
 */
class Converters {

    // Tipi espliciti: KSP (Room) non vede i serializer generati dal plugin kotlinx.serialization
    // e fallirebbe nel risolvere un tipo inferito.
    private val stringListSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
    private val setupParamsSerializer: KSerializer<List<MachineSetupParam>> =
        ListSerializer(MachineSetupParam.serializer())

    @TypeConverter
    fun instantToEpochMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate): Long = value.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun localTimeToSecondOfDay(value: LocalTime): Int = value.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToLocalTime(value: Int): LocalTime = LocalTime.ofSecondOfDay(value.toLong())

    /** ISO-8601: 1 = lunedì … 7 = domenica (ordinabile in SQL). */
    @TypeConverter
    fun dayOfWeekToIso(value: DayOfWeek): Int = value.value

    @TypeConverter
    fun isoToDayOfWeek(value: Int): DayOfWeek = DayOfWeek.of(value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        SportJson.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        SportJson.decodeFromString(stringListSerializer, value)

    @TypeConverter
    fun setupParamsToJson(value: List<MachineSetupParam>): String =
        SportJson.encodeToString(setupParamsSerializer, value)

    @TypeConverter
    fun jsonToSetupParams(value: String): List<MachineSetupParam> =
        SportJson.decodeFromString(setupParamsSerializer, value)
}
