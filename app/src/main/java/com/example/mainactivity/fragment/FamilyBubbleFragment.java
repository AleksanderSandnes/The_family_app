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
import com.example.mainactivity.adapter.FamilyBubbleAdapter;
import com.example.mainactivity.model.FamilyBubbleModel;
import com.example.mainactivity.model.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class FamilyBubbleFragment extends Fragment {
    Database database;
    SharedPreferences sharedPreferences;

    private ArrayList<FamilyBubbleModel> samtaler;
    private FloatingActionButton nyConversation;
    private TextView empty;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_bubble, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);
        empty = view.findViewById(R.id.emptyConversation);
        nyConversation = view.findViewById(R.id.FamilyBubbleNewConversation);

        // Setting names and ids to global arrays
        setNamesAndIds();
        setUpRecyclerView();

        if (samtaler.isEmpty()) { empty.setVisibility(View.VISIBLE); }
        else { empty.setVisibility(View.GONE); }

        // Go to new samtale
        nyConversation.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.action_family_bubbleFragment_to_family_bubbleNyConversationFragment));
    }

    private void setUpRecyclerView() {
        RecyclerView family_bubbleRecyclerView = getView().findViewById(R.id.listOfConversations);
        family_bubbleRecyclerView.setAdapter(new FamilyBubbleAdapter(getContext(), samtaler));
        family_bubbleRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setNamesAndIds() {
        // Getting data from database table CONVERSATION
        Cursor data = database.getData(Database.TABLE_CONVERSATION);

        ArrayList<FamilyBubbleModel> samtaler = new ArrayList<>();

        // Own id from session
        int meID = Integer.parseInt(sharedPreferences.getString(User.ID, null));

        while(data.moveToNext()) {
            int conversationID = Integer.parseInt(data.getString(data.getColumnIndexOrThrow(Database.COLUMN_ID)));
            int fromID = Integer.parseInt(data.getString(data.getColumnIndexOrThrow(Database.COLUMN__USER_FROM)));
            int toID = Integer.parseInt(data.getString(data.getColumnIndexOrThrow(Database.COLUMN__USER_TO)));
            String samtaleName = data.getString(data.getColumnIndexOrThrow(Database.COLUMN__CONVERSATION_NAME));

            if (fromID == meID || toID == meID) {
                // If I am person from set button name = person from
                if (fromID == meID) {
                    // Getting name of user to
                    Cursor userTo = database.getData(Database.TABLE_USER, toID);
                    userTo.moveToFirst();
                    String userToName = userTo.getString(1);
                    String userToFamily = userTo.getString(6);

                    if (userToFamily.equals(sharedPreferences.getString(User.FAMILIE, null))){
                        FamilyBubbleModel samtale = new FamilyBubbleModel(String.valueOf(conversationID), userToName, samtaleName);
                        samtaler.add(samtale);
                    }
                }
                if (toID == meID) {
                    // Getting name of user from
                    Cursor userFrom = database.getData(Database.TABLE_USER, fromID);
                    userFrom.moveToFirst();
                    String userFromName = userFrom.getString(1);
                    String userFromFamily = userFrom.getString(6);

                    if (userFromFamily.equals(sharedPreferences.getString(User.FAMILIE, null))) {
                        FamilyBubbleModel samtale = new FamilyBubbleModel(String.valueOf(conversationID), userFromName, samtaleName);
                        samtaler.add(samtale);
                    }
                }
            }
        }
        this.samtaler = samtaler;
    }
}
