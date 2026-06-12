package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Model.SystemNotification;
import com.example.slagalica.R;
import com.example.slagalica.Services.NotificationService;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private ImageButton menuButton;

    private Button allNotificationsButton;
    private Button unreadNotificationsButton;
    private Button readNotificationsButton;
    private Button dummyNotificationsButton;

    private ListView notificationsListView;

    private NotificationService notificationService;
    private List<SystemNotification> currentNotifications;

    public NotificationsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationService = new NotificationService();
        currentNotifications = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        menuButton = view.findViewById(R.id.menuButton);

        allNotificationsButton = view.findViewById(R.id.allNotificationsButton);
        unreadNotificationsButton = view.findViewById(R.id.unreadNotificationsButton);
        readNotificationsButton = view.findViewById(R.id.readNotificationsButton);
        dummyNotificationsButton = view.findViewById(R.id.dummyNotificationsButton);

        notificationsListView = view.findViewById(R.id.notificationsListView);

        menuButton.setVisibility(View.VISIBLE);

        menuButton.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).toggleNavbar();
        });

        allNotificationsButton.setOnClickListener(v -> loadNotifications("ALL"));
        unreadNotificationsButton.setOnClickListener(v -> loadNotifications("UNREAD"));
        readNotificationsButton.setOnClickListener(v -> loadNotifications("READ"));

        dummyNotificationsButton.setOnClickListener(v -> {
            notificationService.createDummyNotifications(
                    () -> {
                        Toast.makeText(requireContext(), "Dummy notifications created", Toast.LENGTH_SHORT).show();
                        loadNotifications("ALL");
                    },
                    error -> Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            );
        });

        notificationsListView.setOnItemClickListener((parent, itemView, position, id) -> {
            SystemNotification notification = currentNotifications.get(position);

            notificationService.markAsRead(
                    notification.getId(),
                    () -> {
                        Toast.makeText(requireContext(), "Marked as read", Toast.LENGTH_SHORT).show();
                        loadNotifications("ALL");
                    },
                    error -> Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            );
        });

        loadNotifications("ALL");

        return view;
    }

    private void loadNotifications(String filter) {
        notificationService.getNotifications(
                filter,
                notifications -> {
                    currentNotifications = notifications;

                    List<String> displayItems = new ArrayList<>();

                    for (SystemNotification notification : notifications) {
                        displayItems.add(notification.getDisplayText());
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            displayItems
                    );

                    notificationsListView.setAdapter(adapter);
                },
                error -> Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
        );
    }
}