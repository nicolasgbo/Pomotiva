package com.ifpr.androidapptemplate

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.ifpr.androidapptemplate.databinding.ActivityMainBinding
import com.ifpr.androidapptemplate.R
import android.util.TypedValue
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.widget.TextView
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.graphics.Color
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var homeToolbarView: View? = null
    private var genericToolbarView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Controlamos manualmente os insets do sistema (status/navigation bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Aplica gradiente Pomodoro (#58b873 -> #8259f0) ao drawable do ImageView
        fun applyPomodoroGradientToImageView(imageView: ImageView) {
            val drawable = imageView.drawable ?: return
            val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: imageView.width)
            val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: imageView.height)
            if (width <= 0 || height <= 0) {
                imageView.post { applyPomodoroGradientToImageView(imageView) }
                return
            }

            val iconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val iconCanvas = Canvas(iconBitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(iconCanvas)

            val gradientBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val gradientCanvas = Canvas(gradientBitmap)
            val startHex = Color.parseColor("#58b873")
            val endHex = Color.parseColor("#8259f0")
            val shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(startHex, endHex),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            gradientCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Mascara o gradiente com o alfa do ícone
            paint.shader = null
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            gradientCanvas.drawBitmap(iconBitmap, 0f, 0f, paint)
            paint.xfermode = null

            // Evita que tint do ImageView sobrescreva o gradiente
            imageView.imageTintList = null
            imageView.setImageDrawable(BitmapDrawable(resources, gradientBitmap))
        }

        // Função para montar a toolbar da Home quando necessário
        fun attachHomeToolbar() {
            if (homeToolbarView != null) return
            supportActionBar?.apply {
                displayOptions = androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM
                setDisplayShowTitleEnabled(false)
                setDisplayShowCustomEnabled(true)
                setDisplayHomeAsUpEnabled(false)
                setHomeButtonEnabled(false)
                val params = androidx.appcompat.app.ActionBar.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = android.view.Gravity.START }
                val customView = layoutInflater.inflate(R.layout.toolbar_home, null)
                setCustomView(customView, params)
                homeToolbarView = customView

                // Gradiente no texto "Pomotiva"
                val brandText = customView.findViewById<TextView>(R.id.text_brand)
                brandText.post {
                    val text = brandText.text?.toString() ?: ""
                    val width = brandText.paint.measureText(text)
                    val startHex = Color.parseColor("#58b873")
                    val endHex = Color.parseColor("#8259f0")
                    val startOpaque = ColorUtils.setAlphaComponent(startHex, 255)
                    val endOpaque = ColorUtils.setAlphaComponent(endHex, 255)
                    val shader = LinearGradient(
                        0f, 0f, width, brandText.textSize,
                        intArrayOf(startOpaque, endOpaque),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    brandText.paint.shader = shader
                    brandText.invalidate()
                }

                // Insets de status bar/notch
                ViewCompat.setOnApplyWindowInsetsListener(customView) { v, insets ->
                    val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    v.updatePadding(top = sb.top + v.paddingTop)
                    insets
                }

                // Fundo e elevação
                val tv = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)
                val surfaceColor = if (tv.resourceId != 0) ContextCompat.getColor(this@MainActivity, tv.resourceId) else tv.data
                setBackgroundDrawable(ColorDrawable(surfaceColor))
                elevation = 0f

                // Zera insets da Toolbar interna
                var parent: View? = customView.parent as? View
                var actionToolbar: Toolbar? = null
                var safety = 0
                while (parent != null && safety < 10) {
                    if (parent is Toolbar) {
                        actionToolbar = parent
                        break
                    }
                    parent = parent.parent as? View
                    safety++
                }
                actionToolbar?.apply {
                    setContentInsetsAbsolute(0, 0)
                    setContentInsetsRelative(0, 0)
                    contentInsetStartWithNavigation = 0
                }

                // Aplica gradiente ao ícone de ajustes (Tune)
                (customView.findViewById<View>(R.id.btn_tune) as? ImageView)?.let { iv ->
                    applyPomodoroGradientToImageView(iv)
                }

                // Clique do botão Tune abre o diálogo de presets
                customView.findViewById<View>(R.id.btn_tune)?.setOnClickListener {
                    val fm = supportFragmentManager
                    val tag = "PomodoroPresetDialog"
                    if (fm.findFragmentByTag(tag) == null) {
                        com.ifpr.androidapptemplate.ui.pomodoro.PomodoroPresetDialogFragment().show(fm, tag)
                    }
                }
            }
        }

        fun detachHomeToolbar() {
            if (homeToolbarView == null) return
            supportActionBar?.apply {
                setDisplayShowCustomEnabled(false)
                displayOptions = androidx.appcompat.app.ActionBar.DISPLAY_SHOW_TITLE
                customView = null
            }
            homeToolbarView = null
        }

        // Toolbar genérica para outras telas, reutilizando o layout da Home
        fun attachGenericToolbar(title: String, iconRes: Int, showTune: Boolean = false) {
            // Se já existe, remonta com novos dados
            supportActionBar?.apply {
                displayOptions = androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM
                setDisplayShowTitleEnabled(false)
                setDisplayShowCustomEnabled(true)
                setDisplayHomeAsUpEnabled(false)
                setHomeButtonEnabled(false)
                val params = androidx.appcompat.app.ActionBar.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = android.view.Gravity.START }
                val customView = layoutInflater.inflate(R.layout.toolbar_home, null)
                setCustomView(customView, params)
                genericToolbarView = customView

                val brandText = customView.findViewById<TextView>(R.id.text_brand)
                val logo = customView.findViewById<ImageView>(R.id.img_logo)
                brandText.text = title
                logo.setImageResource(iconRes)

                // Aplica gradiente ao ícone da esquerda (logo usado como ícone da tela)
                logo.post { applyPomodoroGradientToImageView(logo) }

                // Gradiente no título
                brandText.post {
                    val text = brandText.text?.toString() ?: ""
                    val width = brandText.paint.measureText(text)
                    val startHex = Color.parseColor("#58b873")
                    val endHex = Color.parseColor("#8259f0")
                    val startOpaque = ColorUtils.setAlphaComponent(startHex, 255)
                    val endOpaque = ColorUtils.setAlphaComponent(endHex, 255)
                    val shader = LinearGradient(
                        0f, 0f, width, brandText.textSize,
                        intArrayOf(startOpaque, endOpaque),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    brandText.paint.shader = shader
                    brandText.invalidate()
                }

                // Insets de status bar/notch
                ViewCompat.setOnApplyWindowInsetsListener(customView) { v, insets ->
                    val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    v.updatePadding(top = sb.top + v.paddingTop)
                    insets
                }

                // Fundo e elevação
                val tv = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)
                val surfaceColor = if (tv.resourceId != 0) ContextCompat.getColor(this@MainActivity, tv.resourceId) else tv.data
                setBackgroundDrawable(ColorDrawable(surfaceColor))
                elevation = 0f

                // Zera insets da Toolbar interna
                var parent: View? = customView.parent as? View
                var actionToolbar: Toolbar? = null
                var safety = 0
                while (parent != null && safety < 10) {
                    if (parent is Toolbar) {
                        actionToolbar = parent
                        break
                    }
                    parent = parent.parent as? View
                    safety++
                }
                actionToolbar?.apply {
                    setContentInsetsAbsolute(0, 0)
                    setContentInsetsRelative(0, 0)
                    contentInsetStartWithNavigation = 0
                }

                // Botão de ajustes visível/apagado conforme parâmetro
                val tune = customView.findViewById<View>(R.id.btn_tune)
                tune?.visibility = if (showTune) View.VISIBLE else View.GONE
                if (showTune) {
                    (tune as? ImageView)?.post { applyPomodoroGradientToImageView(tune) }
                }
            }
        }

        fun detachGenericToolbar() {
            if (genericToolbarView == null) return
            supportActionBar?.apply {
                setDisplayShowCustomEnabled(false)
                displayOptions = androidx.appcompat.app.ActionBar.DISPLAY_SHOW_TITLE
                customView = null
            }
            genericToolbarView = null
        }

        val navView: BottomNavigationView = binding.navView

        // Aplica WindowInsets ao BottomNavigationView para respeitar a navigation bar
        val originalBottomPadding = navView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(navView) { v, insets ->
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = originalBottomPadding + nb.bottom)
            insets
        }

        // Opcional: aplicar insets de bordas laterais ao container raiz
        val root = binding.container
        val originalLeft = root.paddingLeft
        val originalRight = root.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = originalLeft + sb.left, right = originalRight + sb.right)
            insets
        }

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_estatistica, R.id.navigation_metas,
                R.id.navigation_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Mostrar a toolbar custom na Home e replicar nas demais telas com títulos/ícones
        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.show()
            when (destination.id) {
                R.id.navigation_home -> {
                    detachGenericToolbar()
                    attachHomeToolbar()
                }
                R.id.navigation_metas -> {
                    detachHomeToolbar()
                    attachGenericToolbar(title = getString(R.string.title_notifications), iconRes = R.drawable.ic_goals_black_24dp, showTune = false)
                }
                R.id.navigation_estatistica -> {
                    detachHomeToolbar()
                    attachGenericToolbar(title = getString(R.string.title_estatistica), iconRes = R.drawable.ic_finance_24, showTune = false)
                }
                R.id.navigation_profile -> {
                    detachHomeToolbar()
                    attachGenericToolbar(title = getString(R.string.title_profile), iconRes = R.drawable.ic_profile_black_24dp, showTune = false)
                }
                else -> {
                    detachHomeToolbar()
                    attachGenericToolbar(title = getString(R.string.app_name), iconRes = R.drawable.pomotiva_logo, showTune = false)
                }
            }
        }
    }
}