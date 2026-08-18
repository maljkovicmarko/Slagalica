package com.example.slagalica.wsserver;

public final class PhaseTypes {
    public static final String GENERAL_KNOWLEDGE_QUESTION_OPEN = "general_knowledge_question_open";
    public static final String GENERAL_KNOWLEDGE_ANSWER_REVEAL = "general_knowledge_answer_reveal";
    public static final String CONNECTIONS_OWNER_ATTEMPT = "connections_owner_attempt";
    public static final String CONNECTIONS_OPPONENT_ATTEMPT = "connections_opponent_attempt";
    public static final String ASSOCIATIONS_ROUND_ACTIVE = "associations_round_active";
    public static final String ASSOCIATIONS_FINAL_REVEAL = "associations_final_reveal";
    public static final String GUESS_COMBINATION_OWNER_ATTEMPT = "guess_combination_owner_attempt";
    public static final String GUESS_COMBINATION_OPPONENT_ATTEMPT = "guess_combination_opponent_attempt";
    public static final String STEP_BY_STEP_OWNER_STEP = "step_by_step_owner_step";
    public static final String STEP_BY_STEP_OPPONENT_ATTEMPT = "step_by_step_opponent_attempt";
    public static final String FIND_NUMBER_TARGET_STOP = "find_number_target_stop";
    public static final String FIND_NUMBER_NUMBERS_STOP = "find_number_numbers_stop";
    public static final String FIND_NUMBER_SOLVE = "find_number_solve";

    private PhaseTypes() {
    }
}
