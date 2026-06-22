package com.example.mainactivity.fragment;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Toast;

import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.model.User;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MealPlanAddFragment extends Fragment {
    public MealPlanAddFragment() {
        // Required empty constructor
    }

    // Variabler
    private TextInputEditText fraDato, tilDato;
    private Button opprettBtn;
    Database database;
    SharedPreferences sharedPreferences;
    private NavController navController;
    private Date fromDate, toDate;
    private String dateFromString, dateToString;
    private int mYear, mMonth, mDay, familyID, meal_planID;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_plan_add, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Instansierer variabler
        navController = Navigation.findNavController(view);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        fraDato = view.findViewById(R.id.txtDateFromMealPlan);
        tilDato = view.findViewById(R.id.txtDateToMealPlan);
        opprettBtn = view.findViewById(R.id.opprettMealPlan);

        familyID = Integer.parseInt(sharedPreferences.getString(User.FAMILIE, null));

        fromDate = new Date();
        toDate = new Date();

        // Her henter man er DatePicker, og gjør at bruker kan velge en dato
        fraDato.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fromDate.equals(new Date())) {
                    final Calendar c = Calendar.getInstance();
                    mYear = c.get(Calendar.YEAR);
                    mMonth = c.get(Calendar.MONTH);
                    mDay = c.get(Calendar.DAY_OF_MONTH);
                } else {
                    mYear = fromDate.getYear() + 1900;
                    mMonth = fromDate.getMonth();
                    mDay = fromDate.getDate();
                }

                DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), R.style.my_dialog_theme,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                                Date date = new Date((year-1900), monthOfYear, dayOfMonth);

                                fromDate.setYear(year-1900);
                                fromDate.setMonth(monthOfYear);
                                fromDate.setDate(dayOfMonth);

                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "dd.MM.yyyy");

                                fraDato.setText(simpleDateFormat.format(date));
                                dateFromString = simpleDateFormat.format(date);
                            }
                        }, mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });

        // Her henter man er DatePicker, og gjør at bruker kan velge en dato
        tilDato.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toDate.equals(new Date())) {
                    final Calendar c = Calendar.getInstance();
                    mYear = c.get(Calendar.YEAR);
                    mMonth = c.get(Calendar.MONTH);
                    mDay = c.get(Calendar.DAY_OF_MONTH);
                } else {
                    mYear = toDate.getYear() + 1900;
                    mMonth = toDate.getMonth();
                    mDay = toDate.getDate();
                }

                DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), R.style.my_dialog_theme,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                                Date date = new Date((year-1900), monthOfYear, dayOfMonth);

                                toDate.setYear(year-1900);
                                toDate.setMonth(monthOfYear);
                                toDate.setDate(dayOfMonth);

                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "dd.MM.yyyy");

                                tilDato.setText(simpleDateFormat.format(date));
                                dateToString = simpleDateFormat.format(date);
                            }
                        }, mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });

        opprettBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dateFromString.isEmpty() || dateToString.isEmpty()) {
                    Toast.makeText(getActivity(), "Fyll inn alle feltene", Toast.LENGTH_SHORT).show();
                    Log.e("MealPlanAdd", "Brukeren fylte ikke inn alle feltene");
                    return;
                }

                if (toDate.before(fromDate)) {
                    Toast.makeText(getActivity(), "Til dato må være før/lik som fra dato", Toast.LENGTH_SHORT).show();
                    Log.e("MealPlanAdd", "Brukeren fylte inn til dato som ikke er før/lik fra dato");
                    return;
                }

                // Gjør om for å få noen ferdige metoder
                Calendar calFrom = Calendar.getInstance();
                Calendar calTo = Calendar.getInstance();
                calFrom.setTime(fromDate);
                calTo.setTime(toDate);

                int week = calFrom.get(Calendar.WEEK_OF_YEAR);
                int daysBetween = (int) ((toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24));

                // Går fint så lenge det er maks 6 dager mellom f.eks. mandag - mandag
                if (daysBetween > 7) {
                    Toast.makeText(getActivity(), "Det må maks være syv dager mellom til og fra dato", Toast.LENGTH_SHORT).show();
                    Log.e("MealPlanAdd", "Det er over syv dager mellom til og fra dato");
                    return;
                }
                boolean addToDatabase = false;

                if (sjekkOfMealPlanFinnesFraFor(week))
                    addToDatabase = database.addWeekToMealPlan(dateFromString, dateToString, familyID, week);

                // Henter id til meal_plan
                Cursor meal_planIdQuery = database.getMealPlanIdByLastRow();
                meal_planID = 1;
                while(meal_planIdQuery.moveToNext()) {
                    meal_planID = Integer.parseInt(meal_planIdQuery.getString(0));
                }

                if (addToDatabase) {
                    Log.i("MealPlanAddFragment", "MealPlan i uke " + week + " er opprettet");
                    addDaysToMealPlan(meal_planID, daysBetween);
                    navController.navigateUp();
                } else {
                    Toast.makeText(getActivity(), "MealPlan i uke " + week + " finnes allerede fra før på disse datoene", Toast.LENGTH_SHORT).show();
                    Log.e("MealPlanAddFragment", "MealPlan for denne uken finnes allerede");
                }
            }

            // Legger til dager (mandag, tirsdag, ...) i databasen
            private void addDaysToMealPlan(int meal_planID, int daysBetween) {
                Date dateFromTemp = fromDate;
                Date date = fromDate;

                for (int i = 0; i < daysBetween; i++) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);

                    String dateOnStringForm = date.getDate() + "." + (date.getMonth() + 1) + "." + (date.getYear() + 1900);

                    Locale current = getResources().getConfiguration().locale;
                    SimpleDateFormat sdf = new SimpleDateFormat("EEEE", current);

                    String dayOfWeek = sdf.format(date);
                    dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1);

                    database.makeSubMealPlan(meal_planID, dayOfWeek, dateOnStringForm, familyID);

                    date.setDate(date.getDate() + 1);
                }
                fromDate = dateFromTemp;
            }

            // sjekker om det finnes en meal_plan med samme start dato, og til dato
            private boolean sjekkOfMealPlanFinnesFraFor(int week) {
                Cursor insertData = database.getData(Database.TABLE_MATPLAN);

                while(insertData.moveToNext()) {
                    String uke = insertData.getString(insertData.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_UKE));
                    String fromDate = insertData.getString(insertData.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_FROM_DATE));
                    String toDate = insertData.getString(insertData.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_TO_DATE));
                    String familieID = insertData.getString(insertData.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_FAMILY_ID));

                    if (familieID.equals(String.valueOf(familyID)) && uke.equals(String.valueOf(week)) && fromDate.equals(dateFromString) && toDate.equals(dateToString)) {
                        return false;
                    }
                }
                return true;
            }
        });
    }
}