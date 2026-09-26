package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemArtistBinding
import com.example.muzpleer.model.Artist
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperAdapter
import com.example.muzpleer.ui.local.frags.CoverChangeLevelFragment.LevelType
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getAlbumsCountString
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File
import java.util.Collections

class ArtistsAdapter(
    private val viewModel: SharedViewModel,
    private val onItemClick: (Artist) -> Unit
) : RecyclerView.Adapter<ArtistsAdapter.ArtistViewHolder>(),
    ItemTouchHelperAdapter {

    var artists:List<Artist> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper // Добавляем ссылку
    // Временный список для перетаскивания
    private val dragData = mutableListOf<Artist>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ArtistViewHolder,
        position: Int
    ) {
        holder.bind(artists[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedArtistPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount(): Int {
        return artists.size
    }

    inner class ArtistViewHolder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(artist: Artist) {
            binding.tvArtistName.text = artist.name
            binding.tvTracksCount.text =buildString {
                append("${getAlbumsCountString(artist.albums.size)}, ")
                append(getTracksCountString(artist.songs.size))
            }

            artist.artworkUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.ivArtistArtwork, artist.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateArtistArtUri(binding.root.context, artist)
                        }
                        Log.d(TAG, "✅ ArtistsAdapter Glide load success for URI: $artUri")
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.ivArtistArtwork, artist.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateArtistArtUri(binding.root.context, artist)
                        }
                        Log.d(TAG, "✅✅ ArtistsAdapter Glide load success for URI: $artUri")
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.ivArtistArtwork, artist.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateArtistArtUri(binding.root.context, artist)
                        }
                        Log.d(TAG, "✅✅✅ ArtistsAdapter Glide load success for URI: $artUri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ArtistsAdapter exception when loading: ${e.message}")
                    binding.ivArtistArtwork.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.ivArtistArtwork.setImageResource(R.drawable.muz_player3)

            binding.artistMenuButton.setOnClickListener { view ->
                showPopupMenu(view, artist)
            }

            binding.root.setOnClickListener {
                viewModel.setSelectedArtistPosition(absoluteAdapterPosition)
                onItemClick(artist)
            }

            // Показываем/скрываем иконку перетаскивания в режиме редактирования
            if (isEditMode) {
                binding.artistDragHandle.visibility = View.VISIBLE
                binding.artistMenuButton.visibility = View.GONE
                // Добавляем слушатель касаний для иконки перетаскивания
                binding.artistDragHandle.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Запускаем перетаскивание при нажатии на иконку
                        itemTouchHelper.startDrag(this)
                    }
                    false
                }
            } else {
                binding.artistDragHandle.visibility = View.GONE
                binding.artistMenuButton.visibility = View.VISIBLE
                binding.artistDragHandle.setOnTouchListener(null) // Убираем слушатель
            }
        }
    }

    private fun showPopupMenu(view: View, artist: Artist) {
        val context = view.context
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.level_item_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_cover_level -> {  //изменить обложку плейлиста
                    navigateToChangeLevelCover(view, artist)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun navigateToChangeLevelCover(view: View,  artist: Artist) {
        Log.d(TAG, "-=- ArtistsAdapter navigateToChangeLevelCover: " +
                "artist = ${artist.name} id = ${artist.id} artworkUri = ${artist.artworkUri.toString()}")
        viewModel.setCurrentArtist(artist)
        val bundle: Bundle  = Bundle().apply{
            putLong("levelId", artist.id)
            putSerializable("levelType", LevelType.ARTIST)
        }
        view.findNavController().navigate(R.id.coverChangeLevelFragment, bundle)
    }
    fun setEditMode(enable: Boolean) {
        isEditMode = enable
        if (enable) {
            // Инициализируем временный список
            dragData.clear()
            dragData.addAll(artists)
        }
        notifyDataSetChanged() // Перерисовываем для показа/скрытия иконки перетаскивания
    }

    // Метод для установки ItemTouchHelper
    fun setItemTouchHelper(helper: ItemTouchHelper) {
        itemTouchHelper = helper
    }

    //пошли методы от интерфейса ItemTouchHelperAdapter
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Работаем с временным списком
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(dragData, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(dragData, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)

        return true
    }

    override fun onDrop() {
        // Сохраняем окончательный порядок, только если были изменения
        if (dragData.isNotEmpty() && dragData != artists) {
            artists = dragData.toList()
            viewModel.updateArtistsOrder(artists)
            Log.d(TAG, " !! ArtistsAdapter onDrop() - порядок сохранён")
        } else {
            Log.d(TAG, " !! ArtistsAdapter onDrop() - изменений нет")
        }
    }

    override fun onItemDismiss(position: Int) {
        // Не используется, но должен быть реализован
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView, title:String, onError:((Exception?)->Unit)? = null){
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
                    Log.e(TAG, " ❌ ArtistsAdapter Glide load failed for title $title   URI: $artUri", e)
                    onError?.invoke(e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅ ArtistsAdapter Glide load success for title = $title URI: $artUri")
                    Log.d(TAG, "✅ ArtistsAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}