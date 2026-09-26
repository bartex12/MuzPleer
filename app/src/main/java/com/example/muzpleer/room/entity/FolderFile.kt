package com.example.muzpleer.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index(value = ["folderPath"], unique = true)]
)
data class FolderFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderPath: String, // Полный путь к папке
    val folderName: String, // Имя папки (последний сегмент пути)
    val songCount: Int,
    val coverPath: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    var sortOrder: Int = 0 // Новое поле для порядка сортировки
)
