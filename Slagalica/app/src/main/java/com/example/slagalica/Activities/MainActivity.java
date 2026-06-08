package com.example.slagalica.Activities;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.Fragments.LoginFragment;
import com.example.slagalica.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new LoginFragment())
                    .commit();
        }

        View overlay = findViewById(R.id.navbarOverlay);
        overlay.setOnClickListener(v -> {
            System.out.println("Overlay clicked");
            toggleNavbar();
        });
    }

    public void toggleNavbar() {
        View navbar = findViewById(R.id.navbarFragment);
        View overlay = findViewById(R.id.navbarOverlay);

        System.out.println("Navbar visibility: " + navbar.getVisibility());
        System.out.println("Overlay visibility: " + overlay.getVisibility());

        if (navbar.getVisibility() == View.VISIBLE) {
            navbar.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        } else {
            overlay.setVisibility(View.VISIBLE);
            navbar.setVisibility(View.VISIBLE);

            overlay.bringToFront();
            navbar.bringToFront();
        }
    }
}