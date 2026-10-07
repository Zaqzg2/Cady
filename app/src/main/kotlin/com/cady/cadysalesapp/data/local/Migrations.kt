package com.cady.cadysalesapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema history. Every statement here must produce EXACTLY what Room expects for the
 * entity (names, types, NOT NULL, index names) — Room validates the result when the database
 * opens, and a mismatch is a crash at startup, so these mirror the generated schema by hand.
 */
object CadyMigrations {

    /** v1 → v2: the offers (promotions) table. Nothing existing is touched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `offers` (" +
                    "`id` TEXT NOT NULL, " +
                    "`productId` TEXT NOT NULL, " +
                    "`productName` TEXT NOT NULL, " +
                    "`percent` REAL NOT NULL, " +
                    "`startsAt` INTEGER NOT NULL, " +
                    "`endsAt` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offers_productId` ON `offers` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offers_syncStatus` ON `offers` (`syncStatus`)")
        }
    }
}
