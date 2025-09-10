package com.example.volunteersApp;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties // Recommended for Firestore POJOs
public class HowToUseTip {
    private String title;
    private String content;
    private int order; // Assuming you have an 'order' field for sorting

    // Required empty public constructor for Firestore's toObject() method
    public HowToUseTip() {
    }

    // Constructor with arguments (optional, but can be useful)
    public HowToUseTip(String title, String content, int order) {
        this.title = title;
        this.content = content;
        this.order = order;
    }

    // --- Getters ---
    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public int getOrder() {
        return order;
    }

    // --- Setters (optional if you only read from Firestore, but good practice) ---
    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    // Optional: toString() for logging/debugging
    @Override
    public String toString() {
        return "HowToUseTip{" +
                "title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", order=" + order +
                '}';
    }
}

