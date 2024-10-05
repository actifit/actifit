package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static java.lang.String.format;

public class StepsDBHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 3;
    private static final String DATABASE_NAME = "ActifitFitness";
    private static final String TABLE_STEPS_SUMMARY = "ActifitFitness";
    private static final String CREATION_DATE = "creationdate";//Date format is yyyyMMdd
    private static final String STEPS_COUNT = "stepscount";
    private static final String TRACKING_DEVICE = "trackingdevice";

    private static final String TABLE_STEPS_DETAILS = "DailyActivityRecs";
    private static final String DATE_ENTRY = "dateEntry";//Date format is yyyyMMdd
    private static final String TIME_SLOT = "timeSlot";
    private static final String ACTIVITY_COUNT = "activityCount";
    public static final int MAX_SLOTS_PER_DAY = 100;

    public static final String DEVICE_SENSORS = "Device Sensors";
    public static final String FITBIT = "Fitbit";

    private static SQLiteDatabase dbInstance;

    private Context ctx;
    private SharedPreferences sharedPreferences;


    private static final String CREATE_TABLE_ACTIFIT = "CREATE TABLE "
            + TABLE_STEPS_SUMMARY
            + "(" + CREATION_DATE + " INTEGER PRIMARY KEY,"
            + STEPS_COUNT + " INTEGER,"
            + TRACKING_DEVICE + " TEXT"
            +")";

    private static final String CREATE_TABLE_ACTIVITY_DETAILS = "CREATE TABLE " +
            TABLE_STEPS_DETAILS +
            "(" + DATE_ENTRY + " INTEGER, " +
            TIME_SLOT + " INTEGER, " +
            ACTIVITY_COUNT+" INTEGER )";


    StepsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        dbInstance = this.getWritableDatabase();
        ctx = context;
        sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int oldVersion, int newVersion ){
        switch(oldVersion) {

            // If the existing version is before v1 (on which we added the new field)
            case 2: db.execSQL(CREATE_TABLE_ACTIVITY_DETAILS);
                break;

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
        db.execSQL(CREATE_TABLE_ACTIVITY_DETAILS);
    }

    public int recordDetailedSteps(int incrementVal) {

        String todaysDateString = getTodayProperFormat();
        //grab current matching timeslot
        String curTimeSlot = getTimeSlot();

        //grab step count for today, if exists
        int timeSlotStepCount = fetchTimeSlotStepCount(curTimeSlot);
        try {
            //SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(DATE_ENTRY, todaysDateString);
            values.put(TIME_SLOT, curTimeSlot);
            //if we found a match
            if(timeSlotStepCount>-1)
            {
                timeSlotStepCount += incrementVal;
                values.put(ACTIVITY_COUNT, timeSlotStepCount);
                //updating entry with proper step count
                dbInstance.update(TABLE_STEPS_DETAILS, values,
                        DATE_ENTRY + "=" + todaysDateString + " AND "
                                + TIME_SLOT + "=" + curTimeSlot , null);
                //db.close();
            }
            else
            {
                //create entry with 1 step count as first entry
                timeSlotStepCount = (incrementVal>-1?incrementVal:0);
                values.put(ACTIVITY_COUNT, timeSlotStepCount);
                dbInstance.insert(TABLE_STEPS_DETAILS, null,
                        values);
                //db.close();
            }

        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return timeSlotStepCount;
    }

    /**
     * function handles recording a step entry, and returning current step count
     * this is the default function increasing by 1
     * @return integer stepCount
     */
    public int createStepsEntry(){
        return createStepsEntry(1);
    }

    /**
     * function handles recording a step entry, and returning current step count
     * @param incrementVal contains the amount to increase the count
     * @return integer stepCount
     */
    public int createStepsEntry(int incrementVal)
    {
        //grab step count for today, if exists
        int todayStepCount = fetchTodayStepCount();
        String todaysDateString = getTodayProperFormat();
        try {
            //SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CREATION_DATE, getTodayProperFormat());
            //if we found a match
            if(todayStepCount>-1)
            {
                todayStepCount+=incrementVal;
                values.put(STEPS_COUNT, todayStepCount);
                values.put(TRACKING_DEVICE, DEVICE_SENSORS);
                //updating entry with proper step count
                dbInstance.update(TABLE_STEPS_SUMMARY, values,
                        CREATION_DATE + "=" + todaysDateString, null);
                //db.close();
            }
            else
            {
                //create entry with 1 step count as first entry
                todayStepCount = (incrementVal>-1?incrementVal:0);
                values.put(STEPS_COUNT, todayStepCount);
                values.put(TRACKING_DEVICE, DEVICE_SENSORS);
                dbInstance.insert(TABLE_STEPS_SUMMARY, null,
                        values);
                //db.close();
            }

        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        //also store this in the detailed log

        recordDetailedSteps(incrementVal);
        return todayStepCount;
    }

    /**
     * function handling grabbing the data and returning it
     * @return ArrayList containing dates and steps
     */
    @SuppressLint("Range")
    public ArrayList<DateStepsModel> readStepsEntries()
    {
        ArrayList<DateStepsModel> mStepCountList = new ArrayList<DateStepsModel>();
        //build up the query to grab all data
        String selectQuery = "SELECT * FROM " + TABLE_STEPS_SUMMARY;
        try {
            //grab all entries
            //SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = dbInstance.rawQuery(selectQuery, null);
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
            c.close();
            //db.close();
        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return mStepCountList;
    }



    /**
     * function handles grabbing the detailed activity count by each time slot
     * @return target date's activity count by timeslot
     */
    @SuppressLint("Range")
    public ArrayList<ActivitySlot> fetchDateTimeSlotActivity(String targetDateString)
    {
        //array containing all matching activity slots
        ArrayList<ActivitySlot> activitySlots = new ArrayList<ActivitySlot>();

        //generate format for target date
        /*SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(targetDate);*/

        //ensure we are using proper numeric format for dates
        targetDateString = format(Locale.ENGLISH, "%d", Integer.parseInt(targetDateString));

        String selectQuery = "SELECT * FROM "
                + TABLE_STEPS_DETAILS + " WHERE " + DATE_ENTRY +" = "+ targetDateString;
        try {

            //SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = dbInstance.rawQuery(selectQuery, null);
            int i = 0;
            if (c.moveToFirst()) {
                do {
                    //grab the value returned matching target date

                    //format slot value properly (HHmm) with leading zeros for night time
                    //and according to standard EN numeric format to avoid language inconsistencies
                    String timeSlot = format(Locale.ENGLISH,"%04d", c.getInt((c.getColumnIndex(TIME_SLOT))));

                    ActivitySlot curActivitySlot = new ActivitySlot(timeSlot,
                            c.getInt((c.getColumnIndex(ACTIVITY_COUNT))));

                    activitySlots.add(curActivitySlot);

                } while (c.moveToNext());
            }
            c.close();
            //db.close();
        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return activitySlots;

    }

    /**
     * function handles grabbing the current step count saved so far in each time slot in case it was stored
     * @return current timeslot's step count
     */
    @SuppressLint("Range")
    public int fetchTimeSlotStepCount(String timeSlot)
    {
        //tracking found step count. Initiate at -1 to know if entry was found
        int currentSlotStepCounts = -1;

        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(todaysDate);

        //ensure we are using proper numeric format for dates
        todaysDateString = format(Locale.ENGLISH, "%d", Integer.parseInt(todaysDateString));

        String selectQuery = "SELECT " + ACTIVITY_COUNT + " FROM "
                + TABLE_STEPS_DETAILS + " WHERE " + DATE_ENTRY +" = "+ todaysDateString + " AND "
                + TIME_SLOT + "=" + timeSlot;
        try {

            //SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = dbInstance.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    //grab the value returned matching today's date
                    currentSlotStepCounts =
                            c.getInt((c.getColumnIndex(ACTIVITY_COUNT)));

                    //just need first instance
                    break;
                } while (c.moveToNext());
            }
            c.close();
            //db.close();
        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return currentSlotStepCounts;

    }

    /**
     * function handles grabbing the current step count saved so far in case it was stored
     * @return today's step count
     */
    @SuppressLint("Range")
    public int fetchStepCountByDate(String dateString)
    {
        //ensure we are using proper numeric format for dates
        dateString = format(Locale.ENGLISH, "%d", Integer.parseInt(dateString));
        //tracking found step count. Initiate at -1 to know if entry was found
        int currentDateStepCounts = -1;

        String selectQuery = "SELECT " + STEPS_COUNT + " FROM "
                + TABLE_STEPS_SUMMARY + " WHERE " + CREATION_DATE +" = "+ dateString + "";
        try {

            //SQLiteDatabase db = this.getReadableDatabase();
            Cursor c = dbInstance.rawQuery(selectQuery, null);
            if (c.moveToFirst()) {
                do {
                    //grab the value returned matching today's date
                    currentDateStepCounts =
                            c.getInt((c.getColumnIndex(STEPS_COUNT)));

                    //just need first instance
                    break;
                } while (c.moveToNext());
            }
            c.close();
            //db.close();
        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return currentDateStepCounts;
    }

    public int fetchYesterdayStepCount()
    {
        //generate format for today
        //Date todaysDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(cal.getTime());

        return fetchStepCountByDate(todaysDateString);
    }

    /**
     * function handles grabbing the current step count saved so far in case it was stored
     * @return today's step count
     */

    public int fetchTodayStepCount()
    {
        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(todaysDate);
        //if this is not the normal tracking mode, return fitbit synced data
        if(!sharedPreferences.getString("dataTrackingSystem",
                ctx.getString(R.string.device_tracking_ntt))
                .equals(ctx.getString(R.string.device_tracking_ntt))) {
            String lastMainSyncDate = sharedPreferences.getString("fitbitLastSyncDate","");
            //TODO make sure date comparison is accurate
            if (todaysDateString.equals(lastMainSyncDate)) {
                return sharedPreferences.getInt("fitbitSyncCount", 0);
            }else{
                //default value
                return 0;
            }
        }
        return fetchStepCountByDate(todaysDateString);
    }

    public String getTodayProperFormat(){
        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat formatToDB = new SimpleDateFormat("yyyyMMdd");
        String todaysDateString = formatToDB.format(todaysDate);
        //fix for EN format for proper DB querying in case of other languages
        todaysDateString = format(Locale.ENGLISH, "%d", Integer.parseInt(todaysDateString));
        return todaysDateString;
    }

    public String getTimeSlot(){
        //generate format for today
        Date todaysDate = new Date();
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
        SimpleDateFormat minFormat = new SimpleDateFormat("mm");
        String hourStr = hourFormat.format(todaysDate);
        //fix to proper EN format avoiding inconsistencies across languages
        hourStr = format(Locale.ENGLISH, "%d", Integer.parseInt(hourStr));

        String minStr = minFormat.format(todaysDate);

        //fix to proper EN format avoiding inconsistencies across languages
        minStr = format(Locale.ENGLISH, "%d", Integer.parseInt(minStr));

        int minValue = Integer.parseInt(minStr);
        if (minValue >=45) {
            minStr = "45";
        }else if (minValue >=30) {
            minStr = "30";
        }else if (minValue >=15) {
            minStr = "15";
        }else{
            minStr = "00";
        }
        return hourStr + minStr;
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
            //SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CREATION_DATE, getTodayProperFormat());

            values.put(STEPS_COUNT, activityCount);
            values.put(TRACKING_DEVICE, FITBIT);
            //updating entry with proper step count
            int updatedRecords = dbInstance.update(TABLE_STEPS_SUMMARY, values,
                    CREATION_DATE + "=" + todaysDateString, null);

            //if no records updated, create a new entry
            if (updatedRecords<1) {
                dbInstance.insert(TABLE_STEPS_SUMMARY, null,
                        values);
            }
            //db.close();
            return true;
        } catch (Exception e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return false;
    }

    public boolean isConnected(){
        if (dbInstance==null) {
            return false;
        }
        return dbInstance.isOpen();
    }

    public void reConnect(){
        if (dbInstance==null || !dbInstance.isOpen()) {
            dbInstance = this.getWritableDatabase();
        }
    }

    public void closeConnection(){
        if (dbInstance.isOpen()){
            dbInstance.close();
        }
    }

}