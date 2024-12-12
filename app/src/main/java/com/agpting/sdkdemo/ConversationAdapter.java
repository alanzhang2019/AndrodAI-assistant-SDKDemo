package com.agpting.sdkdemo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class ConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<ConversationItem> items;

    public ConversationAdapter(List<ConversationItem> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ConversationItem.TYPE_PROMPT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_message, parent, false);
            return new PromptViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
            return new ViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConversationItem item = items.get(position);
        
        if (holder instanceof ViewHolder) {
            ViewHolder messageHolder = (ViewHolder) holder;
            messageHolder.userMessageContainer.setVisibility(View.GONE);
            messageHolder.assistantMessageContainer.setVisibility(View.GONE);
            
            if (item.getType() == ConversationItem.TYPE_USER) {
                messageHolder.userMessageContainer.setVisibility(View.VISIBLE);
                messageHolder.userMessageText.setText(item.getMessage());
            } else {  // 助手消息和系统消息都显示在左侧
                messageHolder.assistantMessageContainer.setVisibility(View.VISIBLE);
                messageHolder.assistantMessageText.setText(item.getMessage());
            }
        } else if (holder instanceof PromptViewHolder) {
            ((PromptViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItem(ConversationItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout userMessageContainer;
        LinearLayout assistantMessageContainer;
        TextView userMessageText;
        TextView assistantMessageText;

        ViewHolder(View itemView) {
            super(itemView);
            userMessageContainer = itemView.findViewById(R.id.userMessageContainer);
            assistantMessageContainer = itemView.findViewById(R.id.assistantMessageContainer);
            userMessageText = itemView.findViewById(R.id.userMessageText);
            assistantMessageText = itemView.findViewById(R.id.assistantMessageText);
        }
    }

    static class PromptViewHolder extends RecyclerView.ViewHolder {
        TextView customMessageText;

        PromptViewHolder(View itemView) {
            super(itemView);
            customMessageText = itemView.findViewById(R.id.customMessageText);
        }

        void bind(ConversationItem item) {
            customMessageText.setText(item.getMessage());
        }
    }
}