package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TunnelEntity::class,
        DiagnosticHistoryEntity::class,
        CarrierConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tunnelDao(): TunnelDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun carrierConfigDao(): CarrierConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omar_vpn_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
