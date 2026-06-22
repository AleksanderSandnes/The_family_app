package com.example.mainactivity.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
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
import com.example.mainactivity.adapter.ShoppingListAdapter;
import com.example.mainactivity.model.ShoppingListModel;
import com.example.mainactivity.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class ShoppingListFragment extends Fragment {
    public ShoppingListFragment() {
        // Required empty constructor
    }

    //Elementer i layouten
    private FloatingActionButton NyShoppingList;
    private TextView empty;
    private RecyclerView shopping_listRecyclerView;

    // Variabler for å hente fra database
    private Database database;
    private SharedPreferences sharedPreferences;
    private Integer familieID;
    private String bruker;

    // ArrayList for å lagre dataen fra databasen
    private ArrayList<ShoppingListModel> shopping_list = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shopping_list, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);
        familieID = Integer.valueOf(sharedPreferences.getString(User.FAMILIE, null));
        bruker = sharedPreferences.getString(User.NAME, null);
        NyShoppingList = view.findViewById(R.id.NyShoppingList);
        empty = view.findViewById(R.id.emptyShoppingList);
        shopping_listRecyclerView = requireView().findViewById(R.id.ShoppingListRecyclerview);

        // Metoder
        setInfo();
        setUpRecyclerView();

        if (shopping_list.isEmpty()) { empty.setVisibility(View.VISIBLE); }
        else { empty.setVisibility(View.GONE); }

        // Tar deg videre til nytt fragment
        NyShoppingList.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.shopping_listAddFragment));
    }

    // Metoder for å fylle Arraylist med data fra databasen
    private void setInfo() {
        // Henter data fra databasetabell ShoppingList
        Cursor data = database.getData(Database.TABLE_HANDLELISTE);

        ArrayList<ShoppingListModel> shopping_listr = new ArrayList<>();

        while(data.moveToNext()) {
            String id = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_ID));
            String tittel = data.getString(data.getColumnIndexOrThrow(Database.COLUMN_HANDLELISTE_TITTEL));
            int userID = data.getInt(data.getColumnIndexOrThrow(Database.COLUMN_HANDLELISTE_USERID));

            Cursor userName = database.getData(Database.TABLE_USER, userID);
            userName.moveToFirst();
            String nameOfUser = userName.getString(1);
            String userFamily = userName.getString(6);

            if (userFamily.equals(sharedPreferences.getString(User.FAMILIE, null))) {
                ShoppingListModel list = new ShoppingListModel(tittel, id, familieID, nameOfUser);
                shopping_listr.add(list);
            }
        }
        this.shopping_list = shopping_listr;
    }

    private void setUpRecyclerView() {
        shopping_listRecyclerView.setAdapter(new ShoppingListAdapter(getContext(), shopping_list, familieID));
        shopping_listRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }
}