package com.example.mainactivity.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.adapter.MealPlanListAdapter;
import com.example.mainactivity.model.MealPlanListModel;
import com.example.mainactivity.model.User;

import java.util.ArrayList;

public class MealPlanListFragment extends Fragment {
    // Variabler for å hente fra database
    private Database database;
    private SharedPreferences sharedPreferences;

    public MealPlanListFragment() {
    }

    private TextView overskrift;
    private RecyclerView meal_planListRecyclerview;

    private int meal_planID, week;
    private ArrayList<MealPlanListModel> allFoodplans;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_plan_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        overskrift = view.findViewById(R.id.overskriftMealPlan);
        meal_planListRecyclerview = view.findViewById(R.id.MealPlanListRecyclerview);

        setMealPlanID(getArguments().getInt("ID"));
        setWeek(getArguments().getInt("UKE"));

        String overskriften = "MealPlan uke " + week;
        overskrift.setText(overskriften);

        getMealPlans();
        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        meal_planListRecyclerview = getView().findViewById(R.id.MealPlanListRecyclerview);
        meal_planListRecyclerview.setAdapter(new MealPlanListAdapter(getContext(), allFoodplans));
        meal_planListRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void getMealPlans() {
        // Getting data from database table MESSAGES
        Cursor foodplans = database.getAllFoodplansForFoodplan(meal_planID);

        ArrayList<MealPlanListModel> allFoodplans = new ArrayList<>();

        while(foodplans.moveToNext()) {
            int subMealPlanID = foodplans.getInt(foodplans.getColumnIndexOrThrow(Database.COLUMN_ID));
            int meal_planID = foodplans.getInt(foodplans.getColumnIndexOrThrow(Database.COLUMN__SUBMATPLAN_MATPLANID));
            String day = foodplans.getString(foodplans.getColumnIndexOrThrow(Database.COLUMN__SUBMATPLAN_DAY));
            String food = foodplans.getString(foodplans.getColumnIndexOrThrow(Database.COLUMN__SUBMATPLAN_FOOD));

            // Dersom samtaleID fra database og lokalt er like vil den bli lagt til i array
            if (meal_planID == this.meal_planID) {
                MealPlanListModel meal_planData = new MealPlanListModel(subMealPlanID, day, food);
                allFoodplans.add(meal_planData);
            }
        }
        this.allFoodplans = allFoodplans;
    }

    public void setMealPlanID(int meal_planID) {
        this.meal_planID = meal_planID;
    }

    public void setWeek(int week) {
        this.week = week;
    }
}