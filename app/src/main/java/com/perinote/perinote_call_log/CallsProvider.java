package com.perinote.perinote_call_log;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class CallsProvider extends ContentProvider
{
  public static final String AUTHORITY = "com.perinote.perinote_calls"; // must match manifest decl.
  public static final Uri CONTENT_URI = Uri.parse ("content://" + AUTHORITY + "/calls");

//  private CallsProviderObservable observable = null; // for observers of CallsProvider

  private Handler handlerForObserver = new Handler ();
  private CallLogObserver callLogObserver = null;
  private static Uri observerURI = CallLog.Calls.CONTENT_URI;

  //-----------------------------------------------------------------------------
  @Override
  public boolean onCreate ()
  {
    PerinoteCallLog.logD ("CP onCreate");

    if (PerinoteCallLog.isGranted (getContext (), Manifest.permission.READ_CALL_LOG))
      registerObserver ();

    return true;
  }

  //-----------------------------------------------------------------------------
  private void registerObserver ()
  {
    callLogObserver = new CallLogObserver (handlerForObserver, new CallLogObserver.Listener ()
    {
      @Override public void onChange (Uri uri)
      {
        getContext ().getContentResolver ().notifyChange (CONTENT_URI, null);
      }
    });

    getContext ().getContentResolver ().registerContentObserver (observerURI, true, callLogObserver);
  }

  //-----------------------------------------------------------------------------
  private Uri convertUri (Uri uri)
  {
    String text = uri.toString ();
    text = text.replace (AUTHORITY, CallLog.AUTHORITY);
    return Uri.parse (text);
  }

  //-----------------------------------------------------------------------------
  @Nullable
  @Override
  public Cursor query (@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                       @Nullable String[] selectionArgs, @Nullable String sortOrder)
  {
    PerinoteCallLog.logD ("CP query");
    Context context = getContext ();

    if (PerinoteCallLog.isGranted (context, Manifest.permission.READ_CALL_LOG))
    {
      if (callLogObserver == null)
        registerObserver ();
    }
    else // don't have permission
    {
      if (callLogObserver != null)
        getContext ().getContentResolver ().unregisterContentObserver (callLogObserver);
      callLogObserver = null;

      return null;
    }

    ContentResolver resolver = context.getContentResolver ();
    uri = convertUri (uri);
    Cursor cursor = resolver.query (uri, projection, selection, selectionArgs, sortOrder);
    return cursor;
  }

  //-----------------------------------------------------------------------------
  @Nullable
  @Override
  public String getType (@NonNull Uri uri)
  {
    return null;
  }

  //-----------------------------------------------------------------------------
  @Nullable
  @Override
  public Uri insert (@NonNull Uri uri, @Nullable ContentValues values)
  {
    return null;
  }

  //-----------------------------------------------------------------------------
  @Override
  public int delete (@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs)
  {
    return 0;
  }

  //-----------------------------------------------------------------------------
  @Override
  public int update (@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs)
  {
    PerinoteCallLog.logD ("CP update");
    Context context = getContext ();

    if (!PerinoteCallLog.isGranted (context, Manifest.permission.WRITE_CALL_LOG))
    {
      PerinoteCallLog.logE ("CP update: need write call log permission");
      return 0;
    }

    ContentResolver resolver = context.getContentResolver ();
    uri = convertUri (uri);
    int count = resolver.update (uri, values, selection, selectionArgs);
    PerinoteCallLog.logD ("CP update: count " + count);

    return count;
  }


  //-----------------------------------------------------------------------------
  // CallLogObserver
  //-----------------------------------------------------------------------------


  public static class CallLogObserver extends ContentObserver
  {
    public static Uri URI = CallLog.Calls.CONTENT_URI;
    private Listener listener = null;

    // for onChange()
    private String normalNumber;
//    private ContactData contactData;
    private long placeCallTime;
    boolean lookupKeyPatched = false;

    public interface Listener
    {
      void onChange (Uri uri);
    }

    //-----------------------------------------------------------------------------
    public CallLogObserver (Handler handler, Listener listener)
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
/*
      PerinoteCallLog.logD ("CC onChange: number " + normalNumber);
      if (normalNumber != null) // call placed via Perinote
      {
        // update the call log db first so that "super" will update the call log displays with the
        // new data.
        if (lookupKeyPatched)
          PerinoteCallLog.logD ("CC onChange: calllog lookupkey already patched");
        else
        {
          CallData.writeLookupKeyToMostRecentCall (context, placeCallTime, normalNumber, contactData);
          lookupKeyPatched = true;
        }
      }
*/
      super.onChange (selfChange, uri);
      listener.onChange (uri);
    }
    /*
        //-----------------------------------------------------------------------------
        // Watch for a new call matching "number" and starting after "now". If we detect
        // such a call, ensure that the calllog contains the contact's name and lookupkey.
        // number - number to watch for; null to disable watching.
        public void watchForNewCall (String number, ContactData contactData)
        {
          PerinoteCallLog.logD ("CC watchForNewCall.2");
          if (number != null && this.normalNumber != null)
            PerinoteCallLog.logE ("CC watch for call: already watching");

          PerinoteCallLog.logD ("CC watchForNewCall.3");
          PerinoteCallLog.logD ("CCas watchForNewCall: number " + number);
          this.normalNumber = number == null ? null : CallData.normalizePhoneNumber (number);
          this.placeCallTime = System.currentTimeMillis ();
          this.lookupKeyPatched = false;
          PerinoteCallLog.logD ("CC watchForNewCall.4");
        }
    */
  }
}