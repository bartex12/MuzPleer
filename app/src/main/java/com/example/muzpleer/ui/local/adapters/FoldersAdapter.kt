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
import com.example.muzpleer.databinding.ItemFolderBinding
import com.example.muzpleer.model.Folder
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperAdapter
import com.example.muzpleer.ui.local.frags.CoverChangeLevelFragment.LevelType
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File
import java.util.Collections

class FoldersAdapter(
    private val viewModel: SharedViewModel,
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FoldersAdapter.FolderViewHolder>(),
    ItemTouchHelperAdapter {

    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper // Добавляем ссылку
    // Временный список для перетаскивания
    private val dragData = mutableListOf<Folder>()

    var folders:List<Folder> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folders[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedFolderPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount() = folders.size

    inner class FolderViewHolder(private val binding: ItemFolderBinding)
        : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(folder: Folder) {
            binding.tvFolderName.text = folder.name
            binding.tvFolderPath.text = folder.path
            binding.tvTracksCount.text = getTracksCountString(folder.songs.size)

            folder.artworkUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.ivFolderIcon, folder.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateFolderArtUri(binding.root.context, folder)
                        }
                        Log.d(TAG, "✅ FoldersAdapter bind Glide load success for folder = ${folder.name} URI: $artUri")
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.ivFolderIcon, folder.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateFolderArtUri(binding.root.context, folder)
                        }
                        Log.d(TAG, "✅✅ FoldersAdapter bind Glide load success for folder = ${folder.name} URI: $artUri")
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.ivFolderIcon, folder.name){
                            // При ошибке сбрасываем в БД
                            viewModel.updateFolderArtUri(binding.root.context, folder)
                        }
                        Log.d(TAG, "✅✅✅ FoldersAdapter bind Glide load success for folder = ${folder.name} URI: $artUri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FoldersAdapter exception when loading  folder name = ${folder.name}   ${e.message}")
                    binding.ivFolderIcon.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.ivFolderIcon.setImageResource(R.drawable.muz_player3)


            binding.folderMenuButton.setOnClickListener { view ->
                showPopupMenu(view, folder)
            }

            itemView.setOnClickListener {
                viewModel.setSelectedFolderPosition(absoluteAdapterPosition)
                onFolderClick(folder)
            }
            // Показываем/скрываем иконку перетаскивания в режиме редактирования
            if (isEditMode) {
                binding.folderDragHandle.visibility = View.VISIBLE
                binding.folderMenuButton.visibility = View.GONE
                // Добавляем слушатель касаний для иконки перетаскивания
                binding.folderDragHandle.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Запускаем перетаскивание при нажатии на иконку
                        itemTouchHelper.startDrag(this)
                    }
                    false
                }
            } else {
                binding.folderDragHandle.visibility = View.GONE
                binding.folderMenuButton.visibility = View.VISIBLE
                binding.folderDragHandle.setOnTouchListener(null) // Убираем слушатель
            }
        }
    }

    private fun showPopupMenu(view: View, folder: Folder) {
        val context = view.context
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.level_item_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_cover_level -> {  //изменить обложку плейлиста
                    navigateToChangeLevelCover(view, folder)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun navigateToChangeLevelCover(view: View,  folder: Folder) {
        viewModel.setCurrentFolderByFolderPath(folder.path)
        val bundle: Bundle  = Bundle().apply{
            putLong("levelId", folder.id)
            putSerializable("levelType", LevelType.FOLDER)
        }
        view.findNavController().navigate(R.id.coverChangeLevelFragment, bundle)
    }

    fun setEditMode(enable: Boolean) {
        isEditMode = enable
        if (enable) {
            // Инициализируем временный список
            dragData.clear()
            dragData.addAll(folders)
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
        // Сохраняем окончательный порядок
        folders = dragData.toList()
        viewModel.updateFoldersOrder(folders)
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
                    Log.e(TAG, " ❌ FoldersAdapter Glide load failed for title =$title URI: $artUri", e)
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
                    Log.d(TAG, "✅ FoldersAdapter Glide load success for title =$title  URI: $artUri")
                    Log.d(TAG, "✅ FoldersAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}