package com.example.slagalica.Model;

public class ConnectingPair {
    private String left;
    private String right;

    public ConnectingPair(String left, String right) {
        this.left = left;
        this.right = right;
    }

    public ConnectingPair() {

    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
    }
}
