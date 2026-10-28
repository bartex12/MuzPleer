package com.example.muzpleer.util

import androidx.lifecycle.MutableLiveData

/** Общее состояние «музыка сейчас играет» для анимации эквалайзера в строках списков. */
object NowPlaying {
    val isPlaying = MutableLiveData(false)
}
