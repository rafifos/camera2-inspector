package dev.rafifos.camera2inspector.export

import android.content.Context
import android.net.Uri
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.InspectionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportExporter {

    private val FILE_TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US)

    fun defaultFileName(now: ZonedDateTime = ZonedDateTime.now()): String =
        "camera2_report_${now.format(FILE_TIMESTAMP)}.json"

    fun toJsonString(report: InspectionReport): String = ReportJson.toJsonString(report)

    suspend fun writeToUri(context: Context, uri: Uri, report: InspectionReport) {
        val payload = ReportJson.toJsonString(report)
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException(context.getString(R.string.export_open_failed))
            stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        }
    }
}
