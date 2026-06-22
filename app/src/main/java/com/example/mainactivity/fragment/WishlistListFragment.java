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
import com.example.mainactivity.adapter.WishlistListAdapter;
import com.example.mainactivity.model.WishlistListModel;
import com.example.mainactivity.model.User;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;

public class WishlistListFragment extends Fragment {
    public WishlistListFragment() {
        // Required empty constructor
    }

    Database database;
    SharedPreferences sharedPreferences;

    private int wishlistID, wishlistForUserID;
    private String wishlistName, wishlistForUserName;
    private ArrayList<WishlistListModel> wishes;
    private TextView wishlistBruker;
    private EditText onskeToAdd;
    private Button leggTilBtn;
    private TextInputLayout onskeToAddParent;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wishlist_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        wishlistID = getArguments().getInt("wishlistId");
        wishlistForUserID = getArguments().getInt("wishlistForBrukerID");
        wishlistName = getArguments().getString("wishlistNavn");
        wishlistForUserName = getArguments().getString("wishlistForBruker");

        String text = wishlistName + " (" + wishlistForUserName + ")";
        wishlistBruker = view.findViewById(R.id.WishlistBruker);
        wishlistBruker.setText(text);

        onskeToAddParent = view.findViewById(R.id.textInputLayout);
        onskeToAdd = view.findViewById(R.id.onskeToAdd);
        leggTilBtn = view.findViewById(R.id.leggTil);
        leggTilBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addWish();
            }
        });

        setWishesInWishlist();
        setUpRecyclerView();
        if (wishlistForUserID != Integer.parseInt(sharedPreferences.getString(User.ID, null)))
            hideElements();
    }

    private void hideElements() {
        onskeToAddParent.setVisibility(View.INVISIBLE);
        leggTilBtn.setVisibility(View.INVISIBLE);
    }

    private void setUpRecyclerView() {
        RecyclerView wishlistListRecyclerView = getView().findViewById(R.id.WishlistRecyclerview);
        wishlistListRecyclerView.setAdapter(new WishlistListAdapter(getContext(), wishes));

        wishlistListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setWishesInWishlist() {
        // Getting data from database table MESSAGES
        Cursor wishes = database.getAllWishesFromWishlist(wishlistID);

        ArrayList<WishlistListModel> allWishes = new ArrayList<>();

        while(wishes.moveToNext()) {
            int wishID = Integer.parseInt(wishes.getString(wishes.getColumnIndexOrThrow(Database.COLUMN_ID)));
            int wishlistID = wishes.getInt(wishes.getColumnIndexOrThrow(Database.COLUMN__WISHLIST_ID));
            String wish = wishes.getString(wishes.getColumnIndexOrThrow(Database.COLUMN__NAME_WISH));
            boolean isChecked = wishes.getInt(wishes.getColumnIndexOrThrow(Database.COLUMN__WISH_CHECKED)) == 1;
            int userID = wishes.getInt(wishes.getColumnIndexOrThrow(Database.COLUMN__WISH_USER_ID));

            if (wishlistID == this.wishlistID) {
                WishlistListModel wish1 = new WishlistListModel(wishID, userID, wish, isChecked);

                allWishes.add(wish1);
            }
        }
        this.wishes = allWishes;
    }

    private void addWish() {
        int meID = Integer.parseInt(sharedPreferences.getString(User.ID, null));
        long addToDatabase = -1;

        if (!onskeToAdd.getText().toString().isEmpty())
            addToDatabase = database.addWishToWishlist(wishlistID, onskeToAdd.getText().toString(), meID);
        else {
            Toast.makeText(getContext(),"Du må skrive inn et ønske først", Toast.LENGTH_SHORT).show();
            Log.e("WishlistList", "Brukeren skrev ikke inn et ønske");
        }

        if (addToDatabase >= 0) {
            Log.i("WishlistList", "Added wish: " + onskeToAdd.getText().toString());
            onskeToAdd.setText("");
            setWishesInWishlist();
            setUpRecyclerView();
        }
    }
}