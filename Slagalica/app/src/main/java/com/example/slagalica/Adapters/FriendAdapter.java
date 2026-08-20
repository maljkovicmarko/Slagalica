package com.example.slagalica.Adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.slagalica.Model.Player;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendAdapter extends BaseAdapter {

    public interface OnAddFriendClickListener {
        void onAddFriend(Player player);
    }

    public interface OnChallengeFriendClickListener {
        void onChallengeFriend(Player player);
    }

    private final LayoutInflater inflater;
    private final boolean showAddButton;
    private final OnAddFriendClickListener addFriendClickListener;
    private final List<Player> players = new ArrayList<>();
    private final Map<String, Boolean> availabilityByPlayerId = new HashMap<>();
    private OnChallengeFriendClickListener challengeFriendClickListener;
    private String pendingInviteFriendId;

    public FriendAdapter(LayoutInflater inflater,
                         boolean showAddButton,
                         OnAddFriendClickListener addFriendClickListener) {
        this.inflater = inflater;
        this.showAddButton = showAddButton;
        this.addFriendClickListener = addFriendClickListener;
    }

    public void submitList(List<Player> newPlayers) {
        players.clear();
        if (newPlayers != null) {
            players.addAll(newPlayers);
        }
        notifyDataSetChanged();
    }

    public void submitAvailability(Map<String, Boolean> availability) {
        availabilityByPlayerId.clear();
        if (availability != null) {
            availabilityByPlayerId.putAll(availability);
        }
        notifyDataSetChanged();
    }

    public void setChallengeFriendClickListener(OnChallengeFriendClickListener listener) {
        challengeFriendClickListener = listener;
    }

    public void setPendingInviteFriendId(String friendId) {
        pendingInviteFriendId = friendId;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return players.size();
    }

    @Override
    public Player getItem(int position) {
        return players.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_friend, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Player player = getItem(position);
        holder.username.setText(player.getUsername());
        holder.monthlyRank.setText(player.getMonthlyRank() > 0
                ? parent.getContext().getString(R.string.friend_monthly_rank_value, player.getMonthlyRank())
                : parent.getContext().getString(R.string.friend_monthly_rank_unavailable));
        holder.stars.setText(parent.getContext().getString(R.string.friend_stars_value, player.getTotalStars()));
        holder.league.setText(player.getLeagueName() == null || player.getLeagueName().isBlank()
                ? parent.getContext().getString(R.string.no_league)
                : player.getLeagueName());
        displayAvatar(holder.avatar, player.getAvatarBase64());

        holder.addButton.setVisibility(showAddButton ? View.VISIBLE : View.GONE);
        holder.addButton.setEnabled(true);
        holder.addButton.setOnClickListener(view -> {
            if (addFriendClickListener != null) {
                holder.addButton.setEnabled(false);
                addFriendClickListener.onAddFriend(player);
            }
        });

        boolean showChallengeButton = !showAddButton && challengeFriendClickListener != null;
        boolean isPendingInvite = player.getId() != null && player.getId().equals(pendingInviteFriendId);
        boolean isAvailable = Boolean.TRUE.equals(availabilityByPlayerId.get(player.getId()));
        holder.challengeButton.setVisibility(showChallengeButton ? View.VISIBLE : View.GONE);
        holder.challengeButton.setText(isPendingInvite
                ? parent.getContext().getString(R.string.cancel_friend_invite)
                : parent.getContext().getString(R.string.challenge_friend));
        holder.challengeButton.setEnabled(isPendingInvite || isAvailable);
        holder.challengeButton.setOnClickListener(view -> {
            if (challengeFriendClickListener != null) {
                challengeFriendClickListener.onChallengeFriend(player);
            }
        });
        return convertView;
    }

    private void displayAvatar(ImageView imageView, String avatarBase64) {
        if (avatarBase64 == null || avatarBase64.isBlank()) {
            imageView.setImageResource(R.mipmap.ic_launcher);
            return;
        }
        try {
            byte[] bytes = Base64.decode(avatarBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            imageView.setImageBitmap(bitmap);
        } catch (RuntimeException error) {
            imageView.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private static final class ViewHolder {
        private final ImageView avatar;
        private final TextView username;
        private final TextView monthlyRank;
        private final TextView stars;
        private final TextView league;
        private final Button addButton;
        private final Button challengeButton;

        private ViewHolder(View view) {
            avatar = view.findViewById(R.id.friendAvatarImage);
            username = view.findViewById(R.id.friendUsernameText);
            monthlyRank = view.findViewById(R.id.friendMonthlyRankText);
            stars = view.findViewById(R.id.friendStarsText);
            league = view.findViewById(R.id.friendLeagueText);
            addButton = view.findViewById(R.id.addFriendButton);
            challengeButton = view.findViewById(R.id.challengeFriendButton);
        }
    }
}
