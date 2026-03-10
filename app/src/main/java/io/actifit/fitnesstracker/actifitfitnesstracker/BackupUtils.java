package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

public class BackupUtils {
    private static final String TAG = "BackupUtils";
    private static final String KEY_DB = "database";
    private static final String KEY_PREFS = "preferences";

    public static boolean createBackup(Context context, Uri uri) {
        try {
            JSONObject backupJson = new JSONObject();

            // 1. Backup SharedPreferences
            JSONObject prefsJson = new JSONObject();
            SharedPreferences prefs = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                prefsJson.put(entry.getKey(), entry.getValue());
            }
            backupJson.put(KEY_PREFS, prefsJson);

            // 2. Backup Database
            JSONObject dbJson = new JSONObject();
            StepsDBHelper dbHelper = new StepsDBHelper(context);
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            dbJson.put(StepsDBHelper.TABLE_STEPS_SUMMARY, tableToJSONArray(db, StepsDBHelper.TABLE_STEPS_SUMMARY));
            dbJson.put(StepsDBHelper.TABLE_STEPS_DETAILS, tableToJSONArray(db, StepsDBHelper.TABLE_STEPS_DETAILS));
            backupJson.put(KEY_DB, dbJson);

            // 3. Write to Uri
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(backupJson.toString().getBytes());
                outputStream.close();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
        }
        return false;
    }

    private static JSONArray tableToJSONArray(SQLiteDatabase db, String tableName) {
        JSONArray tableArray = new JSONArray();
        Cursor cursor = db.rawQuery("SELECT * FROM " + tableName, null);
        if (cursor.moveToFirst()) {
            do {
                JSONObject row = new JSONObject();
                int columnCount = cursor.getColumnCount();
                for (int i = 0; i < columnCount; i++) {
                    String columnName = cursor.getColumnName(i);
                    try {
                        switch (cursor.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                row.put(columnName, cursor.getLong(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                row.put(columnName, cursor.getDouble(i));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                row.put(columnName, cursor.getString(i));
                                break;
                            case Cursor.FIELD_TYPE_NULL:
                                row.put(columnName, JSONObject.NULL);
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing column " + columnName, e);
                    }
                }
                tableArray.put(row);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tableArray;
    }

    public static boolean restoreBackup(Context context, Uri uri) {
        try {
            // 1. Read from Uri
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            inputStream.close();

            JSONObject backupJson = new JSONObject(sb.toString());

            // 2. Restore SharedPreferences
            if (backupJson.has(KEY_PREFS)) {
                JSONObject prefsJson = backupJson.getJSONObject(KEY_PREFS);
                SharedPreferences prefs = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                JSONArray keys = prefsJson.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        Object value = prefsJson.get(key);
                        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                        else if (value instanceof Long) editor.putLong(key, (Long) value);
                        else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
                        else if (value instanceof String) editor.putString(key, (String) value);
                    }
                }
                editor.apply();
            }

            // 3. Restore Database
            if (backupJson.has(KEY_DB)) {
                JSONObject dbJson = backupJson.getJSONObject(KEY_DB);
                StepsDBHelper dbHelper = new StepsDBHelper(context);
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                db.beginTransaction();
                try {
                    db.delete(StepsDBHelper.TABLE_STEPS_SUMMARY, null, null);
                    JSONArray summaryArray = dbJson.getJSONArray(StepsDBHelper.TABLE_STEPS_SUMMARY);
                    for (int i = 0; i < summaryArray.length(); i++) {
                        JSONObject row = summaryArray.getJSONObject(i);
                        db.insert(StepsDBHelper.TABLE_STEPS_SUMMARY, null, jsonToContentValues(row));
                    }

                    db.delete(StepsDBHelper.TABLE_STEPS_DETAILS, null, null);
                    JSONArray detailsArray = dbJson.getJSONArray(StepsDBHelper.TABLE_STEPS_DETAILS);
                    for (int i = 0; i < detailsArray.length(); i++) {
                        JSONObject row = detailsArray.getJSONObject(i);
                        db.insert(StepsDBHelper.TABLE_STEPS_DETAILS, null, jsonToContentValues(row));
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Restore failed", e);
        }
        return false;
    }

    private static ContentValues jsonToContentValues(JSONObject json) {
        ContentValues values = new ContentValues();
        JSONArray keys = json.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                try {
                    String key = keys.getString(i);
                    Object value = json.get(key);
                    if (value == JSONObject.NULL) values.putNull(key);
                    else if (value instanceof Boolean) values.put(key, (Boolean) value);
                    else if (value instanceof Integer) values.put(key, (Integer) value);
                    else if (value instanceof Long) values.put(key, (Long) value);
                    else if (value instanceof Double) values.put(key, (Double) value);
                    else if (value instanceof String) values.put(key, (String) value);
                } catch (Exception e) {
                    // Skip
                }
            }
        }
        return values;
    }
}
