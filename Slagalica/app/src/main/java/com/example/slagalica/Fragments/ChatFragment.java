package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Adapters.ChatMessageAdapter;
import com.example.slagalica.Model.ChatMessage;
import com.example.slagalica.R;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatFragment extends Fragment {
    private final Map<String, ChatMessage> messagesById = new LinkedHashMap<>();
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle chatListenerHandle;
    private ChatMessageAdapter adapter;
    private TextView titleText;
    private TextView stateText;
    private ListView messagesList;
    private EditText messageInput;
    private Button sendButton;
    private String currentUid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webSocketGameClient = WebSocketGameClient.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = user == null ? null : user.getUid();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        titleText = view.findViewById(R.id.chatTitle);
        stateText = view.findViewById(R.id.chatStateText);
        messagesList = view.findViewById(R.id.chatMessagesList);
        messageInput = view.findViewById(R.id.chatMessageInput);
        sendButton = view.findViewById(R.id.chatSendButton);

        adapter = new ChatMessageAdapter(requireContext(), currentUid);
        messagesList.setAdapter(adapter);
        messagesList.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        menuButton.setOnClickListener(v -> ((MainActivity) requireActivity()).toggleNavbar());
        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        registerChatListener();
        connectAndLoadChat();
        return view;
    }

    @Override
    public void onDestroyView() {
        if (chatListenerHandle != null) {
            chatListenerHandle.remove();
            chatListenerHandle = null;
        }
        super.onDestroyView();
    }

    private void registerChatListener() {
        chatListenerHandle = webSocketGameClient.addRegionChatListener(new WebSocketGameClient.OnRegionChatListener() {
            @Override
            public void onMessage(ChatMessage message) {
                addOrReplaceMessage(message);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded()) {
                    stateText.setText(R.string.chat_connection_failed);
                }
            }
        });
    }

    private void connectAndLoadChat() {
        if (currentUid == null) {
            stateText.setText(R.string.chat_login_required);
            sendButton.setEnabled(false);
            return;
        }
        sendButton.setEnabled(false);
        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
        webSocketGameClient.connect(currentUid, new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
                webSocketGameClient.getRegionChat(new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isAdded()) {
                            return;
                        }
                        String regionName = data.optString("regionName", getString(R.string.chat_region_fallback));
                        titleText.setText(getString(R.string.chat_region_title, regionName));
                        mergeHistory(data.optJSONArray("messages"));
                        stateText.setText(messagesById.isEmpty()
                                ? R.string.chat_empty
                                : R.string.chat_realtime_status);
                        sendButton.setEnabled(true);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showFailure(errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                showFailure(errorMessage);
            }
        });
    }

    private void mergeHistory(JSONArray messages) {
        if (messages != null) {
            for (int index = 0; index < messages.length(); index++) {
                ChatMessage message = ChatMessage.fromJson(messages.optJSONObject(index));
                if (message != null) {
                    messagesById.put(message.getMessageId(), message);
                }
            }
        }
        renderMessages();
    }

    private void addOrReplaceMessage(ChatMessage message) {
        if (!isAdded() || message == null) {
            return;
        }
        messagesById.put(message.getMessageId(), message);
        stateText.setText(R.string.chat_realtime_status);
        renderMessages();
    }

    private void renderMessages() {
        List<ChatMessage> messages = new ArrayList<>(messagesById.values());
        messages.sort(Comparator.comparingLong(ChatMessage::getSentAtMs));
        adapter.setMessages(messages);
        if (!messages.isEmpty()) {
            messagesList.post(() -> messagesList.setSelection(adapter.getCount() - 1));
        }
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        sendButton.setEnabled(false);
        webSocketGameClient.sendRegionChatMessage(text, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                ChatMessage message = ChatMessage.fromJson(data);
                if (message != null) {
                    addOrReplaceMessage(message);
                }
                messageInput.setText("");
                sendButton.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMessage) {
                sendButton.setEnabled(true);
                showFailure(errorMessage);
            }
        });
    }

    private void showFailure(String errorMessage) {
        if (!isAdded()) {
            return;
        }
        stateText.setText(R.string.chat_connection_failed);
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
    }
}
