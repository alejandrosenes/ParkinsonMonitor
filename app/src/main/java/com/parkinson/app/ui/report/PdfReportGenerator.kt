package com.parkinson.app.ui.report

import android.content.Context
import android.util.Log
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.layout.properties.UnitValue
import com.parkinson.app.data.health.HealthConnectManager
import com.parkinson.app.data.local.database.dao.TremorDao
import com.parkinson.app.data.local.database.entities.TremorResultEntity
import com.parkinson.app.data.local.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {
    suspend fun generate(
        health: HealthConnectManager,
        repo: MedicationRepository,
        tremorDao: TremorDao,
        days: Int
    ): File = withContext(Dispatchers.IO) {
        val end = Instant.now()
        val start = end.minusSeconds(days.toLong() * 24 * 3600)
        val symptoms = try { health.readSymptoms(start, end) } catch (e: Exception) {
            Log.w("PdfReport", "readSymptoms failed", e); emptyList()
        }
        val intakes = try { repo.observeIntakes().first() } catch (e: Exception) {
            Log.w("PdfReport", "observeIntakes failed", e); emptyList()
        }
        val filteredIntakes = intakes.filter { it.intakeTimeMillis in start.toEpochMilli()..end.toEpochMilli() }
        val tremorResults = try { tremorDao.observeAll().first() } catch (e: Exception) { emptyList() }
        val filteredTremor = tremorResults.filter { it.timestamp in start.toEpochMilli()..end.toEpochMilli() }

        val steps = try { health.readSteps(start, end) } catch (e: Exception) { emptyList() }
        val hr = try { health.readHeartRate(start, end) } catch (e: Exception) { emptyList() }
        val sleep = try { health.readSleepSessions(start, end) } catch (e: Exception) { emptyList() }
        val exercise = try { health.readExerciseSessions(start, end) } catch (e: Exception) { emptyList() }
        val stepsTotal = steps.sumOf { it.count }
        val hrValues = hr.flatMap { r -> r.samples.map { it.beatsPerMinute.toInt() } }
        val hrAvg = if (hrValues.isNotEmpty()) hrValues.average().toInt() else 0
        val hrMin = hrValues.minOrNull() ?: 0
        val hrMax = hrValues.maxOrNull() ?: 0
        val sleepMinutes = sleep.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
        val sports = exercise.filter { com.parkinson.app.data.health.TremorParser.parseRecord(it) == null }
        val sportMinutesByType = java.util.LinkedHashMap<Int, Long>()
        sports.forEach { e ->
            val type = e.exerciseType
            sportMinutesByType[type] = (sportMinutesByType[type] ?: 0L) + java.time.Duration.between(e.startTime, e.endTime).toMinutes()
        }

        val tremor = symptoms.count { it.type.name == "TREMOR" }
        val dysk = symptoms.count { it.type.name == "DYSKINESIA" }
        val avgSeverity = if (symptoms.isNotEmpty()) symptoms.map { it.severity }.average() else 0.0

        val dir = context.getExternalFilesDir("reports") ?: context.filesDir.resolve("reports")
        dir.mkdirs()
        val file = File(dir, "parkinson_report_${days}d_${System.currentTimeMillis()}.pdf")

        try {
            val writer = PdfWriter(file)
            val pdf = PdfDocument(writer)
            val doc = Document(pdf)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            doc.add(Paragraph("Informe - Parkinson Monitor").setFontSize(18f).setTextAlignment(TextAlignment.CENTER))
            doc.add(Paragraph("Periodo: ${sdf.format(Date(start.toEpochMilli()))} a ${sdf.format(Date(end.toEpochMilli()))}").setFontSize(11f))
            doc.add(Paragraph("Generado: ${sdf.format(Date())}").setFontSize(11f))
            doc.add(Paragraph(" "))

            // Summary table
            val table = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
            table.addCell("Metrica")
            table.addCell("Valor")
            table.addCell("Episodios de sintomas")
            table.addCell(symptoms.size.toString())
            table.addCell("  - Temblor")
            table.addCell(tremor.toString())
            table.addCell("  - Discinesia")
            table.addCell(dysk.toString())
            table.addCell("Severidad media")
            table.addCell(String.format(Locale.getDefault(), "%.2f", avgSeverity))
            table.addCell("Tomas de medicacion")
            table.addCell(filteredIntakes.size.toString())
            table.addCell("Pasos (Health)")
            table.addCell(stepsTotal.toString())
            table.addCell("Pasos media/dia")
            table.addCell(if (days > 0) (stepsTotal / days).toString() else "0")
            table.addCell("FC media/min/max")
            table.addCell("$hrAvg / $hrMin / $hrMax bpm")
            table.addCell("Horas de sueno")
            table.addCell("${sleepMinutes / 60}h ${sleepMinutes % 60}m")
            table.addCell("Sueno media/dia")
            val sleepPerDay = if (days > 0) sleepMinutes / days else 0L
            table.addCell("${sleepPerDay / 60}h ${sleepPerDay % 60}m")
            table.addCell("Actividades deportivas (eventos)")
            table.addCell(sports.size.toString())
            sportMinutesByType.forEach { (type, mins) ->
                val typeName = "Tipo $type"
                table.addCell("  - $typeName")
                table.addCell("${mins / 60}h ${mins % 60}m")
            }
            doc.add(table)

            // Daily Health Connect metrics
            doc.add(Paragraph(" "))
            doc.add(Paragraph("Metricas diarias de Health Connect:").setBold())
            val zone = java.time.ZoneId.systemDefault()
            val daysList = (0 until days).map { start.atZone(zone).toLocalDate().plusDays(it.toLong()) }
            val daySteps = java.util.LinkedHashMap<java.time.LocalDate, Long>()
            val dayHr = java.util.LinkedHashMap<java.time.LocalDate, MutableList<Int>>()
            val daySleep = java.util.LinkedHashMap<java.time.LocalDate, Long>()
            val dayExercise = java.util.LinkedHashMap<java.time.LocalDate, Long>()
            daysList.forEach { d ->
                daySteps[d] = 0L
                dayHr[d] = mutableListOf()
                daySleep[d] = 0L
                dayExercise[d] = 0L
            }
            steps.forEach { r ->
                val d = r.startTime.atZone(zone).toLocalDate()
                if (daySteps.containsKey(d)) daySteps[d] = (daySteps[d] ?: 0L) + r.count
            }
            hr.flatMap { r -> r.samples.map { it.time to it.beatsPerMinute.toInt() } }.forEach { (t, bpm) ->
                val d = t.atZone(zone).toLocalDate()
                dayHr[d]?.add(bpm)
            }
            sleep.forEach { r ->
                val d = r.startTime.atZone(zone).toLocalDate()
                if (daySleep.containsKey(d)) daySleep[d] = (daySleep[d] ?: 0L) + java.time.Duration.between(r.startTime, r.endTime).toMinutes()
            }
            exercise.forEach { e ->
                val d = e.startTime.atZone(zone).toLocalDate()
                if (dayExercise.containsKey(d)) dayExercise[d] = (dayExercise[d] ?: 0L) + java.time.Duration.between(e.startTime, e.endTime).toMinutes()
            }
            val td = Table(UnitValue.createPercentArray(floatArrayOf(24f, 18f, 18f, 18f, 22f))).useAllAvailableWidth()
            td.addCell("Fecha")
            td.addCell("Pasos")
            td.addCell("FC media")
            td.addCell("Sueno (min)")
            td.addCell("Ejercicio (min)")
            daysList.forEach { d ->
                td.addCell(d.toString())
                td.addCell((daySteps[d] ?: 0L).toString())
                val hrVals = dayHr[d] ?: emptyList()
                td.addCell(if (hrVals.isNotEmpty()) (hrVals.average().toInt()).toString() else "-")
                td.addCell((daySleep[d] ?: 0L).toString())
                td.addCell((dayExercise[d] ?: 0L).toString())
            }
            doc.add(td)

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Detalle de tomas:").setBold())
            if (filteredIntakes.isEmpty()) {
                doc.add(Paragraph("Sin tomas en el periodo."))
            } else {
                val t2 = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 40f))).useAllAvailableWidth()
                t2.addCell("Medicamento")
                t2.addCell("Dosis (mg)")
                t2.addCell("Fecha/Hora")
                filteredIntakes.sortedBy { it.intakeTimeMillis }.forEach { i ->
                    t2.addCell(i.medicationName)
                    t2.addCell(i.doseMg.toInt().toString())
                    t2.addCell(sdf.format(Date(i.intakeTimeMillis)))
                }
                doc.add(t2)
            }

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Eventos ON/OFF y estado de animo:").setBold())
            val moodEvents = symptoms.filter { it.type.name == "ON" || it.type.name == "OFF" || it.type.name == "MOOD" }
            if (moodEvents.isEmpty()) {
                doc.add(Paragraph("Sin eventos ON/OFF o estado de animo en el periodo."))
            } else {
                val tEvents = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
                tEvents.addCell("Evento")
                tEvents.addCell("Fecha/Hora")
                moodEvents.sortedBy { it.startTime }.forEach { e ->
                    val label = when (e.type.name) {
                        "ON" -> "Entrada en ON"
                        "OFF" -> "Entrada en OFF"
                        "MOOD" -> "Estado de animo ${e.severity.toInt()}"
                        else -> e.type.name
                    }
                    tEvents.addCell(label)
                    tEvents.addCell(sdf.format(Date(e.startTime)))
                }
                doc.add(tEvents)
            }

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Actividades deportivas:").setBold())
            if (sports.isEmpty()) {
                doc.add(Paragraph("Sin actividades deportivas en el periodo."))
            } else {
                val t3 = Table(UnitValue.createPercentArray(floatArrayOf(50f, 20f, 30f))).useAllAvailableWidth()
                t3.addCell("Actividad")
                t3.addCell("Duracion (min)")
                t3.addCell("Fecha/Hora")
                sports.sortedBy { it.startTime }.forEach { e ->
                    t3.addCell(e.title?.takeIf { it.isNotBlank() } ?: "Deporte")
                    t3.addCell(java.time.Duration.between(e.startTime, e.endTime).toMinutes().toString())
                    t3.addCell(sdf.format(Date(e.startTime.toEpochMilli())))
                }
                doc.add(t3)
            }

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Informe mixto (cronologico):").setBold())
            data class MixedEvent(val time: Long, val label: String, val type: String)
            val mixed = mutableListOf<MixedEvent>()
            filteredIntakes.forEach { i ->
                mixed.add(MixedEvent(i.intakeTimeMillis, "${i.medicationName} ${i.doseMg.toInt()} mg", "TOMA"))
            }
            symptoms.filter { it.type.name == "ON" || it.type.name == "OFF" || it.type.name == "MOOD" }.forEach { e ->
                val lbl = when (e.type.name) {
                    "ON" -> "Entrada en ON"
                    "OFF" -> "Entrada en OFF"
                    "MOOD" -> "Estado de animo ${e.severity.toInt()}"
                    else -> e.type.name
                }
                mixed.add(MixedEvent(e.startTime, lbl, e.type.name))
            }
            val sportSessions = exercise.filter { com.parkinson.app.data.health.TremorParser.parseRecord(it) == null }
            sportSessions.forEach { e ->
                mixed.add(MixedEvent(e.startTime.toEpochMilli(), "Ejercicio: ${(e.title?.takeIf { it.isNotBlank() } ?: "Deporte")} ${java.time.Duration.between(e.startTime, e.endTime).toMinutes()}m", "EJERCICIO"))
            }
            mixed.sortBy { it.time }
            if (mixed.isEmpty()) {
                doc.add(Paragraph("Sin eventos en el periodo."))
            } else {
                val tm = Table(UnitValue.createPercentArray(floatArrayOf(22f, 63f, 15f))).useAllAvailableWidth()
                tm.addCell("Hora")
                tm.addCell("Evento")
                tm.addCell("Tipo")
                mixed.forEach { (t, label, type) ->
                    val color = when (type) {
                        "ON" -> DeviceRgb(0f,0f,0f)
                        "OFF" -> DeviceRgb(0f,0f,0f)
                        "EJERCICIO" -> DeviceRgb(0f,0f,0f)
                        "TOMA" -> DeviceRgb(0f,0f,0f)
                        else -> DeviceRgb(0f,0f,0f)
                    }
                    val c1 = com.itextpdf.layout.element.Cell().add(Paragraph(sdf.format(Date(t))).setFontColor(color).setBold())
                    val c2 = com.itextpdf.layout.element.Cell().add(Paragraph(label).setFontColor(color).setBold())
                    val c3 = com.itextpdf.layout.element.Cell().add(Paragraph(type).setFontColor(color).setBold())
                    tm.addCell(c1)
                    tm.addCell(c2)
                    tm.addCell(c3)
                }
                doc.add(tm)
            }

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Analisis automatico:").setBold())
            val onCount = symptoms.count { it.type.name == "ON" }
            val offCount = symptoms.count { it.type.name == "OFF" }
            val exDays = sportSessions.map { java.time.Instant.ofEpochMilli(it.startTime.toEpochMilli()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSet()
            val onOnExDays = symptoms.count { it.type.name == "ON" && java.time.Instant.ofEpochMilli(it.startTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate() in exDays }
            doc.add(Paragraph("Eventos ON: $onCount; OFF: $offCount."))
            if (sportSessions.isNotEmpty()) {
                doc.add(Paragraph("En dias con ejercicio se registraron $onOnExDays entradas ON; fuera de esos dias ${onCount - onOnExDays}."))
            }
            val lowSleepDays = sleep.filter { java.time.Duration.between(it.startTime, it.endTime).toMinutes() < 360 }.map { it.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSet()
            if (lowSleepDays.isNotEmpty()) {
                doc.add(Paragraph("Hay ${lowSleepDays.size} dias con menos de 6h de sueno. Comprueba si aumentan los eventos OFF en los dias siguientes."))
            }

            doc.add(Paragraph(" "))
            doc.add(Paragraph("Pruebas de temblor (historico):").setBold())
            if (filteredTremor.isEmpty()) {
                doc.add(Paragraph("Sin pruebas de temblor en el periodo."))
            } else {
                val tt = Table(UnitValue.createPercentArray(floatArrayOf(30f, 20f, 20f, 30f))).useAllAvailableWidth()
                tt.addCell("Fecha/Hora")
                tt.addCell("Modo")
                tt.addCell("Puntuacion")
                tt.addCell("Freq. pico (Hz)")
                filteredTremor.sortedBy { it.timestamp }.forEach { t ->
                    tt.addCell(sdf.format(Date(t.timestamp)))
                    tt.addCell(t.mode)
                    tt.addCell(String.format(Locale.getDefault(), "%.0f", t.score))
                    tt.addCell(String.format(Locale.getDefault(), "%.2f", t.peakFreqHz))
                }
                doc.add(tt)

                // Simple line chart of scores over time
                doc.add(Paragraph(" "))
                doc.add(Paragraph("Grafica historica de puntuaciones de temblor:").setBold())
                val chartBytes = createTremorChart(filteredTremor)
                val imageData = com.itextpdf.io.image.ImageDataFactory.create(chartBytes)
                val image = com.itextpdf.layout.element.Image(imageData)
                image.setWidth(com.itextpdf.layout.properties.UnitValue.createPointValue(400f))
                doc.add(image)
            }
            doc.add(Paragraph(" "))
            doc.add(Paragraph("Entregar al neurologo para revision del tratamiento.").setItalic().setFontSize(10f))
            doc.close()
            Log.i("PdfReport", "PDF creado: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("PdfReport", "Error generando PDF", e)
            // Cleanup parcial
            if (file.exists()) file.delete()
            throw e
        }
    }

    private fun createTremorChart(results: List<TremorResultEntity>): ByteArray {
        val width = 800
        val height = 300
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 2f
            isAntiAlias = true
        }
        val pointPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1565C0")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1565C0")
            strokeWidth = 3f
            isAntiAlias = true
        }
        val margin = 40f
        val chartLeft = margin
        val chartBottom = height - margin
        val chartWidth = width - 2 * margin
        val chartHeight = height - 2 * margin
        canvas.drawLine(chartLeft, chartBottom, chartLeft + chartWidth, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartLeft, chartBottom - chartHeight, axisPaint)
        val sorted = results.sortedBy { it.timestamp }
        if (sorted.size >= 2) {
            val minTime = sorted.first().timestamp
            val maxTime = sorted.last().timestamp
            val timeRange = (maxTime - minTime).coerceAtLeast(1L).toDouble()
            var prevX = 0f
            var prevY = 0f
            sorted.forEachIndexed { idx, t ->
                val x = chartLeft + (((t.timestamp - minTime).toDouble() / timeRange) * chartWidth).toFloat()
                val y = chartBottom - ((t.score / 100.0) * chartHeight).toFloat()
                if (idx > 0) {
                    canvas.drawLine(prevX, prevY, x, y, linePaint)
                }
                canvas.drawCircle(x, y, 5f, pointPaint)
                prevX = x
                prevY = y
            }
        }
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
