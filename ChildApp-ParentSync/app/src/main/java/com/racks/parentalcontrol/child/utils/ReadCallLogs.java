package com.racks.parentalcontrol.child.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import com.racks.parentalcontrol.child.remote.FirebaseClient;

import java.util.HashMap;
import java.util.Map;

public class ReadCallLogs {
    private final Context context;
    private final FirebaseClient firebaseClient;
    private final MySharedPreferences mySharedPreferences;

    public ReadCallLogs(Context context) {
        this.context = context;
        this.firebaseClient = new FirebaseClient();
        this.mySharedPreferences = new MySharedPreferences(context);
    }
    public String getContactName(String phoneNumber) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );

        String name = phoneNumber;
        Cursor cursor = contentResolver.query(
                uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null,
                null,
                null
        );

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                name = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                );
            }
            cursor.close();
        }

        return name;
    }

    public void readAndUploadCallLogs() {
        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = { String.valueOf(mySharedPreferences.getLastUploadTime()) };

        Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        );
        if (cursor != null) {
            Log.d("RaviKumar-ReadCallLogs", "cursor is not null");
            long latestTimestamp = mySharedPreferences.getLastUploadTime();

            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String contactName = getContactName(number);
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));

                String typeStr;
                switch (type) {
                    case CallLog.Calls.INCOMING_TYPE:
                        typeStr = "Incoming";
                        break;
                    case CallLog.Calls.OUTGOING_TYPE:
                        typeStr = "Outgoing";
                        break;
                    case CallLog.Calls.MISSED_TYPE:
                        typeStr = "Missed";
                        break;
                    case CallLog.Calls.REJECTED_TYPE:
                        typeStr = "Rejected";
                        break;
                    case CallLog.Calls.BLOCKED_TYPE:
                        typeStr = "Blocked";
                        break;
                    default:
                        typeStr = "Unknown";
                }

                Map<String, Object> call = new HashMap<>();
                call.put("name", contactName);
                call.put("number", number);
                call.put("type", typeStr);
                call.put("date", date);
                call.put("duration", duration);

                firebaseClient.updateCallLogs(call);
                Log.d("RaviKumar-ReadCallLogs", "uploading data to firebase");
                if (date > latestTimestamp) {
                    latestTimestamp = date;
                }
            }

            cursor.close();

            if (latestTimestamp > mySharedPreferences.getLastUploadTime()) {
                mySharedPreferences.setLastUploadTime(latestTimestamp);
            }
            Log.d("RaviKumar-ReadCallLogs", "reseting trigger");
        }else {
            Log.d("RaviKumar-ReadCallLogs", "cursor is null");
        }
    }



}
