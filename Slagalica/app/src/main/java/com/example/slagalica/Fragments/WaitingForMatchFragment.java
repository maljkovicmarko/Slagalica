package com.example.slagalica.Fragments;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.slagalica.R;

public class WaitingForMatchFragment extends DialogFragment {

    public interface OnCancelSearchListener {
        void onCancelSearch();
    }

    public static final String TAG = "WaitingForMatchFragment";

    private OnCancelSearchListener onCancelSearchListener;

    public WaitingForMatchFragment() {
        // Required empty public constructor
    }

    public static WaitingForMatchFragment newInstance() {
        return new WaitingForMatchFragment();
    }

    public void setOnCancelSearchListener(OnCancelSearchListener listener) {
        this.onCancelSearchListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_waiting_for_match, container, false);

        Button cancelButton = view.findViewById(R.id.cancelMatchmakingButton);
        cancelButton.setOnClickListener(v -> {
            if (onCancelSearchListener != null) {
                onCancelSearchListener.onCancelSearch();
            }
            dismissAllowingStateLoss();
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }

        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.45f;

        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }
}
