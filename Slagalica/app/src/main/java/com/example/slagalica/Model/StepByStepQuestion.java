package com.example.slagalica.Model;

import java.util.List;

public class StepByStepQuestion {
    private String answer;
    private List<String> steps;

    public StepByStepQuestion(String answer, List<String> steps) {
        this.answer = answer;
        this.steps = steps;
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getSteps() {
        return steps;
    }
}