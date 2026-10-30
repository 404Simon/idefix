package dev.idefix.app

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.idefix.app.data.database.IdefixDatabase

object TestDatabase {
    private const val NAME = "idefix-instrumentation-test.db"

    fun create(context: Context): IdefixDatabase {
        context.deleteDatabase(NAME)
        return Room.databaseBuilder(context, IdefixDatabase::class.java, NAME)
            .setDriver(BundledSQLiteDriver())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) {
                    connection.execSQL(IdefixDatabase.CREATE_FTS_SQL)
                }

                override fun onOpen(connection: SQLiteConnection) {
                    connection.execSQL(IdefixDatabase.CREATE_FTS_SQL)
                }
            })
            .build()
    }

    fun cleanup(context: Context) {
        context.deleteDatabase(NAME)
    }
}
