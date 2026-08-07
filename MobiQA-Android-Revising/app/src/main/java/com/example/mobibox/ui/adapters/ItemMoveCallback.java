package com.example.mobibox.ui.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class ItemMoveCallback extends ItemTouchHelper.Callback {

    private final ItemTouchHelperContract mAdapter;

    public ItemMoveCallback(ItemTouchHelperContract adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true; // 允许长按拖拽
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true; // 允许滑动
    }

    @Override
    // ItemMoveCallback.java
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // 水平列表允许左右拖动排序；启用“上滑删除”
        int dragFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT|ItemTouchHelper.UP|ItemTouchHelper.DOWN; // 左右拖动排序
        int swipeFlags = 0;
        return makeMovementFlags(dragFlags, swipeFlags);
    }
    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        mAdapter.onRowMoved(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        mAdapter.onRowSwiped(viewHolder.getAdapterPosition());
    }

    // 定义一个接口，让Adapter实现这些方法
    public interface ItemTouchHelperContract {
        void onRowMoved(int fromPosition, int toPosition);
        void onRowSwiped(int position);
    }
}