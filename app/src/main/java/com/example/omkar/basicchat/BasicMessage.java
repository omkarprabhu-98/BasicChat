package com.example.omkar.basicchat;

/**
 * Created by omkar on 12/11/2017.
 */

public class BasicMessage {

    private String text;
    private String name;
    private String photoUrl;

    public BasicMessage(){

    }

    // Constructor for initializing variables
    public BasicMessage(String text, String name, String photoUrl){
        this.text = text;
        this.name = name;
        this.photoUrl = photoUrl;
    }

    // Helper functions for BasicMessage object
    public String getText(){
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhotoUrl() {
        return this.photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

}
