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
import com.example.mainactivity.adapter.WishlistAdapter;
import com.example.mainactivity.model.WishlistModel;
import com.example.mainactivity.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class WishlistFragment extends Fragment {
    Database database;
    SharedPreferences sharedPreferences;

    public WishlistFragment() {
        // Required empty constructor
    }

    private FloatingActionButton nyWishlist;
    private TextView empty;
    private RecyclerView WishlistRecyclerview;

    // ArrayList for å lagre dataen fra databasen
    private ArrayList<WishlistModel> wishlistr = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wishlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        getWishlists();
        nyWishlist = view.findViewById(R.id.CreateWishlistBtn);
        WishlistRecyclerview = getView().findViewById(R.id.WishlistrRecyclerview);
        empty = view.findViewById(R.id.emptyWishlist);

        setUpRecyclerView();
        if (wishlistr.isEmpty()) { empty.setVisibility(View.VISIBLE); }
        else { empty.setVisibility(View.GONE); }

        nyWishlist.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.wishlistAddFragment));
    }

    private void getWishlists() {
        // Getting data from database table WISHLIST
        Cursor wishlistsFromDB = database.getData(Database.TABLE_WISHLIST);

        ArrayList<WishlistModel> wishlistr = new ArrayList<>();

        String familieID = sharedPreferences.getString(User.FAMILIE, null);

        while(wishlistsFromDB.moveToNext()) {
            int wishlistID = Integer.parseInt(wishlistsFromDB.getString(wishlistsFromDB.getColumnIndexOrThrow(Database.COLUMN_ID)));
            int wishlistUserID = Integer.parseInt(wishlistsFromDB.getString(wishlistsFromDB.getColumnIndexOrThrow(Database.COLUMN__USER_ID_WISHLIST)));
            String wishlistName = wishlistsFromDB.getString(wishlistsFromDB.getColumnIndexOrThrow(Database.COLUMN__NAME_WISHLIST));

            Cursor wishlistUser = database.getData(Database.TABLE_USER, wishlistUserID);
            wishlistUser.moveToFirst();
            String userToName = wishlistUser.getString(1);
            String userToFamily = wishlistUser.getString(6);

            if (userToFamily.equals(familieID)) {
                WishlistModel wishlist = new WishlistModel(wishlistID, userToName, wishlistUserID, wishlistName);
                wishlistr.add(wishlist);
            }
        }
        this.wishlistr = wishlistr;
    }

    private void setUpRecyclerView() {
        int meID = Integer.parseInt(sharedPreferences.getString(User.ID, null));

        RecyclerView WishlistRecyclerview = getView().findViewById(R.id.WishlistrRecyclerview);
        WishlistRecyclerview.setAdapter(new WishlistAdapter(getContext(), wishlistr, meID));
        WishlistRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
    }
}
