package com.yangshizi.todo

import com.yangshizi.todo.data.sync.BackupHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupHelperTest {

  @Test
  fun `legacy history entries are normalized safely`() {
    val json =
      """
      {
        "history": [
          {
            "logId": 9,
            "taskName": "旧任务",
            "createdAt": 1719820800,
            "duration": 1500000,
            "type": "学习"
          },
          {
            "taskName": "坏记录",
            "timestamp": 0,
            "durationMinutes": 25,
            "category": "工作"
          }
        ]
      }
      """.trimIndent()

    val parsed = BackupHelper.deserializeData(json)

    assertNotNull(parsed)
    val logs = parsed!!.second
    assertEquals(1, logs.size)
    assertEquals("旧任务", logs[0].taskTitle)
    assertEquals(1719820800000L, logs[0].timestamp)
    assertEquals(25, logs[0].durationMinutes)
    assertEquals("学习", logs[0].category)
  }

  @Test
  fun `raw log arrays can be restored as local history`() {
    val json =
      """
      [
        {
          "title": "自由专注",
          "timestamp": 1719820800000,
          "durationMinutes": 30,
          "category": "工作"
        }
      ]
      """.trimIndent()

    val parsed = BackupHelper.deserializeData(json)

    assertNotNull(parsed)
    assertEquals(0, parsed!!.first.size)
    assertEquals(1, parsed.second.size)
    assertEquals(30, parsed.second[0].durationMinutes)
  }

  @Test
  fun `per task durations round trip through backup`() {
    val json =
      """
      {
        "tasks": [
          {
            "id": 1,
            "title": "自定义时长任务",
            "isCompleted": false,
            "category": "学习",
            "estimatedPomodoros": 3,
            "completedPomodoros": 1,
            "timerMode": "countdown",
            "workDurationMin": 45,
            "breakDurationMin": 8,
            "longBreakDurationMin": 20,
            "lastUpdated": 1719820800000
          },
          {
            "id": 2,
            "title": "旧版任务无时长字段",
            "isCompleted": false,
            "category": "工作",
            "estimatedPomodoros": 1,
            "timerMode": "countdown",
            "lastUpdated": 1719820800000
          }
        ]
      }
      """.trimIndent()

    val parsed = BackupHelper.deserializeData(json)

    assertNotNull(parsed)
    val tasks = parsed!!.first
    assertEquals(2, tasks.size)

    // 新备份：自定义时长完整还原
    assertEquals(45, tasks[0].workDurationMin)
    assertEquals(8, tasks[0].breakDurationMin)
    assertEquals(20, tasks[0].longBreakDurationMin)

    // 旧备份：无时长字段解析为 null（跟随全局设置）
    assertEquals(null, tasks[1].workDurationMin)
    assertEquals(null, tasks[1].breakDurationMin)
    assertEquals(null, tasks[1].longBreakDurationMin)

    // 序列化后再解析，字段保持一致
    val serialized = BackupHelper.serializeData(tasks, emptyList())
    val reparsed = BackupHelper.deserializeData(serialized)
    assertNotNull(reparsed)
    assertEquals(45, reparsed!!.first[0].workDurationMin)
    assertEquals(null, reparsed.first[1].longBreakDurationMin)
  }
}
