package com.example.slagalica.Model;

public class Player {
    private String id;
    private String email;
    private String username;
    private String region;

    public Player(){

    }

    public Player(String id, String email, String username, String region){
        this.id = id;
        this.email = email;
        this.username = username;
        this.region = region;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
