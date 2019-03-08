
package com.perinote.perinote_call_log;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public class PerinoteCallLog extends Activity
{
  // for preparing db queries
  public static final char QUERY_ESCAPE_CHAR = '\b';

  public enum ObserverType {CALLS, CONTACTS};
  public interface Listener
  {
    void onChange (Context context, ObserverType type);
  }

  ViewGroup enableGroup;
  ListView listView;

  // observer for CallsProvider
  private Handler handlerForObserver = new Handler ();
  private CallsProviderObserver callsObserver = null;

  //-----------------------------------------------------------------------------
  // Do not change these values - they must correspond to values in the DB.
  public static enum DateFormatType
  {
    DATE_FORMAT_NONE (0),         // don't show date
    DATE_FORMAT_DD_mon_YYYY (1),  // DD-mon-YYYY
    DATE_FORMAT_DDmonYY (2),      // DDmonYY
    DATE_FORMAT_mon_DD_YYYY (3),  // mon DD, YYYY
    DATE_FORAMT_MM_DD_YY (4);     // MM/DD/YY

    int val;
    private DateFormatType (int val) {this.val = val;}
    public int get() {return val;}
    public static DateFormatType find (int val)
    {
      if (val == 0) return DATE_FORMAT_NONE;
      if (val == 1) return DATE_FORMAT_DD_mon_YYYY;
      if (val == 2) return DATE_FORMAT_DDmonYY;
      if (val == 3) return DATE_FORMAT_mon_DD_YYYY;
      if (val == 4) return DATE_FORAMT_MM_DD_YY;
      throw new IllegalArgumentException ("unrecognized DateFormatType val: " + val);
    }
  };

  //-----------------------------------------------------------------------------
  // Do not change these values - they must correspond to values in the DB.
  public enum TimeFormatType
  {
    TIME_FORMAT_HH_MM (1),     // HH:MM 24 hour time
    TIME_FORMAT_HH_MM_AP (2);  // HH:MMam or pm

    int val;
    private TimeFormatType (int val) {this.val = val;}
    public int get() {return val;}
    public static TimeFormatType find (int val)
    {
      if (val == 1) return TIME_FORMAT_HH_MM;
      if (val == 2) return TIME_FORMAT_HH_MM_AP;
      throw new IllegalArgumentException ("unrecognized TimeFormatType val: " + val);
    }
  };

  private static String typeUnknown = "Unknown";

  // ordering must match phoneTypeIds
  private static String[] phoneTypes = {
    "Mobile",
    "Work",
    "Home",
    "Main",
    "Fax Work",
    "Fax Home",
    "Pager",
    "Other",
    "Custom",

    "Callback",
    "Car",
    "Company Main",
    "ISDN",
    "Other Fax",
    "Radio",
    "Telex",
    "TTY TTD",
    "Work Mobile",
    "Work Pager",
    "Assistant",
    "MMS"
  };

  // ordering must match phoneTypes, phoneTypesShortList
  private static int[] phoneTypeIds = {
    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
    ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
    ContactsContract.CommonDataKinds.Phone.TYPE_PAGER,
    ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,

    ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK,
    ContactsContract.CommonDataKinds.Phone.TYPE_CAR,
    ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN,
    ContactsContract.CommonDataKinds.Phone.TYPE_ISDN,
    ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX,
    ContactsContract.CommonDataKinds.Phone.TYPE_RADIO,
    ContactsContract.CommonDataKinds.Phone.TYPE_TELEX,
    ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD,
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE,
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER,
    ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT,
    ContactsContract.CommonDataKinds.Phone.TYPE_MMS,
  };


  //------------------------------------------------------------------------------
  // METHODS
  //------------------------------------------------------------------------------


  @Override
  protected void onCreate (Bundle savedInstanceState)
  {
    logD ("PPh onCreate");
    super.onCreate (savedInstanceState);
    setContentView (R.layout.perinote_call_log);

    enableGroup = findViewById (R.id.enable_group);
    listView = findViewById (R.id.list);

    if (!isGranted (this, Manifest.permission.READ_CALL_LOG) ||
        !isGranted (this, Manifest.permission.WRITE_CALL_LOG))
    {
      listView.setVisibility (View.GONE);
      enableGroup.setVisibility (View.VISIBLE);

      Button enable = enableGroup.findViewById (R.id.enable);
      enable.setOnClickListener (new View.OnClickListener ()
      {
        @Override public void onClick (View v)
        {
          logE ("PPh refresh: need permissions");
          ActivityCompat.requestPermissions (PerinoteCallLog.this,
                                             new String[] {
                                               Manifest.permission.READ_CALL_LOG,
                                               Manifest.permission.WRITE_CALL_LOG},
                                             1);
        }
      });

      return;
    }

    init ();
  }

  //------------------------------------------------------------------------------
  private void init ()
  {
    enableGroup.setVisibility (View.GONE);
    listView.setVisibility (View.VISIBLE);

    // observers
    callsObserver = new CallsProviderObserver (handlerForObserver, new CallsProviderObserver.Listener ()
    {
      @Override public void onChange () { refresh (); }
    });
    getContentResolver ().registerContentObserver (CallsProvider.CONTENT_URI, true, callsObserver);

    new PerinoteCallLog.CallsProviderObserver.Listener ()
    {
      @Override public void onChange ()
      {
        logD ("CaLF onChange");
        refresh ();
      }
    };

    listView = findViewById (R.id.list);
    listView.setAdapter (null);

    refresh ();
  }

  //------------------------------------------------------------------------------
  @Override
  protected void onDestroy ()
  {
    logD ("PPh onDestroy");
    super.onDestroy ();
    getContentResolver ().unregisterContentObserver (callsObserver);
  }

  //------------------------------------------------------------------------------
  protected void onStart ()
  {
    logD ("PPh onStart");
    super.onStart ();
  }

  //------------------------------------------------------------------------------
  @Override
  protected void onStop()
  {
    logD ("PPh onStop");
    super.onStop();
  }

  //-----------------------------------------------------------------------------
  // from requestPermissions
  @Override
  public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults)
  {
    logD ("PPh onRequestPermissionsResult: " + permissions + ", results " + grantResults);
    // switch to UI thread
    runOnUiThread (new Runnable ()
    {
      @Override public void run () { init (); }
    });
  }

  public static final int TAB_DIALER = 0; // tab index for dialer

  //-----------------------------------------------------------------------------
  public static boolean isGranted (Context context, String permission)
  {
    if (Build.VERSION.SDK_INT < 23)
      return true;
    int level = ActivityCompat.checkSelfPermission (context, permission);
    return level == PackageManager.PERMISSION_GRANTED;
  }

  //-----------------------------------------------------------------------------
  public static String escapeString (String s, char escapeChar)
  {
    String se = s.replace ("'", "''");
    se = se.replace ("%", escapeChar + "%");
    se = se.replace ("_", escapeChar + "_");
    return se;
  }

  //------------------------------------------------------------------------------
  // NB these log messages will not be written in release version.
  public static void logD (String s)
  {
    Log.d ("PerinoteCallLog", s);
  }

  //------------------------------------------------------------------------------
  // will write to log if VERBOSE is set
  public static void logI (String s)
  {
    Log.i ("PerinoteCallLog", s);
  }

  //------------------------------------------------------------------------------
  public static void logE (String s)
  {
    Log.e ("PerinoteCallLog", s);
  }

  //------------------------------------------------------------------------------
  private void refresh ()
  {
    logD ("CaLF refresh");
    if (listView == null)
      return;

    if (!isGranted (this, Manifest.permission.READ_CALL_LOG) ||
        !isGranted (this, Manifest.permission.WRITE_CALL_LOG))
    {
      logE ("CaLF refresh: need permission");
      listView.setAdapter (null);
      return;
    }

    CallsAdapter adapter = (CallsAdapter) listView.getAdapter ();
    if (adapter != null)
      adapter.cursor.close ();

    ContentResolver resolver = getContentResolver();
    Uri uri = CallsProvider.CONTENT_URI;

    String where = "";
    String order = CallLog.Calls.DATE + " DESC";

    Cursor cursor = resolver.query (uri, null, where, null, order);

    // set list adapter
    if (cursor == null)
      listView.setAdapter (null);
    else
    {
      adapter = new CallsAdapter (cursor);
      listView.setAdapter (adapter);
    }
  }

  //-----------------------------------------------------------------------------
  public static String getPhoneTypeFromId (int id, String label)
  {
    if (id == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM)
      return label;
    else
    {
      int index = findPhoneTypeIdIndex (id);
      return index == -1 ? typeUnknown : phoneTypes [index];
    }
  }

  //-----------------------------------------------------------------------------
  // returns index of type, -1 if not found.
  public static int findPhoneTypeIdIndex (int type)
  {
    for (int i = 0;  i < phoneTypeIds.length;  i++)
      if (type == phoneTypeIds[i])
        return i;
    return -1;
  }


  //------------------------------------------------------------------------------
  // CallData
  //------------------------------------------------------------------------------


  private class CallData
  {
    String description;
    String number;
    int numberType;
    String numberLabel;
    int callType;
    long date;
    int duration;
  }


  //------------------------------------------------------------------------------
  // CallsAdapter
  //------------------------------------------------------------------------------


  private class CallsAdapter implements ListAdapter
  {
    Cursor cursor;

    //------------------------------------------------------------------------------
    public CallsAdapter (Cursor cursor)
    {
      this.cursor = cursor;
    }

    //------------------------------------------------------------------------------
    @Override
    public void registerDataSetObserver (DataSetObserver observer)
    {

    }

    //------------------------------------------------------------------------------
    @Override
    public void unregisterDataSetObserver (DataSetObserver observer)
    {

    }

    //------------------------------------------------------------------------------
    @Override
    public int getCount ()
    {
      return cursor.getCount ();
    }

    //------------------------------------------------------------------------------
    @Override
    public Object getItem (int position)
    {
      logD ("CaLF getItem");

      // get data
      cursor.moveToPosition (position);

      String description = cursor.getString (cursor.getColumnIndex (CallLog.Calls.CACHED_NAME));
      String number = cursor.getString (cursor.getColumnIndex (CallLog.Calls.NUMBER));
      int numberType = cursor.getInt (cursor.getColumnIndex (CallLog.Calls.CACHED_NUMBER_TYPE));
      String numberLabel = cursor.getString (cursor.getColumnIndex (CallLog.Calls.CACHED_NUMBER_LABEL));
      int callType = cursor.getInt (cursor.getColumnIndex (CallLog.Calls.TYPE));
      long date = cursor.getLong (cursor.getColumnIndex (CallLog.Calls.DATE));
      int duration = cursor.getInt (cursor.getColumnIndex (CallLog.Calls.DURATION));
/*
      String lookupKey = bundle.getString (RESULT_LOOKUP_KEY, getLookupKeyFromUri (callUri));
      bundle.putString (RESULT_DESCRIPTION, name);
      bundle.putString (RESULT_NUMBER, number);
      bundle.putLong (RESULT_TIME, time);
      bundle.putInt (RESULT_CALL_TYPE, callType);
      bundle.putInt (RESULT_NUMBER_TYPE, numberType);
      bundle.putString (RESULT_LABEL, label);
      bundle.putInt (RESULT_DURATION, duration);
*/

      CallData data = new CallData ();
      data.description = description;
      data.number = number;
      data.numberType = numberType;
      data.numberLabel = numberLabel;
      data.callType = callType;
      data.date = date;
      data.duration = duration;

      return data;
    }

    //------------------------------------------------------------------------------
    @Override
    public long getItemId (int position)
    {
      return 0;
    }

    //------------------------------------------------------------------------------
    @Override
    public boolean hasStableIds ()
    {
      return false;
    }

    //------------------------------------------------------------------------------
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
      Context context = parent.getContext ();

      // create empty row
      if (convertView == null)
      {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService (Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate (R.layout.call_row, parent, false);
      }

      // fetch data, asynch
      CallData data = (CallData) getItem (position);

      // fill fields
      TextView descrView = convertView.findViewById (R.id.description);
      TextView dateView = convertView.findViewById (R.id.date);
      TextView timeView = convertView.findViewById (R.id.time);
      TextView numberTypeView = convertView.findViewById (R.id.number_type);
      TextView numberView = convertView.findViewById (R.id.number);

      descrView.setText (data.description);

      if (data.description == null || data.description.isEmpty ()) // "unknown", show number instead
      {
        descrView.setText (data.number);
        numberTypeView.setVisibility (View.GONE);
        numberView.setVisibility (View.GONE);
      }
      else // show number on it's own line
      {
        numberView.setVisibility (View.VISIBLE);
        numberView.setText (data.number);

        numberTypeView.setVisibility (View.VISIBLE);
        numberTypeView.setText (getPhoneTypeFromId (data.numberType, data.numberLabel));
      }

      String dateFormatString = getDateFormatString (true);
      SimpleDateFormat dateFormatter = new SimpleDateFormat (dateFormatString);

      String timeFormatString = getTimeFormatString ();
      SimpleDateFormat timeFormatter = new SimpleDateFormat (timeFormatString);

      GregorianCalendar gdate = new GregorianCalendar();
      gdate.setTimeInMillis (data.date);

      dateView.setText (dateFormatter.format (gdate.getTimeInMillis()));
      timeView.setText (timeFormatter.format (gdate.getTimeInMillis()));

      // on click
/*
      convertView.setTag (data);
      convertView.setOnClickListener (new View.OnClickListener ()
      {
        @Override public void onClick (View v)
        {
          CallData selectedData = (CallData) v.getTag ();
          ((PerinoteCallLog)getActivity ()).showDialer (selectedData.number);
        }
      });
*/
      return convertView;
    }

    //------------------------------------------------------------------------------
    @Override
    public int getItemViewType (int position)
    {
      return 0;
    }

    //------------------------------------------------------------------------------
    @Override
    public int getViewTypeCount ()
    {
      return 1;
    }

    //------------------------------------------------------------------------------
    @Override
    public boolean isEmpty ()
    {
      return cursor.getCount () > 0;
    }

    //------------------------------------------------------------------------------
    @Override
    public boolean areAllItemsEnabled ()
    {
      return true;
    }

    //------------------------------------------------------------------------------
    @Override
    public boolean isEnabled (int position)
    {
      return true;
    }

    //------------------------------------------------------------------------------
    // withDay - true if day of week needed with date
    // returns a SimpleDateFormat string.
    public String getDateFormatString (boolean withDay)
    {
      return getDateFormatString (DateFormatType.DATE_FORAMT_MM_DD_YY, withDay);
    }

    //------------------------------------------------------------------------------
    // withDay - true if day of week needed with date
    // returns a SimpleDateFormat string.
    public String getDateFormatString (DateFormatType format, boolean withDay)
    {
      String s = "";
      if (withDay)
        s = "E, ";

      switch (format)
      {
        case DATE_FORMAT_DD_mon_YYYY: return s + "d MMM yyyy";
        case DATE_FORMAT_DDmonYY:     return s + "dMMMyy";
        case DATE_FORMAT_mon_DD_YYYY: return s + "MMM d, yyyy";
        case DATE_FORAMT_MM_DD_YY:    return s + "M/d/yy";
        default:  break;
      }
      throw new IllegalStateException ("unrecognized date format: " + format);
    }

    //------------------------------------------------------------------------------
    public String getTimeFormatString ()
    {
      return getTimeFormatString (TimeFormatType.TIME_FORMAT_HH_MM_AP);
    }

    //------------------------------------------------------------------------------
    public String getTimeFormatString (TimeFormatType format)
    {
      switch (format)
      {
        case TIME_FORMAT_HH_MM:    return "HH:mm";
        case TIME_FORMAT_HH_MM_AP: return "h:mma";
      }
      throw new IllegalStateException ("unrecognized time format: " + format);
    }

  }


  //-----------------------------------------------------------------------------
  // CallsProviderObserver
  //-----------------------------------------------------------------------------


  public static class CallsProviderObserver extends ContentObserver
  {
    Listener listener = null;

    public interface Listener
    {
      void onChange ();
    }

    //-----------------------------------------------------------------------------
    public CallsProviderObserver (Handler handler, Listener listener)
    {
      super (handler);
      PerinoteCallLog.logD ("CLO constructor");
      this.listener = listener;
    }

    //-----------------------------------------------------------------------------
    @Override
    public void onChange (boolean selfChange, Uri uri)
    {
      PerinoteCallLog.logD ("CLO onChange: " + uri);
      super.onChange (selfChange, uri);
      listener.onChange ();;
    }
  }
}
