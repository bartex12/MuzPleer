package com.example.muzpleer.ui.local.adapters.touch

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onItemDismiss(position: Int)
    fun onDrop() // Новый метод для завершения перетаскивания
}