package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.Services.PlayerService;

public class RegisterFragment extends Fragment {

    private EditText emailInput;
    private EditText usernameInput;
    private EditText regionInput;
    private EditText passwordInput;
    private EditText repeatPasswordInput;
    private Button registerButton;
    private TextView loginText;

    private PlayerService playerService;

    public RegisterFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playerService = new PlayerService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register, container, false);

        emailInput = view.findViewById(R.id.emailInput);
        usernameInput = view.findViewById(R.id.usernameInput);
        regionInput = view.findViewById(R.id.regionInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        repeatPasswordInput = view.findViewById(R.id.repeatPasswordInput);
        registerButton = view.findViewById(R.id.registerButton);
        loginText = view.findViewById(R.id.loginText);

        registerButton.setOnClickListener(v -> registerPlayer());

        loginText.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack();
        });

        return view;
    }

    private void registerPlayer() {
        String email = emailInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String region = regionInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String repeatedPassword = repeatPasswordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(username)) {
            usernameInput.setError("Username is required");
            return;
        }

        if (TextUtils.isEmpty(region)) {
            regionInput.setError("Region is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }

        if (TextUtils.isEmpty(repeatedPassword)) {
            repeatPasswordInput.setError("Repeated password is required");
            return;
        }

        if (!password.equals(repeatedPassword)) {
            repeatPasswordInput.setError("Passwords do not match");
            return;
        }

        registerButton.setEnabled(false);

        playerService.registerPlayer(
                email,
                username,
                region,
                password,
                () -> {
                    registerButton.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            "Registration successful. Please verify your email before logging in.",
                            Toast.LENGTH_LONG
                    ).show();

                    requireActivity()
                            .getSupportFragmentManager()
                            .popBackStack();
                },
                errorMessage -> {
                    registerButton.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            errorMessage,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }
}