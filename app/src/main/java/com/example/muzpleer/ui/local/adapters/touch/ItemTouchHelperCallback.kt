package com.example.muzpleer.ui.local.adapters.touch

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ItemTouchHelperCallback(private val adapter: ItemTouchHelperAdapter) :
    ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Разрешаем перемещение только вверх и вниз
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0 // Отключаем свайпы
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Перемещаем элемент
        adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Не используется
    }

    override fun isLongPressDragEnabled(): Boolean {
        return false // Отключаем длинное нажатие для перетаскивания
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false // Отключаем свайпы
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        // Изменяем внешний вид при перетаскивании
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.apply {
                alpha = 0.5f
                elevation = 8f
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        adapter.onDrop()

        // Восстанавливаем внешний вид после перетаскивания
        viewHolder.itemView.apply {
            alpha = 1.0f
            elevation = 0f
        }
    }
}