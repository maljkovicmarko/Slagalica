package com.example.slagalica.Model;

public class Association {
    private AssociationColumn columnA;
    private AssociationColumn columnB;
    private AssociationColumn columnC;
    private AssociationColumn columnD;
    private String finalSolution;

    public Association() {
    }

    public Association(AssociationColumn columnA,
                       AssociationColumn columnB,
                       AssociationColumn columnC,
                       AssociationColumn columnD,
                       String finalSolution) {
        this.columnA = columnA;
        this.columnB = columnB;
        this.columnC = columnC;
        this.columnD = columnD;
        this.finalSolution = finalSolution;
    }

    public AssociationColumn getColumnA() {
        return columnA;
    }

    public void setColumnA(AssociationColumn columnA) {
        this.columnA = columnA;
    }

    public AssociationColumn getColumnB() {
        return columnB;
    }

    public void setColumnB(AssociationColumn columnB) {
        this.columnB = columnB;
    }

    public AssociationColumn getColumnC() {
        return columnC;
    }

    public void setColumnC(AssociationColumn columnC) {
        this.columnC = columnC;
    }

    public AssociationColumn getColumnD() {
        return columnD;
    }

    public void setColumnD(AssociationColumn columnD) {
        this.columnD = columnD;
    }

    public String getFinalSolution() {
        return finalSolution;
    }

    public void setFinalSolution(String finalSolution) {
        this.finalSolution = finalSolution;
    }
}