package de.blockworker.pwmanager.autofill

import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.blockworker.pwmanager.DEFAULT_SYMBOLS
import de.blockworker.pwmanager.PWM_UI
import de.blockworker.pwmanager.settings.AppMappingEntity
import de.blockworker.pwmanager.settings.SettingDao
import de.blockworker.pwmanager.settings.IdentSettingEntity
import de.blockworker.pwmanager.settings.SettingStorage
import de.blockworker.pwmanager.settings.sync.SyncManager
import de.blockworker.pwmanager.ui.theme.PWManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

const val EXTRA_AUTOFILL_ID: String = "de.blockworker.pwmanager.autofill.AUTOFILL_ID"
const val EXTRA_WEBSITE: String = "de.blockworker.pwmanager.autofill.WEBSITE"
const val EXTRA_PACKAGE: String = "de.blockworker.pwmanager.autofill.PACKAGE"

val urlRegex = Regex("""(?:(?:[A-Za-z0-9-]+\.)+)?([A-Za-z0-9-]+\.[A-Za-z0-9-]+)""")

class AutofillActivity : ComponentActivity() {

    private lateinit var dao: SettingDao
    private lateinit var autofillId: AutofillId

    private var website: String? = null
    private var pkg: String? = null
    private var idents: Array<String> = arrayOf()

    private var replyIntent: Intent? = null
    private var masterReset = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dao = SettingStorage.getSettingDao(applicationContext)

        autofillId = intent.getParcelableExtra(EXTRA_AUTOFILL_ID)!!

        website = intent.getStringExtra(EXTRA_WEBSITE)
        pkg = intent.getStringExtra(EXTRA_PACKAGE)

        if (website.isNullOrEmpty() && !pkg.isNullOrEmpty()) {
            runBlocking(Dispatchers.IO) {
                val ident = dao.getIdentForApp(pkg!!)?.identifier
                if (ident != null) idents = arrayOf(ident)
            }
        } else if (!website.isNullOrEmpty()) {
            val match = urlRegex.matchEntire(website!!)
            if (match != null) {
                idents = match.groupValues.reversed().toTypedArray()
            }
        }

        setContent {
            AutofillDialog {
                masterReset = PWM_UI(
                    genCb = this@AutofillActivity::onGenerate,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(all = 10.dp),
                    initIdent = if (idents.isEmpty()) "" else idents.first(),
                    identSwCb = if (idents.isNotEmpty()) this@AutofillActivity::getIdent else null,
                    identChangeCb = this@AutofillActivity::onIdentChange,
                    identHighlight = this@AutofillActivity::identKnown,
                    saveCb = this@AutofillActivity::saveIdent
                )
            }
        }
    }

    private fun onGenerate(pw: String) {
        val placeholderPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)

        val responseDataset = Dataset.Builder()
            .setValue(
                autofillId,
                AutofillValue.forText(pw),
                placeholderPresentation
            )
            .build()

        replyIntent = Intent()
            .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responseDataset)

        finish()
    }

    private fun getIdent(full: Boolean): String {
        return if (idents.isEmpty()) ""
        else if (full) idents.last()
        else idents.first()
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
        if (entity.identifier.isEmpty()) return //don't save empty idents

        if (entity.iteration > 0 || entity.symbols != DEFAULT_SYMBOLS || !entity.longpw) {
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                dao.insertIdentSettings(entity)
            }
        }

        if (website.isNullOrEmpty() && !pkg.isNullOrEmpty()) {
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                dao.insertAppMappings(AppMappingEntity(
                    pkg = pkg!!,
                    identifier = entity.identifier
                ))
            }
        }

        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            SyncManager.sync(dao)
        }
    }

    override fun onStart() {
        super.onStart()

        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            SyncManager.sync(dao)
        }
    }

    override fun finish() {
        if (replyIntent != null) {
            setResult(RESULT_OK, replyIntent)
        } else {
            setResult(RESULT_CANCELED)
        }
        super.finish()
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

@Composable
fun AutofillDialog(inner: @Composable (RowScope.() -> Unit)) {
    PWManagerTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "PWManager",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    inner()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AutofillPreview() {
    AutofillDialog {
        PWM_UI(
            genCb = {},
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(all = 10.dp),
            initIdent = "domain.com",
            identSwCb = { "" },
            identChangeCb = { IdentSettingEntity("sub.domain.com", 1,
                "+/=", false) },
            saveCb = {}
        )
    }
}
