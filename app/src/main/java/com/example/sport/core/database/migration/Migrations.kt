package com.example.sport.core.database.migration

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

/**
 * v1 → v2 (Fase 2, pipeline media):
 * - nuova tabella `exercise_media` (creata automaticamente da Room);
 * - nuova colonna `exercises.content_locale` con default 'it' (automatica);
 * - rimosse `exercises.video_path` / `exercises.thumbnail_path`, sostituite da `exercise_media`.
 *   In v1 erano sempre NULL, quindi nessun dato da trasferire.
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "exercises", columnName = "video_path"),
    DeleteColumn(tableName = "exercises", columnName = "thumbnail_path"),
)
class Migration1To2 : AutoMigrationSpec
