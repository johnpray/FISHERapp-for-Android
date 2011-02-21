package com.sjfc.fisherapp;

import java.net.URL;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.ContentValues;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.sjfc.fisherapp.FisherappDatabase.directoryPeople;

public class DirectoryDetailsActivity extends DirectoryActivity {
	
	public final Handler mHandler = new Handler();
	private SimpleCursorAdapter adapter;
	boolean syncing = false;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.directory_list);

        /** Set yellow bar title and status text */
        TextView txtTitle = (TextView)findViewById(R.id.txtTitle);
        txtTitle.setText(R.string.directory);
        TextView txtUpdateStatus = (TextView)findViewById(R.id.txtUpdateStatus);
        txtUpdateStatus.setText(R.string.waiting_to_sync);

    	/** Listen for logo push */
        ImageView fisherappLogo = (ImageView) findViewById(R.id.imgFISHERappLogo);
        fisherappLogo.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View v) {
        		// Refresh!
        		startXMLParseThread();
        	}
        });
        
        fillPeopleListView();
        
        startXMLParseThread();
        
    }
    
    private void fillPeopleListView() {
        // Populate the ListView
    	SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
    	queryBuilder.setTables(
    		directoryPeople.PEOPLE_TABLE
    	);
    	
    	String asColumnsToReturn[] = { 
    			directoryPeople.PEOPLE_TABLE + "."
    			+ directoryPeople.LAST_NAME + "," +
    			directoryPeople.PEOPLE_TABLE + "."
    			+ directoryPeople.FIRST_NAME + "," +
    			directoryPeople.PEOPLE_TABLE + "."
    			+ directoryPeople.MIDDLE_NAME + "," +
    			directoryPeople.PEOPLE_TABLE + "."
    			+ directoryPeople.JOB_TITLE + "," +
    			directoryPeople.PEOPLE_TABLE + "."
    			+ directoryPeople._ID
    	};
    	
    	mCursor = queryBuilder.query(mDB, asColumnsToReturn, null, null,
    			null, null, directoryPeople.DEFAULT_SORT_ORDER);
    	
    	updateFirstSyncMessage();
    	
    	startManagingCursor(mCursor);
    	
    	adapter = new SimpleCursorAdapter(this,
    			R.layout.directory_people_item, mCursor,
    			new String[]{
    				directoryPeople.LAST_NAME,
    				directoryPeople.FIRST_NAME,
    				directoryPeople.MIDDLE_NAME,
    				directoryPeople.JOB_TITLE},
    			new int[]{
    				R.id.txtLastName,
    				R.id.txtFirstName,
    				R.id.txtMiddle,
    				R.id.txtTitle} 
    	); 
    	
    	ListView av = (ListView)findViewById(R.id.listPeople);
    	av.setAdapter(adapter);
    }
    
    private void startXMLParseThread() {
    	
    	
    	
    	if (!syncing) {
    		TextView txtUpdateStatus = (TextView)findViewById(R.id.txtUpdateStatus);
            txtUpdateStatus.setText(R.string.syncing);
            
            new Thread () {
        		
        		boolean success = false;
        		
        		public void run() {
        			try {
        				if (!syncing) {
        					syncing = true;
            				
        		    		mDB.delete(directoryPeople.TEMP_TABLE, null, null);
        		    		
        		    	    XmlPullParserFactory parserCreator = XmlPullParserFactory.newInstance();
        		    	    XmlPullParser parser = parserCreator.newPullParser();
        		    	    
        		    	    String XMLaddress = "https://genesee2.sjfc.edu:8910/pls/PROD/sjfc_android_app.employees_xml";
        	    	        URL feed = new URL(XMLaddress);
        		    	    parser.setInput(feed.openStream(), null);
        		    	    
        		    	    ContentValues entry = new ContentValues();
        		    	    int parserEvent = parser.getEventType();
        		    	    String tag = "";
        		    	    String value = "";
        		    	    
        		    	    while (parserEvent != XmlPullParser.END_DOCUMENT) {
        		    	    	if(parserEvent == XmlPullParser.START_TAG) {
        		    	    		tag = parser.getName();
        		    	    		if (isPeopleField(tag)) {
        		    	    			parserEvent = parser.next();
        		    	    			value =  parser.getText();
        		    		    		entry.put(tag, value);
        		    		    		//Log.d("Fisherapp", "Parsed " + tag + ": " + value);
        		    	    		}
        		    	    	}
        		    	    	if(parserEvent == XmlPullParser.END_TAG && parser.getName().compareTo("ROW") == 0) {
        		    	    		mDB.insert(directoryPeople.TEMP_TABLE, null, entry);
        		    	    		Log.d("Fisherapp", "Entry added to table_temp.");
        		    				entry.clear();
        		    	    	}
        		    	     	parserEvent = parser.next();
        		    	    }
        		    	    Log.d("Fisherapp", "Finished parsing People XML.");
        		    	    
        		    	 // Copy temp table to permanent table
        		        	mDB.delete(directoryPeople.PEOPLE_TABLE, null, null);
        		        	
        		        	String copyQuery = "INSERT INTO " + directoryPeople.PEOPLE_TABLE
        		        		+ " SELECT * FROM " + directoryPeople.TEMP_TABLE;
        		        	
        		        	mDB.execSQL(copyQuery);
        		        	
        		        	// Delete contents of temp table
        		        	mDB.delete(directoryPeople.TEMP_TABLE, null, null);
        		        	Log.d("Fisherapp", "Data copied to table_people.");
        		        	success = true;
        				}
    		        	
        			} catch (Exception e) {
        				Log.e("Fisherapp", "XML Parse Error: " + e.toString());
        				success = false;
        			}
        			
        			mHandler.post(new Runnable() {
        				public void run() {
        					if (!syncing) {
        						TextView txtUpdateStatus = (TextView)findViewById(R.id.txtUpdateStatus);
            		        	if (success) {
            			        	txtUpdateStatus.setText(R.string.synced);
            		        	} else {
            		            	txtUpdateStatus.setText(R.string.sync_failed);
            		        	}
            		        	// Refresh the ListView
            		        	adapter.getCursor().requery();
            		        	adapter.notifyDataSetChanged();
            		        	updateFirstSyncMessage();
        					}
        				}
        			});
        		}
        	}.start();
    	}    	
    }
    
    private void updateFirstSyncMessage() {
    	/*
    	View firstSyncMessage = (View) findViewById(R.id.emptyBox);
    	if (mCursor.getCount() == 0) {
    		firstSyncMessage.setVisibility(View.VISIBLE);
    	} else {
    		firstSyncMessage.setVisibility(View.GONE);
    	}
    	*/
    }
    
	public boolean isPeopleField(String tag) {
		if (tag.compareTo(directoryPeople.LAST_NAME) == 0)
			return true;
		if (tag.compareTo(directoryPeople.FIRST_NAME) == 0)
			return true;
		if (tag.compareTo(directoryPeople.MIDDLE_NAME) == 0)
			return true;
		if (tag.compareTo(directoryPeople.FAC_STAFF_DIR) == 0)
			return true;
		if (tag.compareTo(directoryPeople.JOB_TITLE) == 0)
			return true;
		if (tag.compareTo(directoryPeople.DEPARTMENT) == 0)
			return true;
		if (tag.compareTo(directoryPeople.OFFICE) == 0)
			return true;
		if (tag.compareTo(directoryPeople.PHONE_NUMBER) == 0)
			return true;
		if (tag.compareTo(directoryPeople.EMAIL) == 0)
			return true;
		return false;
	}
}