package com.example.data.sync

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

        val startMillis: Long = dueDateMs
        val endMillis: Long = dueDateMs + 30 * 60 * 1000 // default 30-minute block for completion/due alarm

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description + "\n\n(来自 💡番茄智伴 Pomodoro Planner 任务同步)")
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
        }

        return try {
            if (existingEventId != null && existingEventId > 0) {
                // Try updating existing event
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val rows = contentResolver.update(updateUri, values, null, null)
                if (rows > 0) {
                    existingEventId
                } else {
                    // If event was deleted in system calendar, create a new one
                    val uri: Uri? = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    uri?.lastPathSegment?.toLongOrNull()
                }
            } else {
                // Insert new event
                val uri: Uri? = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to calendar due to exception", e)
            null
        }
    }

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
