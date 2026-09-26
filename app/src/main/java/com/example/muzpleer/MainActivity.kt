package com.example.muzpleer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.databinding.ActivityMainBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.TabLocalFragment
import com.example.muzpleer.ui.local.frags.SongListFolderFragment
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.ui.local.helper.PreferenceHelperImpl
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import com.example.muzpleer.util.toast
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var doubleBackToExitPressedOnce = false
    private lateinit var navController:NavController
    private val viewModel: SharedViewModel by viewModel()

    private lateinit var playerLayout: ConstraintLayout
    private lateinit var title: AppCompatTextView
    private lateinit var artist: TextView
    private lateinit var artWork: ImageView
    private lateinit var previous: ImageView
    private lateinit var playPause: ImageView
    private lateinit var next: ImageView
    private lateinit var rewindBack: ImageView
    private lateinit var rewindForward: ImageView
    private lateinit var tvTotalTimeMain: TextView
    private lateinit var tvCurrentTimeMain: TextView
    private lateinit var seekBarMain:SeekBar

    private lateinit var appPreferences: IPreferenceHelper
    private var currentSong: Song? = null
    private var viewPager: ViewPager? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scanForMusic()
        } else {
            handlePermissionDenied()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermission()) {
            scanForMusic()
        } else {
            handlePermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Установить заставку ДО setContentView
        val splashScreen = installSplashScreen()
        // Установить основную тему (переключаемся на обычную тему после заставки)
        setTheme(R.style.Theme_MuzPleer_NoActionBar)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navController = findNavController(R.id.nav_host_fragment_content_main)

        appPreferences =PreferenceHelperImpl(this.application)
        // Находим ViewPager через findViewById
        viewPager = findViewById(R.id.view_pager_local)

        //получаем разрешения
        checkPermissions()
    }

    private fun scanForMusic() {

        initViews()
        startMediaScan()

        //поддержка экшенбара
        setSupportActionBar(binding.appBarMain.toolbar)
        //отключаем показ заголовка тулбара, так как там свой макет с main_title
        supportActionBar?.setDisplayShowTitleEnabled(false)
        //текстовое поле в тулбаре
        with(binding.appBarMain.toolbar.findViewById<TextView>(R.id.main_title)){
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.tabLocalFragment,  R.id.settingsFragment), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        //автоматически связывает пункты меню с destinations в NavGraph
        navView.setupWithNavController(navController)
        //слушатель меню шторки - нужен для дополнительной кастомной логики
        navView.setNavigationItemSelectedListener(object : NavigationView.OnNavigationItemSelectedListener{
            override fun onNavigationItemSelected(item: MenuItem): Boolean {
                // Выделяем выбранный пункт меню в шторке
                //item.isChecked = true
                binding.drawerLayout.closeDrawer(GravityCompat.START)

                return when (item.itemId) {
                    R.id.nav_favorites -> {
                        Log.d(TAG, "MainActivity onNavigationItemSelected nav_favorites")
                        navController.navigate(R.id.favoritesFragment)
                        //viewPager?.setCurrentItem(4)
                        true
                    }
                    R.id.nav_setting -> {
                        Log.d(TAG, "MainActivity onNavigationItemSelected nav_setting")
                        navController.navigate(R.id.settingsFragment)
                        true
                    }
                    R.id.nav_help ->{
                        Log.d(TAG, "MainActivity onNavigationItemSelected nav_help")
                        //navController.navigate(R.id.helpFragment)
                        true
                    }
                    R.id.nav_share -> {
                        Log.d(TAG, "MainActivity onNavigationItemSelected nav_share")
                        //поделиться - передаём ссылку на приложение в маркете
                        shareApp()
                        toast(getString(R.string.stub))
                        true
                    }
                    R.id.nav_rate -> {
                        Log.d(TAG, "MainActivity onNavigationItemSelected nav_send")
                        //оценить приложение - попадаем на страницу приложения в маркете
                        rateApp()
                        toast(getString(R.string.stub))
                        true
                    }
                    else -> false
                }
            }

        })

        viewModel.currentPosition.observe(this) { position ->
            tvCurrentTimeMain.text = formatTime(position)
            seekBarMain.progress = position.toInt()
        }

        viewModel.duration.observe(this) { duration ->
            tvTotalTimeMain.text = formatTime(duration)
            seekBarMain.max = duration.toInt()
        }

        viewModel.isPlaying.observe(this) { isPlaying ->
            playPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white
            )
        }

        viewModel.songAndPlaylist.observe(this) { songAndPlaylist ->
            //находим индекс трека в плейлисте
            val indexOfTrack =
                songAndPlaylist.playlist.indexOfFirst { it.mediaUri == songAndPlaylist.song.mediaUri }

            Log.d(TAG, "###MainActivity scanForMusic " +
                    "indexOfTrack = $indexOfTrack " +
                    "songAndPlaylist.playlist.size = ${songAndPlaylist.playlist.size}" +
                    " currentSong title= ${songAndPlaylist.song.title} ")

            viewModel.setPlaylistForHandler(songAndPlaylist.playlist, indexOfTrack)
        }

        viewModel.currentSong.observe(this) {songCurrent->
            currentSong = songCurrent

            songCurrent?. let {
                title.text=songCurrent.title
                artist.text=songCurrent.artist
            }

            songCurrent?.artUri?.let{artUri->
                Log.d(TAG,"### MainActivity currentSong.observe uri = $artUri")
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri)){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, artWork)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, artWork)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri), artWork)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ MainActivity exception when loading: ${e.message}")
                    //при ошибке грузим картинку ошибки
                    artWork.setImageResource(R.drawable.muz_player3)
                }
            }?: artWork.setImageResource(R.drawable.muz_player3)  //если artUri = null
        }
           //управление видимостью нижнего плеера из фрагмента:
            viewModel.playerVisibility.observe(this) { isVisible ->
                playerLayout.visibility = if (isVisible) View.VISIBLE else View.GONE
            }

        viewModel.coverImageUri.observe(this) { uri ->
            val selectedSong = viewModel.getSelectedSong()
            val currentSong = viewModel.getCurrentSong()
            if (currentSong?.id == selectedSong?.id){
                // Загрузка обложки
                currentSong?.artUri?. let{artUri->
                    currentSong.artUri= uri.toString()
                    Log.d(TAG, "3*** MainActivity coverImageUri.observe " +
                            "currentSong.artUri =  ${currentSong.artUri} title = ${currentSong.title}")

                    // Загружаем изображение
                    try {
                        if (isContentProviderUri(artUri)){
                            // Загрузка обложки из  content:/com.android.providers.downloads
                            showImageWithGlide(binding.root.context, artUri, artWork)
                        }else  if (isContentProviderUriPicker(artUri.toString())){
                            val  photoPickerUri =artUri.toString().toUri()
                            showImageWithGlide(binding.root.context, photoPickerUri, artWork)
                        }else{
                            // Загрузка обложки из кэша приложения
                            showImageWithGlide(binding.root.context, File(artUri), artWork)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌Security exception when loading: ${e.message}")
                        //при ошибке грузим картинку ошибки
                        artWork.setImageResource(R.drawable.muz_player3)
                    }
                }?: artWork.setImageResource(R.drawable.muz_player3)  //если artUri = null
            }
        }

        viewModel.appBarTitle.observe(this){title->
           binding.appBarMain.mainTitle.text = title
        }

       //initMenu() нельзя - иначе двоится меню тулбара
    }
    fun updateToolbarTitle(title: String) {
        Log.d(TAG, "### ### MainActivity updateToolbarTitle title = $title ")
        binding.appBarMain.mainTitle.text = title
    }

    fun getCurrentTitle(): String {
        return binding.appBarMain.mainTitle.text.toString()
    }

    // Для сброса к заголовку вкладки
    fun   resetToTabTitle() {
        // Получаем текущий фрагмент из NavController
        val currentFragment = navController.currentDestination?.let { destination ->
            when (destination.id) {
                R.id.tabLocalFragment -> {
                    // Получаем TabLocalFragment из NavHost
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                    val childFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                    (childFragment as? TabLocalFragment)
                }
                else -> null
            }
        }
        if (currentFragment is TabLocalFragment) {
            val currentPosition = currentFragment.viewPager.currentItem
            val tabTitle = when (currentPosition) {
                0 -> "Песни"
                1 -> "Папки"
                2 -> "Избранное"
                3 -> "Плейлисты"
                4 -> "Альбомы"
                5 -> "Исполнители"
                else -> "Музыка на ладони"
            }
            binding.appBarMain.mainTitle.text = tabTitle
        } else {
            // Если не на вкладках, ставим стандартный заголовок
            binding.appBarMain.mainTitle.text = "Музыка на ладони"
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun initViews() {
        playerLayout = binding.appBarMain.contentMain.playerBottom

        title=binding.appBarMain.contentMain.title
        artist=binding.appBarMain.contentMain.artist
        artWork=binding.appBarMain.contentMain.artwork
        previous=binding.appBarMain.contentMain.previous
        playPause=binding.appBarMain.contentMain.playPause
        next=binding.appBarMain.contentMain.next
        rewindBack = binding.appBarMain.contentMain.rewindBack
        rewindForward = binding.appBarMain.contentMain.rewindForward
        seekBarMain = binding.appBarMain.contentMain.seekBarMain
        tvCurrentTimeMain = binding.appBarMain.contentMain.tvCurrentTimeMain
        tvTotalTimeMain = binding.appBarMain.contentMain.tvTotalTimeMain

        previous.setOnClickListener { viewModel.playPrevious() }
        playPause.setOnClickListener { viewModel.togglePlayPause() }
        next.setOnClickListener { viewModel.playNext() }
        rewindBack.setOnClickListener {viewModel.seekRelative(-5000) } // -5 секунд
        rewindForward.setOnClickListener {  viewModel.seekRelative(15000) } // +15 секунд

        artWork.setOnClickListener { navController.navigate(R.id.playerFragment) }
        title.setOnClickListener { navController.navigate(R.id.playerFragment) }
        artist.setOnClickListener { navController.navigate(R.id.playerFragment) }

        ///перемещение прогресса в нижнем плеере
        seekBarMain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun startMediaScan() {

        viewModel.scanMedia(){
            //сначала сканируем телефон и собираем все музыкальные треки в базе, а потом делаем другие вкладки
            viewModel.syncAlbums()
            viewModel.syncArtist ()
            viewModel.syncFolders()
            viewModel.loadPlaylists()

            // Восстанавливаем последнюю песню
            val savedSongId = appPreferences.getCurrentSongId()
            if (savedSongId != -1L) {
                viewModel.setCurrentSongById(savedSongId)
            }

            //Обновляем обложки принудительно очень медленно грузится всё//todo
            //viewModel.refreshArtworks()

            //восстанавливаем заголовок тулбара
            resetToTabTitle()
            Log.d(TAG, "###MainActivity startMediaScan savedSongId = $savedSongId CurrentSong =  ${ viewModel.getCurrentSong()?.title}")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStop() {
        super.onStop()
        // Сохраняем текущую песню при закрытии
        currentSong?.let { appPreferences.saveCurrentSongId(it.id)  }
        //сохраняем индекс текущей песни в списке вкладки песен
        appPreferences.saveIndexOfCurrentSong(viewModel.getIndexOfCurrentSong())
        Log.d(TAG, "###MainActivity onStop " +
                "currentSong id =  ${currentSong?.id} индекс = ${viewModel.getIndexOfCurrentSong()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "###MainActivity onDestroy currentSong id =  ${currentSong?.id} индекс = ${viewModel.getIndexOfCurrentSong()}")
    }

    private fun checkPermissions() {
        when {
            hasStoragePermission() -> {
                scanForMusic()
            }
            shouldShowRequestPermissionRationale(getRequiredPermission()) -> {
                showPermissionRationale()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager() -> {
                requestManageExternalStorage()
            }
            else -> {
                requestStoragePermission()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun requestStoragePermission() {
        storagePermissionLauncher.launch(getRequiredPermission())
    }

    private fun requestManageExternalStorage() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            manageStorageLauncher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageStorageLauncher.launch(intent)
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Для поиска музыкальных треков приложению нужен доступ к вашим аудиофайлам")
            .setPositiveButton("Разрешить") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestManageExternalStorage()
                } else {
                    requestStoragePermission()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun handlePermissionDenied() {
        if (!shouldShowRequestPermissionRationale(getRequiredPermission())) {
            showOpenSettingsDialog()
        } else {
            Toast.makeText(
                this,
                "Разрешение отклонено. Функционал ограничен",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showOpenSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Вы запретили доступ к медиафайлам. Хотите открыть настройки и предоставить разрешение?")
            .setPositiveButton("Настройки") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    //при нажатии на кнопку Назад если фрагмент реализует BackButtonListener, вызываем метод backPressed
    //при этом если мы в homeFragment   - выходим из приложения по двойному щелчку,
    // а если в другом экране - делаем то, что там прописано
    override fun onBackPressed() {
        //если мы в homeFragment, то при нажатии Назад показываем Snackbar и при повторном
        //нажати в течении 2 секунд закрываем приложение
        Log.d(TAG,"MainActivity onBackPressed  Destination = ${navController.currentDestination?.label}")
        if( navController.currentDestination?.id  == R.id.tabLocalFragment){
            Log.d(TAG, "MainActivity onBackPressed  это TabLocalFragment")
            //если флаг = true - а это при двойном щелчке - закрываем программу
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed()
                return
            }
            doubleBackToExitPressedOnce = true //выставляем флаг = true
            //закрываем шторку, если была открыта
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            //показываем Snackbar: Для выхода нажмите  НАЗАД  ещё раз
            Snackbar.make(
                findViewById(android.R.id.content), this.getString(R.string.forExit),
                Snackbar.LENGTH_SHORT
            ).show()
            //запускаем поток, в котором через 2 секунды меняем флаг
            Handler(Looper.getMainLooper())
                .postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
        }else{
            Log.d(TAG, "MainActivity onBackPressed  это НЕ TabLocalFragment ")
            super.onBackPressed()
            // После возврата нельзя делать resetToTabTitle() - всё сделано
            // через запоминание предыдущего заголовка
        }
    }

    companion object{
        const val TAG = "33333"
    }

    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT,
                    "Скачайте крутой музыкальный плеер: https://play.google.com/store/apps/details?id=${packageName}")
            }
            startActivity(Intent.createChooser(shareIntent, "Поделиться приложением"))

        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось поделиться приложением", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Ошибка при分享приложения: ${e.message}")
        }
    }

    private fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data ="market://details?id=$packageName".toUri()
                setPackage("com.android.vending") // Направляем в Google Play
            }

            startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            // Если Google Play не установлен, открываем в браузере
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data ="https://play.google.com/store/apps/details?id=$packageName".toUri()
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть страницу оценки", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при открытии магазина", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Ошибка при открытии магазина: ${e.message}")
        }
    }


    fun showImageWithGlide(context:Context, artUri:Any, imageView: ImageView){
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
                    Log.e(TAG, " ❌ Glide load failed in MainActivity for URI: $artUri", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅Glide load success in MainActivity  for URI: $artUri")
                    Log.d(TAG, "✅ DataSource in MainActivity : $dataSource target = ${target.toString()}") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}
