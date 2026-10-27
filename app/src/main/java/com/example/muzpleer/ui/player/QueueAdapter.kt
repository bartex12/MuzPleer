package com.example.muzpleer.ui.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.muzpleer.databinding.ItemQueueBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperAdapter
import com.example.muzpleer.util.formatDuration

/**
 * Очередь воспроизведения: песни в порядке очереди, играющая выделена, строки перетаскиваются за значок справа.
 * [onMoved] - песню переставили (номера в очереди), [onDropped] - перетаскивание закончено, [onClick] - выбрана строка.
 */
class QueueAdapter(
    private val onClick: (Int) -> Unit,
    private val onMoved: (from: Int, to: Int) -> Unit,
    private val onDropped: (List<Song>) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>(), ItemTouchHelperAdapter {

    private val items = mutableListOf<Song>()
    private var current = -1
    var currentIndex: Int
        get() = current
        set(value) {
            val old = current
            current = value
            if (old in items.indices) notifyItemChanged(old)
            if (value in items.indices) notifyItemChanged(value)
        }
    var touchHelper: ItemTouchHelper? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(songs: List<Song>, current: Int) {
        items.clear()
        items.addAll(songs)
        currentIndex = current
        notifyDataSetChanged()
    }

    fun songs(): List<Song> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = QueueViewHolder(
        ItemQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) = holder.bind(items[position], position)

    @SuppressLint("ClickableViewAccessibility")
    inner class QueueViewHolder(private val binding: ItemQueueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song, position: Int) {
            binding.tvQueueNumber.text = (position + 1).toString()
            binding.tvQueueTitle.text = song.title
            binding.tvQueueArtist.text = song.artist
            binding.tvQueueDuration.text = song.duration.formatDuration()
            //играющая песня подсвечена
            val playing = position == currentIndex
            binding.root.setBackgroundColor(if (playing) 0x33FFFFFF else Color.TRANSPARENT)
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(pos)
            }
            binding.ivQueueDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(this)
                false
            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) return false
        items.add(toPosition, items.removeAt(fromPosition))
        //номер играющей песни едет вместе с ней
        current = when (current) {
            fromPosition -> toPosition
            in (minOf(fromPosition, toPosition))..(maxOf(fromPosition, toPosition)) ->
                if (fromPosition < toPosition) current - 1 else current + 1
            else -> current
        }
        notifyItemMoved(fromPosition, toPosition)
        onMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemDismiss(position: Int) {}

    override fun onDrop() {
        refreshNumbers()
        onDropped(songs())
    }

    //после перетаскивания обновляем номера строк
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshNumbers() = notifyDataSetChanged()
}
