package com.yangshizi.todo.data.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.TimeZone

object CalendarSyncHelper {
    private const val TAG = "CalendarSyncHelper"

    fun hasCalendarPermission(context: Context): Boolean {
        val writePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
        val readPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
        return writePerm == PackageManager.PERMISSION_GRANTED && readPerm == PackageManager.PERMISSION_GRANTED
    }

    private fun getDefaultCalendarId(context: Context): Long {
        if (!hasCalendarPermission(context)) return 1L
        
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        
        var defaultId = 1L
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isPrimary = if (primaryCol >= 0) cursor.getInt(primaryCol) == 1 else false
                    
                    if (isPrimary) {
                        return id // Return immediately if we find primary
                    }
                    defaultId = id // Fallback to last seen calendar ID
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars, fallback to 1L", e)
        }
        return defaultId
    }

    fun syncTaskToCalendar(context: Context, title: String, description: String, dueDateMs: Long, existingEventId: Long?): Long? {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "Calendar permission not granted.")
            return null
        }

        val contentResolver = context.contentResolver
        val calendarId = getDefaultCalendarId(context)
        val timeZone = TimeZone.getDefault().id
        val dueCalendar = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = dueDateMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dueDayStart = dueCalendar.timeInMillis
        val startMillis = if (existingEventId != null && existingEventId > 0) {
            runCatching {
                contentResolver.query(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId),
                    arrayOf(CalendarContract.Events.DTSTART), null, null, null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            }.getOrNull() ?: todayStartMillis()
        } else {
            todayStartMillis()
        }
        val recurrenceEnd = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = dueDayStart
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            add(java.util.Calendar.MILLISECOND, -1)
        }
        val rruleUntil = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).apply {
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }.format(recurrenceEnd.time)
        val recurrenceRule = "FREQ=DAILY;UNTIL=$rruleUntil"
        val endMillis = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = startMillis
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.RRULE, recurrenceRule)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description + "\n\n(来自 💡洋柿子ToDo Pomodoro Planner 任务同步)")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
        }

        return try {
            if (existingEventId != null && existingEventId > 0) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val rows = contentResolver.update(updateUri, values, null, null)
                if (rows > 0) existingEventId else {
                    contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
                }
            } else {
                contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to calendar due to exception", e)
            null
        }
    }

    private fun todayStartMillis(): Long = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun removeCalendarEvent(context: Context, eventId: Long) {
        if (!hasCalendarPermission(context)) return
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar event: $eventId", e)
        }
    }
}
