package com.example.mainactivity.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.adapter.MealPlanAdapter;
import com.example.mainactivity.model.MealPlanModel;
import com.example.mainactivity.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class MealPlanFragment extends Fragment {
    // Variabler for å hente fra database
    private Database database;
    private SharedPreferences sharedPreferences;

    public MealPlanFragment() {
        // Required empty constructor
    }

    // Elementer i layouten
    private FloatingActionButton nyMealPlan;
    private TextView empty;
    private RecyclerView meal_planRecyclerview;

    // ArrayList for å lagre dataen fra databasen
    private ArrayList<MealPlanModel> meal_planer = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        getFoodplans();
        nyMealPlan = view.findViewById(R.id.NyMealPlan);
        meal_planRecyclerview = getView().findViewById(R.id.meal_planRecyclerview);
        empty = view.findViewById(R.id.emptyMealPlan);

        setUpRecyclerView();
        if (meal_planer.isEmpty()) { empty.setVisibility(View.VISIBLE); }
        else { empty.setVisibility(View.GONE); }

        nyMealPlan.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.meal_planAddFragment));
    }

    private void getFoodplans() {
        // Getting data from database table MATPLAN
        Cursor foodplansFromDB = database.getData(Database.TABLE_MATPLAN);

        ArrayList<MealPlanModel> meal_planer = new ArrayList<>();

        String familieID = sharedPreferences.getString(User.FAMILIE, null);

        while(foodplansFromDB.moveToNext()) {
            int meal_planID = foodplansFromDB.getInt(foodplansFromDB.getColumnIndexOrThrow(Database.COLUMN_ID));
            String fromDate = foodplansFromDB.getString(foodplansFromDB.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_FROM_DATE));
            String toDate = foodplansFromDB.getString(foodplansFromDB.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_TO_DATE));
            int familyID = foodplansFromDB.getInt(foodplansFromDB.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_FAMILY_ID));
            int week = foodplansFromDB.getInt(foodplansFromDB.getColumnIndexOrThrow(Database.COLUMN_MATPLAN_UKE));

            if (familieID.equals(String.valueOf(familyID))) {
                MealPlanModel meal_plan = new MealPlanModel(meal_planID, week, fromDate, toDate);
                meal_planer.add(meal_plan);
            }
        }
        this.meal_planer = meal_planer;
    }

    private void setUpRecyclerView() {
        meal_planRecyclerview.setAdapter(new MealPlanAdapter(getContext(), meal_planer));
        meal_planRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
    }
}