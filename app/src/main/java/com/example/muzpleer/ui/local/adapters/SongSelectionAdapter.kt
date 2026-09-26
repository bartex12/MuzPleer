package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemSongSelectionBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.formatDuration
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

//адаптер для песен, из которых будут выбираться песни для добавления в плейлист
class SongSelectionAdapter (
    private val sharedViewModel: SharedViewModel,
    private val onSelectionChanged: (List<Song>) -> Unit
) : RecyclerView.Adapter<SongSelectionAdapter.SongSelectionViewHolder>() {

    companion object{
        const val TAG = "33333"
    }

    var data:List<Song> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    private val selectedSongs = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongSelectionViewHolder {
        val binding = ItemSongSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongSelectionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: SongSelectionViewHolder,
        position: Int
    ) {
        val song = data[position]
        holder.bind(song, selectedSongs.contains(song.id))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    fun getSelectedSongs(): List<Song> {
        return data.filter { selectedSongs.contains(it.id) }
    }

    fun selectAll() {
        selectedSongs.clear()
        selectedSongs.addAll(data.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(getSelectedSongs())
    }

    inner class SongSelectionViewHolder(private val binding: ItemSongSelectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, isSelected: Boolean) {

            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist
            binding.songDuration.text = formatDuration(song.duration)


            song.artUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.songArtwork, song, binding)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.songArtwork, song, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.songArtwork, song, binding)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌SongSelectionAdapter exception when loading: ${e.message}")
                    binding.songArtwork.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.songArtwork.setImageResource(R.drawable.muz_player3)

            // Устанавливаем состояние чекбокса БЕЗ вызова слушателя
            binding.checkbox.setOnCheckedChangeListener(null) // Сначала удаляем слушатель
            binding.checkbox.isChecked = isSelected

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSongs.add(song.id)
                } else {
                    selectedSongs.remove(song.id)
                }
                onSelectionChanged(getSelectedSongs())
            }

            itemView.setOnClickListener {
                binding.checkbox.isChecked = !binding.checkbox.isChecked
            }
        }
    }

fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView,
                       song: Song, binding: ItemSongSelectionBinding){
    // Загрузка обложки
    Glide.with(context)
        .load(artUri)
        .placeholder(R.drawable.muz_player3)
        .error(R.drawable.muz_player3)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .addListener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: com.bumptech.glide.request.target.Target<Drawable?>,
                isFirstResource: Boolean
            ): Boolean {
                Log.d(TAG, "❌SongSelectionAdapter Glide load failed for" +
                        " song title = ${song.title} URI: $artUri")
                sharedViewModel.updateSongArtUri(binding.root.context, song)
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: com.bumptech.glide.request.target.Target<Drawable?>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                Log.d(TAG, "✅SongSelectionAdapter Glide load success for URI: $artUri")
                Log.d(TAG, "✅SongSelectionAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                return false
            }
        })
        .into(imageView)
}
}

