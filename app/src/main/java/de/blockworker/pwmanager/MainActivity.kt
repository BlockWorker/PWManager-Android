package de.blockworker.pwmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.blockworker.pwmanager.settings.SettingDao
import de.blockworker.pwmanager.settings.IdentSettingEntity
import de.blockworker.pwmanager.settings.SettingStorage
import de.blockworker.pwmanager.settings.SettingsActivity
import de.blockworker.pwmanager.settings.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext


class MainActivity : ComponentActivity() {

    private lateinit var cbManager: ClipboardManager
    private lateinit var dao: SettingDao
    private var masterReset = {}

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dao = SettingStorage.getSettingDao(applicationContext)

        setContent {
            ActivityContainer (
                heightClass = calculateWindowSizeClass(this).heightSizeClass,
                settingsCb = { startActivity(Intent(applicationContext, SettingsActivity::class.java)) }
            ) {
                masterReset = PWM_UI(
                    genCb = this@MainActivity::genCallback,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .align(Alignment.CenterVertically)
                        .padding(all = 10.dp),
                    genLabel = "Generate and Copy",
                    identChangeCb = this@MainActivity::onIdentChange,
                    identHighlight = this@MainActivity::identKnown,
                    saveCb = this@MainActivity::saveIdent
                )
            }
        }

        cbManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private fun genCallback(pw: String) {
        val clip = ClipData.newPlainText("PWManager Password", pw)
        cbManager.setPrimaryClip(clip)

        Toast.makeText(applicationContext, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun onIdentChange(ident: String): IdentSettingEntity? {
        return runBlocking(context = Dispatchers.IO) {
            dao.getSettingForIdent(ident)
        }
    }

    private fun identKnown(ident: String): Boolean {
        return runBlocking(context = Dispatchers.IO) {
            dao.hasIdentifier(ident)
        } > 0
    }

    private fun saveIdent(entity: IdentSettingEntity) {
        if ((entity.iteration == 0 && entity.symbols == DEFAULT_SYMBOLS && entity.longpw)
            || entity.identifier.isEmpty()) return //don't save empty idents or default settings

        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            dao.insertIdentSettings(entity)
            SyncManager.sync(dao)
        }
    }

    override fun onStart() {
        super.onStart()

        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            SyncManager.sync(dao)
        }
    }

    override fun onPause() {
        masterReset()
        super.onPause()
    }

    override fun onStop() {
        masterReset()
        super.onStop()
    }
}


@Preview(showBackground = true)
@Composable
fun MainPreview() {
    ActivityContainer (
        heightClass = WindowHeightSizeClass.Medium,
        settingsCb = {}
    ) {
        PWM_UI(
            genCb = {},
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(all = 10.dp),
            initIdent = "sub.domain.com",
            genLabel = "Generate and Copy",
            identChangeCb = { IdentSettingEntity("sub.domain.com", 1,
                DEFAULT_SYMBOLS, true) },
            saveCb = {}
        )
    }
}