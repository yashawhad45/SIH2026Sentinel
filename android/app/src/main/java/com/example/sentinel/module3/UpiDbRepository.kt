package com.example.sentinel.module3

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

/**
 * UpiDbRepository
 *
 * Reads from the local SQLite simulation database (upi_simulation_database.db)
 * bundled in the app's assets. For each UPI ID (user_id), it returns
 * a list of transaction rows as Map<String, Any> — the exact format
 * expected by the ML server's /check-transaction endpoint.
 */
object UpiDbRepository {

    private const val DB_NAME = "upi_simulation_database.db"

    /**
     * Fetches all transactions for the given user_id from the bundled SQLite database.
     * Returns an empty list if user not found or DB not available.
     */
    fun getTransactionsByUserId(context: Context, userId: String): List<Map<String, Any>> {
        val dbFile = copyDbToFilesDir(context) ?: return emptyList()

        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val cursor = db.rawQuery(
                "SELECT * FROM transactions WHERE user_id = ?",
                arrayOf(userId)
            )

            val results = mutableListOf<Map<String, Any>>()
            if (cursor.moveToFirst()) {
                do {
                    val row = mutableMapOf<String, Any>()
                    for (i in 0 until cursor.columnCount) {
                        val colName = cursor.getColumnName(i)
                        // Store as appropriate type
                        row[colName] = when {
                            cursor.getType(i) == android.database.Cursor.FIELD_TYPE_FLOAT ->
                                cursor.getDouble(i)
                            cursor.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER ->
                                cursor.getInt(i)
                            else -> cursor.getString(i) ?: ""
                        }
                    }
                    results.add(row)
                } while (cursor.moveToNext())
            }

            cursor.close()
            db.close()
            results
        } catch (e: SQLiteException) {
            emptyList()
        }
    }

    /**
     * Copies the database from assets to the app's files directory on first run.
     */
    private fun copyDbToFilesDir(context: Context): java.io.File? {
        val outFile = java.io.File(context.filesDir, DB_NAME)
        return try {
            if (!outFile.exists()) {
                context.assets.open(DB_NAME).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile
        } catch (e: Exception) {
            null // DB not bundled in assets — return null gracefully
        }
    }
}
