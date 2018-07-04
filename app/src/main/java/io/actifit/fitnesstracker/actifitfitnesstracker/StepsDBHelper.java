package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import java.util.ArrayList;
import java.util.Calendar;

public class StepsDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "StepsDatabase";
    private static final String TABLE_STEPS_SUMMARY = "StepsSummary";
    private static final String ID = "id";
    private static final String STEPS_COUNT = "stepscount";
    private static final String CREATION_DATE = "creationdate";//Date format is mm/dd/yyyy


    private static final String CREATE_TABLE_STEPS_SUMMARY = "CREATE TABLE "
            + TABLE_STEPS_SUMMARY + "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + CREATION_DATE + " TEXT,"+ STEPS_COUNT + " INTEGER"+")";


    StepsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int param1, int param2 ){

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_STEPS_SUMMARY);

    }

    public int createStepsEntry()
    {
        boolean isDateAlreadyPresent = false;
        //boolean createSuccessful = false;
        int currentDateStepCounts = 0;
        Calendar mCalendar = Calendar.getInstance();
        String todayDate =
                String.valueOf(mCalendar.get(Calendar.MONTH)+1)+"/" +
                        String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        String selectQuery = "SELECT " + STEPS_COUNT + " FROM "
                + TABLE_STEPS_SUMMARY + " WHERE " + CREATION_DATE +" = '"+ todayDate+"'";
        try {

            SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = db.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    isDateAlreadyPresent = true;
                    currentDateStepCounts =
                            c.getInt((c.getColumnIndex(STEPS_COUNT)));
                    //just need first instance
                    break;
                } while (c.moveToNext());
            }
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CREATION_DATE, todayDate);
            if(isDateAlreadyPresent)
            {
                values.put(STEPS_COUNT, ++currentDateStepCounts);
                //int row =
                db.update(TABLE_STEPS_SUMMARY, values,
                        CREATION_DATE +" = '"+ todayDate+"'", null);
                /*if(row == 1)
                {
                    createSuccessful = true;
                }*/
                db.close();
            }
            else
            {
                currentDateStepCounts = 1;
                values.put(STEPS_COUNT, currentDateStepCounts);
                //long row =
                db.insert(TABLE_STEPS_SUMMARY, null,
                        values);
                /*if(row!=-1)
                {
                    createSuccessful = true;
                }*/
                db.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentDateStepCounts;
    }

    /**
     * function handling grabbing the data and returning it
     * @return ArrayList containing dates and steps
     */
    public ArrayList<DateStepsModel> readStepsEntries()
    {
        ArrayList<DateStepsModel> mStepCountList = new ArrayList<DateStepsModel>();
        //build up the query
        String selectQuery = "SELECT * FROM " + TABLE_STEPS_SUMMARY;
        try {
            //grab all entries
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = db.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    DateStepsModel mDateStepsModel = new DateStepsModel();
                    mDateStepsModel.mDate = c.getString((c.getColumnIndex(CREATION_DATE)));
                    mDateStepsModel.mStepCount = c.getInt((c.getColumnIndex(STEPS_COUNT)));
                    //store the result
                    mStepCountList.add(mDateStepsModel);
                } while (c.moveToNext());
            }
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mStepCountList;
    }

    /**
     * function handles grabbing the current step count saved so far in case it was stored
     * @return today's step count
     */

    public int fetchTodayStepCount()
    {
        int currentDateStepCounts = 0;
        Calendar mCalendar = Calendar.getInstance();
        //build up the query
        String todayDate =
                String.valueOf(mCalendar.get(Calendar.MONTH)+1)+"/" +
                        String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        String selectQuery = "SELECT " + STEPS_COUNT + " FROM "
                + TABLE_STEPS_SUMMARY + " WHERE " + CREATION_DATE +" = '"+ todayDate+"'";
        try {

            SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = db.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    //grab the value returned matching today's date
                    currentDateStepCounts =
                            c.getInt((c.getColumnIndex(STEPS_COUNT)));
                    //only need first result
                    break;
                } while (c.moveToNext());
            }
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentDateStepCounts;
    }

}
