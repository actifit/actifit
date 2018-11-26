package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class StepsDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "ActifitFitness";
    private static final String TABLE_STEPS_SUMMARY = "ActifitFitness";
    private static final String CREATION_DATE = "creationdate";//Date format is yyyyMMdd
    private static final String STEPS_COUNT = "stepscount";
    private static final String TRACKING_DEVICE = "trackingdevice";

    public static final String DEVICE_SENSORS = "Device Sensors";
    public static final String FITBIT = "Fitbit";


    private static final String CREATE_TABLE_ACTIFIT = "CREATE TABLE "
            + TABLE_STEPS_SUMMARY
            + "(" + CREATION_DATE + " INTEGER PRIMARY KEY,"
                + STEPS_COUNT + " INTEGER,"
                + TRACKING_DEVICE + " TEXT"
            +")";


    StepsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int oldVersion, int newVersion ){
        switch(oldVersion) {
            // If the existing version is before v1 (on which we added the new field)
            case 1: db.execSQL("ALTER TABLE "+TABLE_STEPS_SUMMARY
                    +" ADD COLUMN "+TRACKING_DEVICE+" TEXT");
                    break;
            default:
                throw new IllegalStateException(
                        "onUpgrade() with unknown oldVersion " + oldVersion);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_ACTIFIT);

    }

    /**
     * function handles recording a step entry, and returning current step count
     * this is the default function increasing by 1
     * @return
     */
    public int createStepsEntry(){
        return createStepsEntry(1);
    }

    /**
     * function handles recording a step entry, and returning current step count
     * @param incrementVal contains the amount to increase the count
     * @return
     */
    public int createStepsEntry(int incrementVal)
    {
        //grab step count for today, if exists
        int todayStepCount = fetchTodayStepCount();
        String todaysDateString = getTodayProperFormat();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CREATION_DATE, getTodayProperFormat());
            //if we found a match
            if(todayStepCount>-1)
            {
                todayStepCount+=incrementVal;
                values.put(STEPS_COUNT, todayStepCount);
                values.put(TRACKING_DEVICE, DEVICE_SENSORS);
                //updating entry with proper step count
                db.update(TABLE_STEPS_SUMMARY, values,
                        CREATION_DATE + "=" + todaysDateString, null);
                db.close();
            }
            else
            {
                //create entry with 1 step count as first entry
                todayStepCount = (incrementVal>-1?incrementVal:0);
                values.put(STEPS_COUNT, todayStepCount);
                values.put(TRACKING_DEVICE, DEVICE_SENSORS);
                db.insert(TABLE_STEPS_SUMMARY, null,
                        values);
                db.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return todayStepCount;
    }

    /**
     * function handling grabbing the data and returning it
     * @return ArrayList containing dates and steps
     */
    public ArrayList<DateStepsModel> readStepsEntries()
    {
        ArrayList<DateStepsModel> mStepCountList = new ArrayList<DateStepsModel>();
        //build up the query to grab all data
        String selectQuery = "SELECT * FROM " + TABLE_STEPS_SUMMARY;
        try {
            //grab all entries
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = db.rawQuery(selectQuery, null);
            //String priorDate = "";
            if (c.moveToFirst()) {
                do {
                    DateStepsModel mDateStepsModel = new DateStepsModel();
                    mDateStepsModel.mDate = c.getString((c.getColumnIndex(CREATION_DATE)));
                    mDateStepsModel.mStepCount = c.getInt((c.getColumnIndex(STEPS_COUNT)));
                    mDateStepsModel.mtrackingDevice = c.getString((c.getColumnIndex(TRACKING_DEVICE)));
                    //fix for the issue with multiple dates showing as row entries
                    //if (!mDateStepsModel.mDate.equals(priorDate)){
                        //store the result only if this is a different display
                        mStepCountList.add(mDateStepsModel);
                   // }
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
        //tracking found step count. Initiate at -1 to know if entry was found
        int currentDateStepCounts = -1;

        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(todaysDate);

        String selectQuery = "SELECT " + STEPS_COUNT + " FROM "
                + TABLE_STEPS_SUMMARY + " WHERE " + CREATION_DATE +" = "+ todaysDateString + "";
        try {

            SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = db.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    //grab the value returned matching today's date
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
        return currentDateStepCounts;
    }

    public String getTodayProperFormat(){
        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(todaysDate);
        return todaysDateString;
    }

    /**
     * function handles manually storing a step entry, to be used by alternative data
     * tracking services
     * @param activityCount contains the amount of activity to store on current date
     * @return operation was successful (TRUE) or not (FALSE)
     */
    public boolean manualInsertStepsEntry(int activityCount)
    {

        String todaysDateString = getTodayProperFormat();
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CREATION_DATE, getTodayProperFormat());

            values.put(STEPS_COUNT, activityCount);
            values.put(TRACKING_DEVICE, FITBIT);
            //updating entry with proper step count
            int updatedRecords = db.update(TABLE_STEPS_SUMMARY, values,
                        CREATION_DATE + "=" + todaysDateString, null);

            //if no records updated, create a new entry
            if (updatedRecords<1) {
                db.insert(TABLE_STEPS_SUMMARY, null,
                        values);
            }
            db.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
