package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Model.Region;
import com.example.slagalica.R;
import com.example.slagalica.Services.PlayerService;
import com.example.slagalica.Services.RegionService;

import java.util.ArrayList;
import java.util.List;

public class RegisterFragment extends Fragment {

    private EditText emailInput;
    private EditText usernameInput;
    private Spinner regionSpinner;
    private EditText passwordInput;
    private EditText repeatPasswordInput;
    private Button registerButton;
    private TextView loginText;

    private PlayerService playerService;
    private RegionService regionService;
    private final List<Region> regions = new ArrayList<>();

    public RegisterFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playerService = new PlayerService();
        regionService = new RegionService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_register, container, false);

        emailInput = view.findViewById(R.id.emailInput);
        usernameInput = view.findViewById(R.id.usernameInput);
        regionSpinner = view.findViewById(R.id.regionSpinner);
        passwordInput = view.findViewById(R.id.passwordInput);
        repeatPasswordInput = view.findViewById(R.id.repeatPasswordInput);
        registerButton = view.findViewById(R.id.registerButton);
        loginText = view.findViewById(R.id.loginText);

        registerButton.setOnClickListener(v -> registerPlayer());
        registerButton.setEnabled(false);
        loadRegions();

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
        String password = passwordInput.getText().toString().trim();
        String repeatedPassword = repeatPasswordInput.getText().toString().trim();
        Region selectedRegion = selectedRegion();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(username)) {
            usernameInput.setError("Username is required");
            return;
        }

        if (selectedRegion == null) {
            Toast.makeText(requireContext(), R.string.region_required, Toast.LENGTH_LONG).show();
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
                selectedRegion,
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

    private void loadRegions() {
        regionService.loadActiveRegions(
                loadedRegions -> {
                    if (!isAdded()) {
                        return;
                    }
                    regions.clear();
                    regions.addAll(loadedRegions);

                    ArrayAdapter<Region> adapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            regions
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    regionSpinner.setAdapter(adapter);
                    registerButton.setEnabled(!regions.isEmpty());
                },
                errorMessage -> {
                    if (!isAdded()) {
                        return;
                    }
                    registerButton.setEnabled(false);
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
        );
    }

    private Region selectedRegion() {
        Object selectedItem = regionSpinner.getSelectedItem();
        if (selectedItem instanceof Region) {
            return (Region) selectedItem;
        }
        return null;
    }
}
