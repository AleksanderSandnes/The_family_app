package com.example.mainactivity.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.adapter.BirthdayAdapter;
import com.example.mainactivity.model.BirthdayModel;
import com.example.mainactivity.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BirthdayFragment extends Fragment{

    public BirthdayFragment() {
        // Required empty constructor
    }

    private String familieIdBruker, familieIdTabell;

    //Elementer i layouten
    private FloatingActionButton NyBirthday;
    private TextView empty;
    private RecyclerView birthdayRecyclerView;

    // Variabler for å hente fra database
    private Database database;
    private SharedPreferences sharedPreferences;

    // ArrayList for å lagre dataen fra databasen
    private ArrayList<BirthdayModel> birthdayer = new ArrayList<>();
    private ArrayList<BirthdayModel> SorterBirthdayer = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_birthday, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final NavController navController = Navigation.findNavController(requireActivity(), R.id.fragment);

        // instansierer variablene
        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);
        birthdayRecyclerView = requireView().findViewById(R.id.BirthdayRecyclerview);
        empty = requireView().findViewById(R.id.emptyBirthday);
        NyBirthday = view.findViewById(R.id.BirthdayNyBirthday);

        // Metoder
        setInfo();
        setUpRecyclerView();

        if (birthdayer.isEmpty()) { empty.setVisibility(View.VISIBLE); }
        else { empty.setVisibility(View.GONE); }

        // Tar deg videre til nytt fragment
        NyBirthday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("ID", sharedPreferences.getString(User.FAMILIE, null));
                Log.i("BurdagFragment", "Sender bruker videre til nytt fragment");
                navController.navigate(R.id.birthdayAddFragment);
            }
        });
    }

    // Metode for å fylle ArrayListne med data fra databasen.
    private void setInfo() {
        Cursor data = database.getData(Database.TABLE_BIRTHDAY);

        ArrayList<BirthdayModel> alleBirthdayer = new ArrayList<>();

        while(data.moveToNext()) {

            String navnet = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_NAME_BIRTHDAY));
            String datoen = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_DATE));
            String id = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_ID));
            String familieId = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_FAMILYID));
            String userID = null;
            String madeByUserID = null;

            if (data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_USERID)) != null)
                userID = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_USERID));

            if (data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_MADEBY_USERID)) != null)
                madeByUserID = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_BIRTHDAY_MADEBY_USERID));

            if (familieId != null) {
                if (familieId.equals(sharedPreferences.getString(User.FAMILIE, null))) {
                    BirthdayModel birthday = new BirthdayModel(navnet, datoen, id, familieId, userID, madeByUserID);
                    alleBirthdayer.add(birthday);
                }
            }
        }
        this.birthdayer = alleBirthdayer;
        this.SorterBirthdayer = alleBirthdayer;
    }

    // Metode for å sette opp recyclerviewet med cardview for hver rad i databasen
    private void setUpRecyclerView() {


        Comparator<BirthdayModel> Day = new Comparator<BirthdayModel>() {
            public int compare(BirthdayModel c1, BirthdayModel c2) {
                DateTimeFormatter format = DateTimeFormatter.ofPattern("d.M.uuuu");
                LocalDate today = LocalDate.now();
                LocalDate d1 = LocalDate.parse(c1.getDato(), format);
                LocalDate d2 = LocalDate.parse(c2.getDato(), format);
                return d1.getDayOfMonth() - d2.getDayOfMonth();
            }
        };
        Collections.sort(birthdayer, Day);

        Comparator<BirthdayModel> byMonth = new Comparator<BirthdayModel>() {
            public int compare(BirthdayModel c1, BirthdayModel c2) {
                DateTimeFormatter format = DateTimeFormatter.ofPattern("d.M.uuuu");
                LocalDate d1 = LocalDate.parse(c1.getDato(), format);
                LocalDate d2 = LocalDate.parse(c2.getDato(), format);
                return d1.getMonth().compareTo(d2.getMonth());
            }
        };
        Collections.sort(birthdayer, byMonth);

        birthdayRecyclerView.setAdapter(new BirthdayAdapter(getContext(), birthdayer));
        birthdayRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }
}