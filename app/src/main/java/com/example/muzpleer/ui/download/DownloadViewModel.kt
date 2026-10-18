package com.example.muzpleer.ui.download

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Состояние загрузки трека. */
sealed class DownloadState {
    object Idle : DownloadState()
    /** [percent] 0..100, -1 если размер файла неизвестен. */
    data class Loading(val percent: Int, val title: String?) : DownloadState()
    /** Файл скачан; [playlistId] - плейлист, в который его надо добавить (если выбран). */
    data class Done(val track: DownloadedTrack, val playlistId: Long?) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Скачивание трека по ссылке. ViewModel общая на Activity, поэтому загрузка не прерывается,
 * если пользователь ушёл с экрана; результат (Done/Error) ждёт, пока экран его обработает.
 */
class DownloadViewModel(context: Context) : ViewModel() {

    private val downloader = TrackDownloader(context.applicationContext)

    private val _state = MutableLiveData<DownloadState>(DownloadState.Idle)
    val state: LiveData<DownloadState> = _state

    private var job: Job? = null

    fun isBusy() = job?.isActive == true

    fun start(url: String, playlistId: Long?) {
        if (isBusy()) return
        _state.value = DownloadState.Loading(-1, null)
        job = viewModelScope.launch {
            try {
                val track = downloader.resolve(url)
                _state.postValue(DownloadState.Loading(-1, track.title))
                val saved = downloader.download(track) { percent ->
                    _state.postValue(DownloadState.Loading(percent, track.title))
                }
                _state.value = DownloadState.Done(saved, playlistId)
            } catch (e: CancellationException) {
                _state.value = DownloadState.Idle
                throw e
            } catch (e: DownloadException) {
                _state.value = DownloadState.Error(e.message ?: "Не удалось скачать трек")
            } catch (e: Exception) {
                Log.d(TAG, "DownloadViewModel start error = $e")
                _state.value = DownloadState.Error("Не удалось скачать трек: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** Результат обработан экраном - возвращаемся в исходное состояние. */
    fun reset() { if (!isBusy()) _state.value = DownloadState.Idle }

    companion object { const val TAG = "33333" }
}
