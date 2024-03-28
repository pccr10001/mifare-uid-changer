package li.power.app.mifareuidchanger;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import li.power.app.mifareuidchanger.model.UidItem;

import java.util.ArrayList;

public class UidAdapter extends RecyclerView.Adapter<UidAdapter.ViewHolder> {

    ArrayList<UidItem> itemList;

    public UidAdapter(ArrayList<UidItem> itemList) {
        this.itemList = itemList;
    }

    private OnClickListener onClickListener;
    private OnClickListener onLongClickListener;


    class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvItem;
        public ViewHolder(View holder) {
            super(holder);
            tvItem = holder.findViewById(R.id.uidView);
        }
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setOnLongClickListener(OnClickListener onClickListener) {
        this.onLongClickListener = onClickListener;
    }

    public interface OnClickListener {
        void onClick(int position, UidItem item);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.uid_item, parent, false);    //載入layout
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        UidItem item = itemList.get(position);
        holder.tvItem.setText(item.getId()+": "+item.getName());
        holder.tvItem.setLongClickable(true);
        holder.tvItem.setOnLongClickListener(view -> {
            if (onLongClickListener != null) {
                onLongClickListener.onClick(holder.getAdapterPosition(), item);
            }
            return true;
        });

        holder.tvItem.setOnClickListener(view -> {
            if (onClickListener != null) {
                onClickListener.onClick(holder.getAdapterPosition(), item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }
}
