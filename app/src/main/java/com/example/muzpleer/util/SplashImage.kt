package com.example.muzpleer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Описание картинки экрана загрузки: какая выбрана и какие свои картинки загружены (до трёх). */
data class SplashSpec(
    val mode: Int = SplashImage.MODE_DEFAULT,
    val photo: String? = null,
    val photos: List<String> = emptyList()
)

/** Картинка экрана загрузки: стандартная, своя или та же, что фон приложения. */
object SplashImage {
    const val MODE_DEFAULT = 0     //стандартная картинка из приложения
    const val MODE_CUSTOM = 1      //своя картинка из списка
    const val MODE_BACKGROUND = 2  //та же картинка, что фон приложения (если фон - фото)
    const val MAX_PHOTOS = 3
    const val PHOTO_PREFIX = "splash_photo_"
    private const val PREFS = "splash_image_prefs"

    fun load(context: Context): SplashSpec {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val photos = (p.getString("photos", null)?.split("|")?.filter { it.isNotEmpty() && File(it).exists() }
            ?: emptyList()).takeLast(MAX_PHOTOS)
        var photo = p.getString("photo", null)
        if (photo != null && photo !in photos) photo = null
        var mode = p.getInt("mode", MODE_DEFAULT)
        if (mode == MODE_CUSTOM && photo == null) mode = MODE_DEFAULT
        return SplashSpec(mode, photo, photos)
    }

    /** Сохраняем выбор и удаляем файлы картинок, которых больше нет в списке. */
    fun save(context: Context, spec: SplashSpec) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("mode", spec.mode)
            .putString("photo", spec.photo)
            .putString("photos", spec.photos.joinToString("|"))
            .apply()
        context.filesDir.listFiles()?.forEach {
            if (it.name.startsWith(PHOTO_PREFIX) && it.absolutePath !in spec.photos) it.delete()
        }
    }

    /** Добавляем новую картинку: если их уже три, самая старая вытесняется. */
    fun withNewPhoto(spec: SplashSpec, path: String): SplashSpec {
        val list = (spec.photos + path).takeLast(MAX_PHOTOS)
        return SplashSpec(MODE_CUSTOM, path, list)
    }

    /** Фон приложения сейчас - фото? Тогда можно предложить его для экрана загрузки. */
    fun backgroundPhotoPath(context: Context): String? {
        val bg = AppBackground.load(context)
        return if (bg.type == AppBackground.TYPE_PHOTO) bg.photo else null
    }

    /** Путь к картинке для показа или null, если надо показать стандартную. */
    fun resolvePath(context: Context, spec: SplashSpec = load(context)): String? = when (spec.mode) {
        MODE_CUSTOM -> spec.photo
        MODE_BACKGROUND -> backgroundPhotoPath(context)
        else -> null
    }

    /** Раскодированная картинка, уменьшенная примерно до размера [reqW] x [reqH]. */
    fun decode(path: String, reqW: Int, reqH: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) { null }
}
