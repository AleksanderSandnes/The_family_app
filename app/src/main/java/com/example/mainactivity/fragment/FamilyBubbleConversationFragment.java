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
import android.widget.TextView;
import android.widget.Toast;

import com.example.mainactivity.Database;
import com.example.mainactivity.R;
import com.example.mainactivity.adapter.FamilyBubbleConversationAdapter;
import com.example.mainactivity.model.FamilyBubbleConversationModel;
import com.example.mainactivity.model.User;

import java.util.ArrayList;

public class FamilyBubbleConversationFragment extends Fragment {
    public FamilyBubbleConversationFragment() {}

    Database database;
    SharedPreferences sharedPreferences;

    private int samtaleId;
    private String samtaleName, samtaleTo;
    private ArrayList<FamilyBubbleConversationModel> messages;
    private TextView samtaleTitle, messageToSend, brukerNavn;
    private Button sendButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_bubble_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = new Database(getActivity());
        sharedPreferences = this.requireActivity().getSharedPreferences(User.SESSION, Context.MODE_PRIVATE);

        // Henter argumenter som ble sendt via bundle
        setConversationId(Integer.parseInt(getArguments().getString("samtaleId")));
        setConversationName(getArguments().getString("samtaleName"));
        setConversationTo(getArguments().getString("samtaleTo"));

        // Setter samtalenavn og hvem det er en samtale med
        samtaleTitle = view.findViewById(R.id.Conversationnavn);
        samtaleTitle.setText(samtaleName);

        String samtaleMed = "Conversation med: " + samtaleTo;
        brukerNavn = view.findViewById(R.id.brukerNavn);
        brukerNavn.setText(samtaleMed);

        messageToSend = view.findViewById(R.id.family_bubbleConversation_messageToSend);
        sendButton = view.findViewById(R.id.family_bubbleConversation_sendMessageBtn);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // Henter fra databasen
        setMessagesInConversation();
        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        RecyclerView family_bubbleConversationRecyclerView = getView().findViewById(R.id.samtaleDisplay);
        family_bubbleConversationRecyclerView.setAdapter(new FamilyBubbleConversationAdapter(getContext(), messages));

        family_bubbleConversationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Går til bunnen av samtalen, slik at man slipper å scrolle til de siste meldingene
        family_bubbleConversationRecyclerView.scrollToPosition(messages.size() - 1);
    }

    private void setMessagesInConversation() {
        // Getting data from database table MESSAGES
        Cursor messages = database.getAllMessageFromConversation(samtaleId);

        ArrayList<FamilyBubbleConversationModel> allMessages = new ArrayList<>();

        while(messages.moveToNext()) {
            int messageID = Integer.parseInt(messages.getString(messages.getColumnIndexOrThrow(Database.COLUMN_ID)));
            int conversationID = Integer.parseInt(messages.getString(messages.getColumnIndexOrThrow(Database.COLUMN__MESSAGE_PART_OF_CONVERSATIONID)));
            int fromID = Integer.parseInt(messages.getString(messages.getColumnIndexOrThrow(Database.COLUMN__MESSAGE_USER_FROM)));
            String message = messages.getString(messages.getColumnIndexOrThrow(Database.COLUMN__MESSAGE_TEXT));

            // Dersom samtaleID fra database og lokalt er like vil den bli lagt til i array
            if (samtaleId == conversationID) {
                FamilyBubbleConversationModel message2 = new FamilyBubbleConversationModel(messageID, fromID, conversationID, message);
                allMessages.add(message2);
            }
        }
        this.messages = allMessages;
    }

    // Legger en melding til i databasen og henter meldingene på nytt
    private void sendMessage() {
        int meID = Integer.parseInt(sharedPreferences.getString(User.ID, null));
        long addToDatabase = -1;

        if (!messageToSend.getText().toString().isEmpty())
            addToDatabase = database.sendMessage(samtaleId, meID, messageToSend.getText().toString());

        if (addToDatabase >= 0) {
            messageToSend.setText("");
            setMessagesInConversation();
            setUpRecyclerView();
        } else {
            Toast.makeText(getContext(),"Det oppstod en feil!", Toast.LENGTH_SHORT).show();
            Log.e("FamilyBubbleConv", "Kunne ikke sende meldingen: " + messageToSend.getText().toString());
        }
    }

    public void setConversationId(int samtaleId) {
        this.samtaleId = samtaleId;
    }

    public void setConversationName(String samtaleName) {
        this.samtaleName = samtaleName;
    }

    public void setConversationTo(String samtaleTo) {
        this.samtaleTo = samtaleTo;
    }
}