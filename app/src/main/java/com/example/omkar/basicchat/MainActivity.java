package com.example.omkar.basicchat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER =  2;


    // Element reference variables
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    // username
    private String mUsername;

    // Database reference objects
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessageDatabaseReference;
    private ChildEventListener mChildEventListener;

    // Firebase Auth Objects
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    // Firebase Storage Object
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Database objects instantiated
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mMessageDatabaseReference = mFirebaseDatabase.getReference().child("messages");

        // Auth objects initialized
        mFirebaseAuth = FirebaseAuth.getInstance();

        // Storage objects initialized
        mFirebaseStorage = FirebaseStorage.getInstance();
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Checking if user is authenticated to access database
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                // get user from firebase Auth passed during AuthStateChange
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null){
                    // user is signed in
                    onSignedIn(user.getDisplayName());
                }
                else {
                    // user is signed out
                    onSignedOut();
//                  // Load FirebaseAuth UI
                    startActivityForResult(
                            AuthUI.getInstance().createSignInIntentBuilder().build(),
                            RC_SIGN_IN);
                }
            }
        };

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<BasicMessage> basicMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, basicMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Create a object using the text entered
                BasicMessage basicMessage = new BasicMessage(mMessageEditText.getText().toString(), mUsername, null);

                // Push message to real-time Database
                mMessageDatabaseReference.push().setValue(basicMessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.sign_out_menu:

                // sign out
                AuthUI.getInstance().signOut(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // overriding activity lifecycle methods for mAuthStateListener
    @Override
    protected void onPause() {
        super.onPause();

        if (mAuthStateListener != null){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    /**
     * Load messages and set username after the user has signed in
     * @param username
     */
    private void onSignedIn(String username){

        // set username of the signed in user
        mUsername = username;

        if(mChildEventListener == null){
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    // add Messages the the list
                    BasicMessage basicMessage = dataSnapshot.getValue(BasicMessage.class);
                    mMessageAdapter.add(basicMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {}

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            };

            // Event Listener for reading data from real time database
            mMessageDatabaseReference.addChildEventListener(mChildEventListener);
        }


    }

    /**
     * Remove messages and reset username after the user has signed in
     *
     */
    private void onSignedOut(){

        // set username of the signed in user
        mUsername = ANONYMOUS;

        // detach ChildEventListener after signed out
        if(mChildEventListener != null){
            mMessageDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }

        // remove messages in display
        mMessageAdapter.clear();

    }


    // Overriding onActivityResult for handling canceling sign in
    // this method gets called before the onResume method of the activity lifecycle
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN){
            if (resultCode == RESULT_OK){
                Toast.makeText(MainActivity.this, "Signed in!", Toast.LENGTH_SHORT).show();
            }
            else if (resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){
            Uri selectImageUri = data.getData();
            StorageReference photoRef = mChatPhotosStorageReference.child(selectImageUri.getLastPathSegment());

            photoRef.putFile(selectImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Log.d("Check", "IN");
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();
                    BasicMessage basicMessage = new BasicMessage(null, mUsername, downloadUrl.toString());
                    mMessageDatabaseReference.push().setValue(basicMessage);
                }
            });


        }
    }
}
