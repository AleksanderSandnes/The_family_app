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

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.adapter.ShoppingListItemAdapter;
import com.example.mainactivity.model.ShoppingListItemsModel;
import com.example.mainactivity.model.User;

import java.util.ArrayList;

public class ShoppingListListFragment extends Fragment {
    public ShoppingListListFragment() {
        // Required empty constructor
    }

    private Database database;
    private SharedPreferences sharedPreferences;
    private ArrayList<ShoppingListItemsModel> varer;
    private TextView tittel;
    private Button leggTil;
    private EditText varelinje;
    private RecyclerView varelist;
    private String id;
    private String sendtTittel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shopping_list_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);
        tittel = view.findViewById(R.id.listTittel);
        leggTil = view.findViewById(R.id.vareAdd);
        varelinje = view.findViewById(R.id.vareLinje);
        varelist = view.findViewById(R.id.ShoppingListListRecyclerView);

        id = getArguments().getString("ID");
        sendtTittel = getArguments().getString("TITTEL");

        tittel.setText(sendtTittel);

        leggTil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addItem();
            }
        });

        setInfo();
        setUpRecyclerView();
    }

    // Setter info i recyclerView
    private void setUpRecyclerView() {
        varelist.setAdapter(new ShoppingListItemAdapter(getContext(), varer));
        varelist.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    // Henter varer til shopping_list
    private void setInfo() {
        Cursor data = database.getAlleItemrFraShoppingList(Integer.valueOf(id));

        ArrayList<ShoppingListItemsModel> allevarer = new ArrayList<>();

        while(data.moveToNext()) {
            String ID = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_ID));
            String vareTittel = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_HANDLELISTELISTE_VARE));
            boolean isChecked = data.getInt(data.getColumnIndexOrThrow(Database.COLUMN_HANDLELISTELISTE_CHECKED)) == 1;

            ShoppingListItemsModel envare = new ShoppingListItemsModel(ID, vareTittel, isChecked);
            allevarer.add(envare);
        }
        this.varer = allevarer;
    }

    // Legger til en vare i shopping_list
    private void addItem() {
        long addToDatabase = -1;

        if( !varelinje.getText().toString().isEmpty()) {
            addToDatabase = database.addvarerShoppingList(varelinje.getText().toString(), id);
            Log.i("ShoppingListList", "En vare ble lagt til i list");
        }
        else {
            Toast.makeText(getContext(),"Du må skrive inn en vare først", Toast.LENGTH_SHORT).show();
            Log.e("ShoppingListList", "Brukeren skrev ikke inn en vare");
        }

        if (addToDatabase >= 0) {
            Log.i("ShoppingListList", "Added vare : " + varelinje.getText().toString());
            varelinje.setText("");
            setInfo();
            setUpRecyclerView();
        }
        System.out.println(varelinje.getText().toString());
    }
}