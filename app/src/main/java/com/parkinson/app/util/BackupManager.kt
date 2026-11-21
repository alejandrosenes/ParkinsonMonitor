package com.parkinson.app.util

import android.content.Context
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.data.local.database.entities.MedicationIntake
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import com.parkinson.app.data.local.database.entities.TremorResultEntity
import com.parkinson.app.data.local.repository.MedicationRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val ENTRY = "backup.json"

    private fun app(context: Context) = context.applicationContext as ParkinsonApp

    private fun repo(context: Context): MedicationRepository = app(context).medicationRepository

    suspend fun createBackup(context: Context, outUri: android.net.Uri): Boolean {
        return try {
            val schedules = repo(context).getAllSchedules()
            val intakes = repo(context).getAllIntakes()
            val customMeds = CustomMedStore.getAll(context)
            val favorites = FavoriteMedStore.getAll(context)
            val tremorResults = app(context).tremorDao.observeAll().first()

            val json = JSONObject()
            json.put("version", 1)

            val sArr = JSONArray()
            schedules.forEach { s ->
                val o = JSONObject()
                o.put("id", s.id)
                o.put("name", s.name)
                o.put("doseMg", s.doseMg.toDouble())
                o.put("hour", s.hour)
                o.put("minute", s.minute)
                o.put("enabled", s.enabled)
                sArr.put(o)
            }
            json.put("schedules", sArr)

            val iArr = JSONArray()
            intakes.forEach { i ->
                val o = JSONObject()
                o.put("scheduleId", i.scheduleId ?: JSONObject.NULL)
                o.put("medicationName", i.medicationName)
                o.put("doseMg", i.doseMg.toDouble())
                o.put("intakeTimeMillis", i.intakeTimeMillis)
                o.put("markedByCaregiver", i.markedByCaregiver)
                iArr.put(o)
            }
            json.put("intakes", iArr)

            val cArr = JSONArray()
            customMeds.forEach { c ->
                val o = JSONObject()
                o.put("name", c.name)
                o.put("doseMg", c.doseMg.toDouble())
                cArr.put(o)
            }
            json.put("customMeds", cArr)

            val fArr = JSONArray()
            favorites.forEach { fArr.put(it) }
            json.put("favorites", fArr)

            val tArr = JSONArray()
            tremorResults.forEach { t ->
                val o = JSONObject()
                o.put("mode", t.mode)
                o.put("timestamp", t.timestamp)
                o.put("score", t.score)
                o.put("peakFreqHz", t.peakFreqHz)
                o.put("tremorPower", t.tremorPower)
                o.put("rmsDisplacementMm", t.rmsDisplacementMm)
                if (t.samplesCsv == null) o.put("samplesCsv", JSONObject.NULL) else o.put("samplesCsv", t.samplesCsv)
                tArr.put(o)
            }
            json.put("tremorResults", tArr)

            val settings = JSONObject()
            settings.put("palette", AppSettings.colorPalette.value)
            settings.put("textScale", AppSettings.textScale.value.toDouble())
            json.put("settings", settings)

            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(outUri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    zos.putNextEntry(ZipEntry(ENTRY))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restoreBackup(context: Context, inUri: android.net.Uri): Boolean {
        return try {
            val (jsonText) = readZipEntry(context, inUri, ENTRY) ?: return false
            val json = JSONObject(jsonText)

            // settings
            val settings = json.optJSONObject("settings")
            val palette = settings?.optString("palette", AppSettings.colorPalette.value) ?: AppSettings.colorPalette.value
            val scale = (settings?.optDouble("textScale", AppSettings.textScale.value.toDouble()) ?: AppSettings.textScale.value.toDouble()).toFloat()
            AppSettings.applyPalette(context, palette)
            AppSettings.applyTextScale(context, scale)

            // clear remote
            repo(context).clearAll()
            CustomMedStore.clearAll(context)
            FavoriteMedStore.clearAll(context)

            // schedules
            val scheduleIdMap = linkedMapOf<Long, Long>()
            val sArr = json.optJSONArray("schedules") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                val oldId = if (o.has("id") && !o.isNull("id")) o.optLong("id") else null
                val newId = repo(context).addSchedule(
                    MedicationSchedule(
                        name = o.optString("name", "Levodopa"),
                        doseMg = o.optDouble("doseMg", 100.0).toFloat(),
                        hour = o.optInt("hour", 8),
                        minute = o.optInt("minute", 0),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
                if (oldId != null) scheduleIdMap[oldId] = newId
            }

            // intakes
            val iArr = json.optJSONArray("intakes") ?: JSONArray()
            for (i in 0 until iArr.length()) {
                val o = iArr.getJSONObject(i)
                val rawSid = if (o.isNull("scheduleId")) null else o.optLong("scheduleId")
                val sid = rawSid?.let { scheduleIdMap[it] }
                repo(context).addIntake(
                    MedicationIntake(
                        scheduleId = sid,
                        medicationName = o.optString("medicationName", ""),
                        doseMg = o.optDouble("doseMg", 0.0).toFloat(),
                        intakeTimeMillis = o.optLong("intakeTimeMillis", 0L),
                        markedByCaregiver = o.optBoolean("markedByCaregiver", false)
                    )
                )
            }

            // custom meds
            val cArr = json.optJSONArray("customMeds") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val o = cArr.getJSONObject(i)
                CustomMedStore.add(context, o.optString("name", ""), o.optDouble("doseMg", 100.0).toFloat())
            }

            // favorites
            val fArr = json.optJSONArray("favorites") ?: JSONArray()
            val favSet = LinkedHashSet<String>()
            for (i in 0 until fArr.length()) favSet.add(fArr.getString(i))
            FavoriteMedStore.setAll(context, favSet)

            // tremor results
            app(context).tremorDao.clearAll()
            val tArr = json.optJSONArray("tremorResults") ?: JSONArray()
            for (i in 0 until tArr.length()) {
                val o = tArr.getJSONObject(i)
                app(context).tremorDao.insert(
                    TremorResultEntity(
                        mode = o.optString("mode", "rest"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        score = o.optDouble("score", 0.0),
                        peakFreqHz = o.optDouble("peakFreqHz", 0.0),
                        tremorPower = o.optDouble("tremorPower", 0.0),
                        rmsDisplacementMm = o.optDouble("rmsDisplacementMm", 0.0),
                        samplesCsv = if (o.isNull("samplesCsv")) null else o.optString("samplesCsv", null)
                    )
                )
            }

            // reschedule
            val schedules = repo(context).getAllSchedules()
            com.parkinson.app.util.MedicationAlarmScheduler.scheduleAll(context, schedules)

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun readZipEntry(context: Context, uri: android.net.Uri, name: String): Pair<String, ByteArray>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == name) {
                            return Pair(String(zis.readBytes(), Charsets.UTF_8), ByteArray(0))
                        }
                        entry = zis.nextEntry
                    }
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
