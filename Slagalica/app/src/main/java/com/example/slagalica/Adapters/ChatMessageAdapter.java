package com.example.slagalica.Adapters;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.slagalica.Model.ChatMessage;
import com.example.slagalica.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ChatMessageAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final String currentUid;
    private final SimpleDateFormat dateTimeFormat;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatMessageAdapter(Context context, String currentUid) {
        inflater = LayoutInflater.from(context);
        this.currentUid = currentUid;
        dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy. HH:mm", new Locale("sr", "RS"));
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public ChatMessage getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getMessageId().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_chat_message, parent, false);
            holder = new ViewHolder(
                    convertView.findViewById(R.id.chatBubble),
                    convertView.findViewById(R.id.chatSenderAndTime),
                    convertView.findViewById(R.id.chatMessageText)
            );
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ChatMessage message = getItem(position);
        boolean ownMessage = currentUid != null && currentUid.equals(message.getSenderUid());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.bubble.getLayoutParams();
        params.gravity = ownMessage ? Gravity.END : Gravity.START;
        holder.bubble.setLayoutParams(params);
        holder.bubble.setBackgroundResource(ownMessage
                ? R.drawable.chat_bubble_own
                : R.drawable.chat_bubble_other);
        holder.senderAndTime.setText(holder.senderAndTime.getContext().getString(
                R.string.chat_sender_and_time,
                message.getSenderName(),
                dateTimeFormat.format(new Date(message.getSentAtMs()))
        ));
        holder.messageText.setText(message.getText());
        return convertView;
    }

    private static final class ViewHolder {
        private final LinearLayout bubble;
        private final TextView senderAndTime;
        private final TextView messageText;

        private ViewHolder(LinearLayout bubble, TextView senderAndTime, TextView messageText) {
            this.bubble = bubble;
            this.senderAndTime = senderAndTime;
            this.messageText = messageText;
        }
    }
}
