package com.example.slagalica.Model;

import java.util.List;

public class FindTheNumberRound {
    private final int targetNumber;
    private final List<Integer> numbers;

    public FindTheNumberRound(int targetNumber, List<Integer> numbers) {
        this.targetNumber = targetNumber;
        this.numbers = numbers;
    }

    public int getTargetNumber() {
        return targetNumber;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }
}