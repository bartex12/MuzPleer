package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemAlbumSelectionBinding
import com.example.muzpleer.model.Folder
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class FoldersSelectionAdapter (
    private val viewModel: SharedViewModel,
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FoldersSelectionAdapter.AlbumViewHolder>() {

    var folders:List<Folder> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(folders[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedAlbumPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount() = folders.size

    inner class AlbumViewHolder(private val binding: ItemAlbumSelectionBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder) {
            binding.albumTitleSelection.text = folder.name
            binding.albumArtistSelection.text = folder.path
            binding.tracksCountSelection.text = getTracksCountString( folder.songs.size)

            folder.artworkUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.albumArtSelection, folder, binding)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.albumArtSelection, folder, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.albumArtSelection, folder, binding)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌FoldersSelectionAdapter exception when loading: ${e.message}")
                    binding.albumArtSelection.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.albumArtSelection.setImageResource(R.drawable.muz_player3)

            itemView.setOnClickListener {
                viewModel.setSelectedAlbumPosition(absoluteAdapterPosition)
                onFolderClick(folder)
            }
        }
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView,
                           folder:Folder, binding: ItemAlbumSelectionBinding){
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
                    Log.d(TAG, "❌FoldersSelectionAdapter Glide load failed for" +
                            " folder Name = ${folder.name} URI: $artUri")
                    viewModel.updateFolderArtUri(binding.root.context, folder)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅FoldersSelectionAdapter Glide load success for URI: $artUri")
                    Log.d(TAG, "✅FoldersSelectionAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}