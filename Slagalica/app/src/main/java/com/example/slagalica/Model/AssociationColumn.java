package com.example.slagalica.Model;

import java.util.List;

public class AssociationColumn {
    private List<String> clues;
    private String solution;

    public AssociationColumn() {
    }

    public AssociationColumn(List<String> clues, String solution) {
        this.clues = clues;
        this.solution = solution;
    }

    public List<String> getClues() {
        return clues;
    }

    public void setClues(List<String> clues) {
        this.clues = clues;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }
}