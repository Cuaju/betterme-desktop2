package com.betterme.models;

import MultimediaService.Multimedia;
import com.google.protobuf.Timestamp;
import java.time.Instant;

public class Post {
    public byte[] getMultimedia() {
        return multimedia;
    }

    public void setMultimedia(byte[] multimedia) {
        this.multimedia = multimedia;
    }

    public enum PostCategory {
        Health,
        Workout,
        Medicine,
        Food
    }

    public enum PostStatus {
        Published,
        Reported,
        Deleted
    }

    private String ID;
    private String title;
    private String userID;
    private String description;
    private PostCategory category;
    private Instant timestamp;
    private PostStatus status;
    private byte[] multimedia;

    public Post() {}

    public Post(String _title, String _userID, PostCategory _category) {
        title = _title;
        userID = _userID;
        category = _category;
    }

    public Post(Multimedia.Post post) {
        title = post.getTitle();
        ID = post.getId();
        userID = post.getUserId();
        description = post.getDescription();
        category = categoryFromString(post.getCategory());
        status = statusFromString(post.getStatus());
        timestamp = instantFromProtoTimestamp(post.getTimeStamp());
    }

    public Multimedia.Post toProto() {
        return Multimedia.Post.newBuilder()
                .setTitle(this.title)
                .setDescription(this.description)
                .setId(this.ID)
                .setUserId(this.userID)
                .setCategory(this.category.toString())
                .setStatus(this.status.toString())
                .setTimeStamp(this.protoTimestamp())
                .build();
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PostCategory getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = categoryFromString(category);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = Instant.parse(timestamp); }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = statusFromString(status);
    }

    private static PostCategory categoryFromString(String category) {
        return switch (category) {
            case "Health" -> PostCategory.Health;
            case "Workout" -> PostCategory.Workout;
            case "Medicine" -> PostCategory.Medicine;
            case "Food" -> PostCategory.Food;
            default -> null;
        };
    }

    private static PostStatus statusFromString(String status) {
        return switch (status) {
            case "Published" -> PostStatus.Published;
            case "Deleted" -> PostStatus.Deleted;
            case "Reported" -> PostStatus.Reported;
            default -> null;
        };
    }

    private Timestamp protoTimestamp() {
        return Timestamp.newBuilder()
                .setSeconds(timestamp.getEpochSecond())
                .setNanos(timestamp.getNano())
                .build();
    }

    private static Instant instantFromProtoTimestamp(Timestamp time) {
        return Instant.ofEpochSecond(time.getSeconds(), time.getNanos());
    }
}
