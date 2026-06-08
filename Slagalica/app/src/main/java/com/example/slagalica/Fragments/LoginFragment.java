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

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link LoginFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LoginFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private PlayerService playerService;
    private EditText usernameOrEmailInput;
    private EditText passwordInput;
    private TextView registerText;
    private Button loginButton;

    public LoginFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment LoginFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static LoginFragment newInstance(String param1, String param2) {
        LoginFragment fragment = new LoginFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playerService = new PlayerService();
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        usernameOrEmailInput = view.findViewById(R.id.usernameOrEmailInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        loginButton = view.findViewById(R.id.loginButton);
        registerText = view.findViewById(R.id.registerText);

        registerText.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new RegisterFragment())
                    .addToBackStack(null)
                    .commit();
        });

        loginButton.setOnClickListener(v -> loginPlayer());
        return view;
    }

    private void loginPlayer() {

        String emailOrUsername =
                usernameOrEmailInput.getText().toString().trim();

        String password =
                passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(emailOrUsername)) {
            usernameOrEmailInput.setError("Username or email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }

        loginButton.setEnabled(false);

        playerService.loginPlayer(
                emailOrUsername,
                password,
                () -> {
                    loginButton.setEnabled(true);

                    requireActivity()
                            .getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, new HomeFragment())
                            .commit();
                },
                errorMessage -> {
                    loginButton.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            errorMessage,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }
}