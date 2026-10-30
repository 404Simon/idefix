package dev.idefix.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [FileEntity::class, RecentFileEntity::class, RecentSearchEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IdefixDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun ftsDao(): FtsDao
    abstract fun recentDao(): RecentDao

    companion object {
        const val NAME = "idefix.db"

        /**
         * FTS5 is not a Room entity: it is created explicitly and verified by an
         * instrumentation test. Room still owns the surrounding schema.
         */
        const val CREATE_FTS_SQL = """
            CREATE VIRTUAL TABLE IF NOT EXISTS file_fts USING fts5(
                filename,
                path,
                content,
                tokenize = 'unicode61 remove_diacritics 2'
            )
        """

        fun build(context: Context): IdefixDatabase {
            return Room.databaseBuilder(context, IdefixDatabase::class.java, NAME)
                .setDriver(BundledSQLiteDriver())
                .addCallback(object : Callback() {
                    override fun onCreate(connection: SQLiteConnection) {
                        connection.execSQL(CREATE_FTS_SQL)
                    }

                    override fun onOpen(connection: SQLiteConnection) {
                        connection.execSQL(CREATE_FTS_SQL)
                    }
                })
                .build()
        }
    }
}
