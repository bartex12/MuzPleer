package com.example.muzpleer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.muzpleer.R
import java.io.File
import kotlin.math.max

/** Описание фона приложения: что выбрано и какое фото загружено последним. */
data class BgSpec(
    val type: Int = AppBackground.TYPE_DEFAULT,
    val color1: Int = 0,
    val color2: Int = 0,
    val photo: String? = null,            //выбранное фото (для типа TYPE_PHOTO)
    val photos: List<String> = emptyList() //загруженные фото, от старого к новому, не больше MAX_PHOTOS
)

/** Фон за всеми экранами: стандартный градиент, один цвет, градиент из двух цветов или своё фото. */
object AppBackground {
    const val TYPE_DEFAULT = 0
    const val TYPE_SOLID = 1
    const val TYPE_GRADIENT = 2
    const val TYPE_PHOTO = 3
    const val MAX_PHOTOS = 3

    private const val PREFS = "theme_prefs"
    private const val PHOTO_PREFIX = "bg_photo_"
    //затемнение поверх фото, чтобы белый текст читался на светлых снимках
    private const val PHOTO_SCRIM_ALPHA = 110

    //последнее раскодированное фото: при быстром переборе плиток не раскодируем файл заново
    private val cache = HashMap<String, Bitmap>()

    /** Выбор, ещё не применённый: живёт, пока открыт экран «Сменить тему». */
    var pending: BgSpec? = null

    val solidColors = intArrayOf(
        0xFF1A1A2E.toInt(), 0xFF0F2A3F.toInt(), 0xFF12343B.toInt(), 0xFF1B3A2E.toInt(),
        0xFF3B2A1F.toInt(), 0xFF5C1F1F.toInt(), 0xFF3A1F4D.toInt(), 0xFF2B2B2B.toInt(),
        0xFF000000.toInt(), 0xFF4A4A4A.toInt(), 0xFF6E6D6D.toInt(), 0xFF17B8B1.toInt()
    )

    val gradientColors = arrayOf(
        intArrayOf(0xFF1A1A2E.toInt(), 0xFF3A4A8F.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF42275A.toInt(), 0xFF734B6D.toInt()),
        intArrayOf(0xFF6A1B9A.toInt(), 0xFFAB47BC.toInt()),
        intArrayOf(0xFF8E1D2B.toInt(), 0xFFD9503F.toInt()),
        intArrayOf(0xFF8E2D6B.toInt(), 0xFFE0607E.toInt()),
        intArrayOf(0xFF1B5E20.toInt(), 0xFF4CAF50.toInt()),
        intArrayOf(0xFF004D40.toInt(), 0xFF26A69A.toInt()),
        intArrayOf(0xFF0D47A1.toInt(), 0xFF42A5F5.toInt()),
        intArrayOf(0xFFE65100.toInt(), 0xFFFFA726.toInt()),
        intArrayOf(0xFF212121.toInt(), 0xFF757575.toInt()),
        intArrayOf(0xFF000000.toInt(), 0xFF434343.toInt())
    )

    fun load(context: Context): BgSpec {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val photos = (p.getString("bg_photos", null)?.split("|")?.filter { it.isNotEmpty() } ?: emptyList())
            .toMutableList()
        //раньше хранилось одно фото в ключе bg_photo
        val old = p.getString("bg_photo", null)
        if (old != null && old !in photos) photos.add(old)
        val existing = photos.filter { File(it).exists() }.takeLast(MAX_PHOTOS)
        var photo = p.getString("bg_photo", null)
        if (photo != null && photo !in existing) photo = null
        var type = p.getInt("bg_type", TYPE_DEFAULT)
        if (type == TYPE_PHOTO && photo == null) type = TYPE_DEFAULT
        return BgSpec(type, p.getInt("bg_color1", 0), p.getInt("bg_color2", 0), photo, existing)
    }

    /** Сохраняем выбор и убираем файлы фото, которых больше нет в списке. */
    fun save(context: Context, spec: BgSpec) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("bg_type", spec.type)
            .putInt("bg_color1", spec.color1)
            .putInt("bg_color2", spec.color2)
            .putString("bg_photo", spec.photo)
            .putString("bg_photos", spec.photos.joinToString("|"))
            .apply()
        context.filesDir.listFiles()?.forEach {
            if (it.name.startsWith(PHOTO_PREFIX) && it.absolutePath !in spec.photos) it.delete()
        }
        cache.clear()
    }

    /** Добавляем новое фото: если их уже MAX_PHOTOS, самое старое вытесняется (файл удалится при сохранении). */
    fun withNewPhoto(spec: BgSpec, path: String): BgSpec {
        val list = (spec.photos + path).takeLast(MAX_PHOTOS)
        return spec.copy(type = TYPE_PHOTO, photo = path, photos = list)
    }

    /** Показываем выбранный фон прямо сейчас в окне приложения (до подтверждения кнопкой «Применить»). */
    fun applyToWindow(activity: android.app.Activity, spec: BgSpec) {
        activity.window.setBackgroundDrawable(drawable(activity, spec))
    }

    /** Фон, как он выглядит для выбранного описания. */
    fun drawable(context: Context, spec: BgSpec): Drawable = when {
        spec.type == TYPE_SOLID -> GradientDrawable().apply { setColor(spec.color1) }
        spec.type == TYPE_GRADIENT -> GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(spec.color1, spec.color2)
        )
        spec.type == TYPE_PHOTO && spec.photo != null -> {
            val bitmap = cache[spec.photo] ?: BitmapFactory.decodeFile(spec.photo)?.also {
                cache.clear()
                cache[spec.photo] = it
            }
            if (bitmap != null) CoverPhotoDrawable(bitmap, PHOTO_SCRIM_ALPHA)
            else ContextCompat.getDrawable(context, R.drawable.item_list_effects_revers)!!
        }
        else -> ContextCompat.getDrawable(context, R.drawable.item_list_effects_revers)!!
    }

    /** Копия выбранного снимка в память приложения, уменьшенная и с учётом поворота. Вызывать не в основном потоке. */
    fun importPhoto(context: Context, uri: Uri, prefix: String = PHOTO_PREFIX): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val bitmap = if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            } else decoded
            val file = File(context.filesDir, "$prefix${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

/** Фото на весь экран: масштабируется с обрезкой по краям, сверху лёгкое затемнение. */
class CoverPhotoDrawable(private val bitmap: Bitmap, scrimAlpha: Int) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrim = Paint().apply { color = 0; alpha = 0 }
    private val matrix = Matrix()
    private val scrimValue = scrimAlpha

    init {
        scrim.color = android.graphics.Color.argb(scrimValue, 0, 0, 0)
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val scale = max(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        val dx = (bounds.width() - bitmap.width * scale) / 2f + bounds.left
        val dy = (bounds.height() - bitmap.height * scale) / 2f + bounds.top
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, matrix, paint)
        canvas.drawRect(bounds, scrim)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
