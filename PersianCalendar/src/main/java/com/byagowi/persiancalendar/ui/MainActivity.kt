package com.byagowi.persiancalendar.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import com.byagowi.persiancalendar.*
import com.byagowi.persiancalendar.databinding.ActivityMainBinding
import com.byagowi.persiancalendar.di.AppDependency
import com.byagowi.persiancalendar.di.MainActivityDependency
import com.byagowi.persiancalendar.service.ApplicationService
import com.byagowi.persiancalendar.ui.calendar.CalendarFragment
import com.byagowi.persiancalendar.utils.*
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject


/**
 * Program activity for android
 */
class MainActivity : DaggerAppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener,
    NavigationView.OnNavigationItemSelectedListener {

    @Inject
    lateinit var appDependency: AppDependency // same object from App
    @Inject
    lateinit var mainActivityDependency: MainActivityDependency

    private var creationDateJdn: Long = 0
    private var settingHasChanged = false
    private lateinit var binding: ActivityMainBinding

    val coordinator: CoordinatorLayout
        get() = binding.coordinator

    private var clickedItem = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Don't replace below with appDependency.getSharedPreferences() ever
        // as the injection won't happen at the right time
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setTheme(getThemeFromName(getThemeFromPreference(this, prefs)))

        applyAppLanguage(this)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        ReleaseDebugDifference.startLynxListenerIfIsDebug(this)
        initUtils(this)

        overrideFont("SERIF", getAppFont(applicationContext))

        startEitherServiceOrWorker(this)

        // Doesn't matter apparently
        // oneTimeClockDisablingForAndroid5LE();
        setDeviceCalendarEvents(applicationContext)
        update(applicationContext, false)

        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        setSupportActionBar(binding.toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // https://learnpainless.com/android/material/make-fully-android-transparent-status-bar
            val win = window
            val winParams = win.attributes
            winParams.flags =
                winParams.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
            win.attributes = winParams
            window.statusBarColor = Color.TRANSPARENT
        }

        val isRTL = isRTL(this)

        val drawerToggle = object : ActionBarDrawerToggle(
            this, binding.drawer, binding.toolbar, R.string.openDrawer, R.string.closeDrawer
        ) {
            var slidingDirection = if (isRTL) -1 else +1

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                slidingAnimation(drawerView, slideOffset / 1.5f)
            }

            private fun slidingAnimation(drawerView: View, slideOffset: Float) = binding.apply {
                appMainLayout.translationX =
                    slideOffset * drawerView.width.toFloat() * slidingDirection.toFloat()
                drawer.bringChildToFront(drawerView)
                drawer.requestLayout()
            }

            override fun onDrawerClosed(drawerView: View) {
                super.onDrawerClosed(drawerView)
                if (clickedItem != 0) {
                    navigateTo(clickedItem)
                    clickedItem = 0
                }
            }
        }

        binding.drawer.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        val intent = intent
        if (intent != null) {
            navigateTo(
                when (intent.action) {
                    "COMPASS" -> R.id.compass
                    "LEVEL" -> R.id.level
                    "CONVERTER" -> R.id.converter
                    "SETTINGS" -> R.id.settings
                    "DEVICE" -> R.id.deviceInfo
                    else -> R.id.calendar
                }
            )

            // So it won't happen again if the activity restarted
            intent.action = ""
        }

        prefs.registerOnSharedPreferenceChangeListener(this)

        if (isShowDeviceCalendarEvents && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) askForCalendarPermission(this)

        binding.navigation.setNavigationItemSelectedListener(this)

        binding.navigation.getHeaderView(0).findViewById<ImageView>(R.id.season_image)
            .setImageResource(run {
                var season = (getTodayOfCalendar(CalendarType.SHAMSI).month - 1) / 3

                // Southern hemisphere
                if ((getCoordinate(this)?.latitude ?: 1.0) < .0) season = (season + 2) % 4

                when (season) {
                    0 -> R.drawable.spring
                    1 -> R.drawable.summer
                    2 -> R.drawable.fall
                    else -> R.drawable.winter
                }
            })

        if (prefs.getString(PREF_APP_LANGUAGE, null) == null &&
            !prefs.getBoolean(CHANGE_LANGUAGE_IS_PROMOTED_ONCE, false)
        ) {
            Snackbar.make(coordinator, "✖  Change app language?", 7000).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    view.layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
                view.setOnClickListener { dismiss() }
                setAction("Settings") {
                    prefs.edit {
                        putString(PREF_APP_LANGUAGE, LANG_EN_US)
                        putString(PREF_MAIN_CALENDAR_KEY, "GREGORIAN")
                        putString(PREF_OTHER_CALENDARS_KEY, "ISLAMIC,SHAMSI")
                        putStringSet(PREF_HOLIDAY_TYPES, emptySet())
                    }
                }
                setActionTextColor(resources.getColor(R.color.dark_accent))
            }.show()
            prefs.edit {
                putBoolean(CHANGE_LANGUAGE_IS_PROMOTED_ONCE, true)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            binding.appbarLayout.outlineProvider = null

        creationDateJdn = getTodayJdn()

        if (mainCalendar == CalendarType.SHAMSI &&
            isIranHolidaysEnabled &&
            getTodayOfCalendar(CalendarType.SHAMSI).year > getMaxSupportedYear()
        ) Snackbar.make(coordinator, getString(R.string.outdated_app), 10000).apply {
            setAction(getString(R.string.update)) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                    )
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=$packageName".toUri()
                        )
                    )
                }
            }
            setActionTextColor(resources.getColor(R.color.dark_accent))
        }.show()

        applyAppLanguage(this)
    }

    fun navigateTo(@IdRes id: Int) {
        val menuItem = binding.navigation.menu.findItem(
            if (id == R.id.level) R.id.compass else id
        ) // We don't have a menu entry for compass, so
        if (menuItem != null) {
            menuItem.isCheckable = true
            menuItem.isChecked = true
        }

        if (settingHasChanged) { // update when checked menu item is changed
            initUtils(this)
            update(applicationContext, true)
            settingHasChanged = false // reset for the next time
        }

        Navigation.findNavController(this, R.id.nav_host_fragment).navigate(id, null, null)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        settingHasChanged = true
        if (key == PREF_APP_LANGUAGE) {
            var persianDigits = false
            var changeToAfghanistanHolidays = false
            var changeToIslamicCalendar = false
            var changeToGregorianCalendar = false
            var changeToPersianCalendar = false
            var changeToIranEvents = false
            var removeAllEvents = false
            when (sharedPreferences?.getString(PREF_APP_LANGUAGE, null) ?: DEFAULT_APP_LANGUAGE) {
                LANG_EN_US -> {
                    changeToGregorianCalendar = true
                    removeAllEvents = true
                }
                LANG_JA -> {
                    changeToGregorianCalendar = true
                    removeAllEvents = true
                    persianDigits = true
                }
                LANG_AZB, LANG_GLK, LANG_FA -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToIranEvents = true
                }
                LANG_EN_IR -> {
                    persianDigits = false
                    changeToPersianCalendar = true
                    changeToIranEvents = true
                }
                LANG_UR -> {
                    persianDigits = false
                    changeToGregorianCalendar = true
                }
                LANG_AR -> {
                    persianDigits = true
                    changeToIslamicCalendar = true
                }
                LANG_FA_AF -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToAfghanistanHolidays = true
                }
                LANG_PS -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToAfghanistanHolidays = true
                }
                else -> persianDigits = true
            }

            sharedPreferences?.edit {
                putBoolean(PREF_PERSIAN_DIGITS, persianDigits)
                // Enable Afghanistan holidays when Dari or Pashto is set
                if (changeToAfghanistanHolidays) {
                    val currentHolidays =
                        sharedPreferences.getStringSet(PREF_HOLIDAY_TYPES, null) ?: emptySet()

                    if (currentHolidays.isEmpty() || currentHolidays.size == 1 &&
                        currentHolidays.contains("iran_holidays")
                    ) putStringSet(PREF_HOLIDAY_TYPES, setOf("afghanistan_holidays"))

                }
                if (changeToIranEvents) {
                    val currentHolidays =
                        sharedPreferences.getStringSet(PREF_HOLIDAY_TYPES, null) ?: emptySet()

                    if (currentHolidays.isEmpty() ||
                        (currentHolidays.size == 1 && currentHolidays.contains("afghanistan_holidays"))
                    ) putStringSet(PREF_HOLIDAY_TYPES, setOf("iran_holidays"))
                }
                if (removeAllEvents) {
                    val currentHolidays =
                        sharedPreferences.getStringSet(PREF_HOLIDAY_TYPES, null) ?: emptySet()

                    if (currentHolidays.isEmpty() ||
                        (currentHolidays.size == 1 && currentHolidays.contains("iran_holidays"))
                    ) putStringSet(PREF_HOLIDAY_TYPES, emptySet())
                }
                when {
                    changeToGregorianCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "GREGORIAN")
                        putString(PREF_OTHER_CALENDARS_KEY, "ISLAMIC,SHAMSI")
                        putString(PREF_WEEK_START, "1")
                        putStringSet(PREF_WEEK_ENDS, setOf("1"))
                    }
                    changeToIslamicCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "ISLAMIC")
                        putString(PREF_OTHER_CALENDARS_KEY, "GREGORIAN,SHAMSI")
                        putString(PREF_WEEK_START, DEFAULT_WEEK_START)
                        putStringSet(PREF_WEEK_ENDS, DEFAULT_WEEK_ENDS)
                    }
                    changeToPersianCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "SHAMSI")
                        putString(PREF_OTHER_CALENDARS_KEY, "GREGORIAN,ISLAMIC")
                        putString(PREF_WEEK_START, DEFAULT_WEEK_START)
                        putStringSet(PREF_WEEK_ENDS, DEFAULT_WEEK_ENDS)
                    }
                }
            }
        }

        if (key == PREF_SHOW_DEVICE_CALENDAR_EVENTS &&
            sharedPreferences?.getBoolean(PREF_SHOW_DEVICE_CALENDAR_EVENTS, true) == true
            && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) askForCalendarPermission(this)

        if (key == PREF_APP_LANGUAGE || key == PREF_THEME) restartToSettings()

        if (key == PREF_NOTIFY_DATE &&
            sharedPreferences?.getBoolean(PREF_NOTIFY_DATE, true) == false
        ) {
            stopService(Intent(this, ApplicationService::class.java))
            startEitherServiceOrWorker(applicationContext)
        }

        updateStoredPreference(this)
        update(applicationContext, true)

        ViewModelProviders.of(this)[MainActivityModel::class.java].preferenceIsUpdated()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALENDAR_READ_PERMISSION_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                toggleShowDeviceCalendarOnPreference(this, true)
                if (getCurrentDestinationId() == R.id.calendar) restartActivity()
            } else toggleShowDeviceCalendarOnPreference(this, false)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initUtils(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            binding.drawer.layoutDirection =
                if (isRTL(this)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
    }

    override fun onResume() {
        super.onResume()
        applyAppLanguage(this)
        update(applicationContext, false)
        if (creationDateJdn != getTodayJdn()) restartActivity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        // Checking for the "menu" key
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
                binding.drawer.closeDrawers()
            } else {
                binding.drawer.openDrawer(GravityCompat.START)
            }
            true
        } else {
            super.onKeyDown(keyCode, event)
        }

    fun restartActivity() {
        val intent = intent
        finish()
        startActivity(intent)
    }

    private fun restartToSettings() {
        val intent = intent
        intent.action = "SETTINGS"
        finish()
        startActivity(intent)
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.exit) {
            finish()
            return true
        }

        binding.drawer.closeDrawers()
        clickedItem = menuItem.itemId
        Log.d("ITEMID", menuItem.itemId.toString())
        return true
    }

    fun setTitleAndSubtitle(title: String, subtitle: String): Unit = supportActionBar?.let {
        it.title = title
        it.subtitle = subtitle
    } ?: Unit

    override fun onBackPressed() {
        if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
            binding.drawer.closeDrawers()
        } else {
            val calendarFragment = supportFragmentManager
                .findFragmentByTag(CalendarFragment::class.java.name) as CalendarFragment?
            if (calendarFragment?.closeSearch() == true) return

            if (getCurrentDestinationId() == R.id.calendar)
                finish()
            else
                navigateTo(R.id.calendar)
        }
    }

    private fun getCurrentDestinationId(): Int? = Navigation
        .findNavController(this, R.id.nav_host_fragment)
        .currentDestination?.id
}
