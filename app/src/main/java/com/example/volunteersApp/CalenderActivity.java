package com.example.volunteersApp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // For logging
import android.view.MenuItem; // For handling up button
import android.widget.CalendarView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
// It's good practice to add a Toolbar for a consistent UI
// and to allow a back/up button if you're not just programmatically creating the CalendarView
import androidx.appcompat.widget.Toolbar; // If you add a Toolbar

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CalenderActivity extends AppCompatActivity {

 private static final String TAG = "CalenderActivity"; // For logging
 CalendarView calendarView;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  // Option 1: Programmatically create CalendarView (as you had)
  // For this simple case, this is fine.
  // If you want a toolbar or more complex layout, use Option 2.
  calendarView = new CalendarView(this);
  setContentView(calendarView);

  // Option 2: Inflate from an XML layout (more common for Activities)
  // setContentView(R.layout.activity_calender); // Assuming you create this layout
  // Toolbar toolbar = findViewById(R.id.toolbar_calender); // If you add a toolbar
  // setSupportActionBar(toolbar);
  // if (getSupportActionBar() != null) {
  //     getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  //     getSupportActionBar().setTitle("Select Date");
  // }
  // calendarView = findViewById(R.id.calendarView); // Assuming this ID in your XML

  // Optionally, get the current date passed from HostAnEvent to pre-select
  Intent intent = getIntent();
  if (intent != null) {
   String currentDateStr = intent.getStringExtra("current_date");
   if (currentDateStr != null && !currentDateStr.isEmpty()) {
    try {
     SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
     sdf.setLenient(false); // Make parsing strict
     Calendar cal = Calendar.getInstance();
     cal.setTime(sdf.parse(currentDateStr));
     calendarView.setDate(cal.getTimeInMillis(), false, true);
    } catch (ParseException e) {
     Log.e(TAG, "Error parsing current_date: " + currentDateStr, e);
     // Optionally show a toast or handle error,
     // but usually, just not pre-selecting is fine.
    } catch (Exception e) {
     Log.e(TAG, "Error setting date on CalendarView: ", e);
    }
   }
  }

  calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
   @Override
   public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
    // month is 0-indexed (0 for January, 1 for February, etc.)
    String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);

    // Toast.makeText(CalenderActivity.this, "Selected: " + selectedDate, Toast.LENGTH_SHORT).show();
    Log.d(TAG, "Date selected by user: " + selectedDate);

    Intent resultIntent = new Intent();
    resultIntent.putExtra("selected_date", selectedDate);
    setResult(Activity.RESULT_OK, resultIntent);
    Log.d(TAG, "Result set with RESULT_OK and data. Finishing CalenderActivity.");
    finish(); // Close CalenderActivity and return to HostAnEvent
   }
  });
 }

 // Optional: Handle the Up button in the Toolbar if you add one
 // @Override
 // public boolean onOptionsItemSelected(@NonNull MenuItem item) {
 //     if (item.getItemId() == android.R.id.home) {
 //         // User pressed the Up button, treat as cancel
 //         setResult(Activity.RESULT_CANCELED);
 //         finish();
 //         return true;
 //     }
 //     return super.onOptionsItemSelected(item);
 // }

 // Optional: Handle hardware back press to ensure RESULT_CANCELED is sent if no date chosen
 // @Override
 // public void onBackPressed() {
 //     // Check if a result has already been set (e.g., by onSelectedDayChange)
 //     // This logic might be tricky if you want to allow changing selection and then pressing back
 //     // For simplicity, if they press back before selecting, it's a cancel.
 //     // If onSelectedDayChange already called finish(), this won't be reached for that path.
 //     setResult(Activity.RESULT_CANCELED);
 //     Log.d(TAG, "Back pressed, setting result to CANCELED.");
 //     super.onBackPressed();
 // }
}