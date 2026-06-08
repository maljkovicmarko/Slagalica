package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link NavbarFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NavbarFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public NavbarFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment NavbarFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static NavbarFragment newInstance(String param1, String param2) {
        NavbarFragment fragment = new NavbarFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_navbar, container, false);

        view.findViewById(R.id.nav_home).setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).toggleNavbar();
        });

        view.findViewById(R.id.nav_profile).setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).toggleNavbar();
        });

        view.findViewById(R.id.nav_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new LoginFragment())
                    .commit();

            ((MainActivity) requireActivity()).toggleNavbar();
        });

        return view;
    }

}