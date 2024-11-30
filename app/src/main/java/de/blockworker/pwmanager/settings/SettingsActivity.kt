package de.blockworker.pwmanager.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import de.blockworker.pwmanager.*
import de.blockworker.pwmanager.settings.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.NumberFormatException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

class SettingsActivity : ComponentActivity() {

    private lateinit var dao: SettingDao

    private val identEntities = mutableStateOf(listOf<IdentSettingEntity>())
    private val appEntities = mutableStateOf(listOf<AppMappingEntity>())
    private val syncInfo = mutableStateOf(SyncInfoEntity())

    private fun updateEntities() {
        identEntities.value = dao.getAllIdents().sortedBy { it.identifier }
        appEntities.value = dao.getAllApps().sortedBy { it.identifier }
        syncInfo.value = dao.getSyncInfo() ?: SyncInfoEntity()
    }

    private fun saveIdentEntity(oldIdent: String?, newEntity: IdentSettingEntity) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            if (oldIdent != null) dao.deleteIdents(SettingIdentifier(oldIdent))
            dao.insertIdentSettings(newEntity)
            updateEntities()
        }
    }

    private fun deleteIdentEntity(ident: String) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            dao.deleteIdents(SettingIdentifier(ident))
            updateEntities()
        }
    }

    private fun saveAppEntity(oldPkg: String?, newEntity: AppMappingEntity) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            if (oldPkg != null) dao.deleteApps(AppPackage(oldPkg))
            dao.insertAppMappings(newEntity)
            updateEntities()
        }
    }

    private fun deleteAppEntity(pkg: String) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            dao.deleteApps(AppPackage(pkg))
            updateEntities()
        }
    }

    private fun saveSyncInfo(info: SyncInfoEntity) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            dao.setSyncInfo(info)
            updateEntities()
        }
    }

    private fun sync(autoOnly: Boolean = false) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            SyncManager.sync(dao, autoOnly) {
                if (it) updateEntities()
            }
        }
    }

    private fun getAppInfo(pkg: String): Pair<Drawable?, String?> {
        return try {
            Pair(
                packageManager.getApplicationIcon(pkg),
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Pair(null, null)
        }
    }

    @Composable
    private fun PickApp(currentPkg: String, resultCb: (String?) -> Unit) {
        val appInfos = packageManager.getInstalledApplications(0)
        val allApps = appInfos.map {
            val res = getAppInfo(it.packageName)
            Triple(it.packageName!!, res.first, res.second)
        }
        val nonSysApps = appInfos.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }.map {
            val res = getAppInfo(it.packageName)
            Triple(it.packageName!!, res.first, res.second)
        }

        val pkg = remember { mutableStateOf(currentPkg) }
        val showSystem = remember { mutableStateOf(false) }
        val searchTerm = remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { resultCb(null) }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    @Composable
                    fun appItem(item: Triple<String, Drawable?, String?>) {
                        val itemModifier = Modifier
                            .padding(vertical = 3.dp)
                            .clickable {
                                pkg.value = item.first
                            }

                        AppPickerItem(
                            pkg = item.first,
                            name = item.third,
                            icon = item.second,
                            modifier = if (pkg.value != item.first) itemModifier
                                else itemModifier
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    1.dp, MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }

                    val apps = (if (showSystem.value) allApps else nonSysApps)
                        .filter {
                            searchTerm.value.isEmpty()
                                || it.first.contains(searchTerm.value, true)
                                || (it.third?.contains(searchTerm.value, true) ?: false)
                        }
                        .sortedBy { it.third ?: it.first }

                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "Choose App:",
                            fontSize = 16.sp
                        )
                        CompactTextField(
                            value = searchTerm.value,
                            onValueChange = { searchTerm.value = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "Search",
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .padding(horizontal = 5.dp, vertical = 5.dp)
                            .weight(1f)
                    ) {
                        if (apps.size > 1) {
                            items(apps.size - 1) {
                                appItem(apps[it])
                                Divider(
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (apps.isNotEmpty()) {
                            item {
                                appItem(apps.last())
                            }
                        }
                    }
                    Row {
                        CompactOutlinedTextField(
                            value = pkg.value,
                            onValueChange = { pkg.value = it },
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .fillMaxWidth(),
                            isError = pkg.value.isEmpty(),
                            contentPadding = PaddingValues(all = 5.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            TextButton(
                                onClick = { showSystem.value = !showSystem.value },
                            ) {
                                Text(
                                    text = if (showSystem.value) "HIDE SYSTEM"
                                    else "SHOW SYSTEM"
                                )
                            }
                        }
                        TextButton(
                            onClick = { resultCb(null) }
                        ) {
                            Text(text = "CANCEL")
                        }
                        TextButton(
                            onClick = { resultCb(pkg.value) },
                            enabled = pkg.value.isNotEmpty()
                        ) {
                            Text(text = "OK")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SyncConfig(currentConfig: SyncInfoEntity, resultCb: (SyncInfoEntity?) -> Unit) {

        val host = remember { mutableStateOf(currentConfig.serverHost) }
        val port = remember { mutableStateOf(currentConfig.serverPort.toUInt()) }
        val portText = remember { mutableStateOf(currentConfig.serverPort.toString()) }
        val token = remember { mutableStateOf(currentConfig.token) }

        val newConfig = remember { mutableStateOf(currentConfig) }
        val testState = remember { mutableStateOf<Boolean?>(null) }

        fun testCb(result: Boolean) {
            if (testState.value != true) return
            if (result) {
                testState.value = null
                resultCb(newConfig.value)
            } else {
                testState.value = false
            }
        }

        Dialog(
            onDismissRequest = {
                testState.value = null
                resultCb(null)
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Sync Configuration",
                        modifier = Modifier
                            .padding(all = 5.dp),
                        fontSize = 16.sp
                    )
                    CompactOutlinedTextField(
                        value = host.value,
                        onValueChange = { host.value = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Server Host",
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        isError = host.value.isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Server Host"
                            )
                        }
                    )
                    CompactOutlinedTextField(
                        value = portText.value,
                        onValueChange = {
                            portText.value = it

                            try {
                                if (it == "") port.value = 0u
                                else {
                                    port.value = it.toUInt()
                                    portText.value = port.value.toString()
                                }
                            } catch (_: NumberFormatException) {

                            }
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Server Port",
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        isError = portText.value != port.value.toString() || port.value == 0u,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .onFocusChanged {
                                if (!it.isFocused) {
                                    portText.value = port.value.toString()
                                }
                            },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lan,
                                contentDescription = "Server Port"
                            )
                        }
                    )
                    CompactOutlinedTextField(
                        value = token.value,
                        onValueChange = { token.value = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "User Token",
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        isError = token.value.isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Token"
                            )
                        },
                        trailingIcon = {
                            val tooltipState = remember { RichTooltipState() }
                            val coroutineScope = rememberCoroutineScope()
                            RichTooltipBox(
                                text = {
                                    Text(
                                        text = "A unique token that identifies you as a user.\n" +
                                                "DO NOT USE YOUR PASSWORD!\n" +
                                                "For example, use a random combination of words.\n" +
                                                "Don't share it, anyone who knows it can mess up " +
                                                "your settings."
                                    )
                                },
                                action = {},
                                tooltipState = tooltipState
                            ) {
                                IconButton(
                                    onClick = { coroutineScope.launch { tooltipState.show() } },
                                    modifier = Modifier
                                        .tooltipAnchor()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Info"
                                    )
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            TextButton(
                                onClick = {
                                    testState.value = null
                                    resultCb(SyncInfoEntity(
                                        token = currentConfig.token,
                                        autoSync = currentConfig.autoSync
                                    ))
                                },
                                enabled = testState.value != true
                            ) {
                                Text(
                                    text = "DELETE",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                testState.value = null
                                resultCb(null)
                            }
                        ) {
                            Text(text = "CANCEL")
                        }
                        TextButton(
                            onClick = {
                                newConfig.value = SyncInfoEntity(
                                    serverHost = host.value,
                                    serverPort = port.value.toInt(),
                                    token = token.value,
                                    autoSync = currentConfig.autoSync
                                )
                                testState.value = true
                                Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                                    SyncManager.testSyncConnection(newConfig.value, ::testCb)
                                }
                            },
                            enabled = host.value.isNotEmpty() && port.value > 0u
                                    && token.value.isNotEmpty() && testState.value != true
                        ) {
                            Text(text = "OK")
                        }
                    }
                    if (testState.value != null) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            if (testState.value == true) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(40.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "Testing server...",
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 5.dp, end = 45.dp),
                                    fontSize = 16.sp
                                )
                            } else {
                                Text(
                                    text = "Server not reachable.",
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically),
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                LaunchedEffect(EmptyCoroutineContext) {
                                    delay(5000)
                                    testState.value = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dao = SettingStorage.getSettingDao(applicationContext)
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            updateEntities()
        }

        setContent {
            ActivityContainer (
                heightClass = calculateWindowSizeClass(this).heightSizeClass,
                backCb = {
                   onBackPressedDispatcher.onBackPressed()
                },
                title = "PWManager Settings"
            ) {
                SettingsContent(
                    identEntities = identEntities.value,
                    appEntities = appEntities.value,
                    syncInfo = syncInfo.value,
                    appInfoCb = this@SettingsActivity::getAppInfo,
                    pickAppCb = { currentPkg, resultCb -> PickApp(currentPkg, resultCb) },
                    syncConfigCb = { currentConfig, resultCb -> SyncConfig(currentConfig, resultCb) },
                    identSaveCb = this@SettingsActivity::saveIdentEntity,
                    appSaveCb = this@SettingsActivity::saveAppEntity,
                    syncSaveCb = this@SettingsActivity::saveSyncInfo,
                    identDeleteCb = this@SettingsActivity::deleteIdentEntity,
                    appDeleteCb = this@SettingsActivity::deleteAppEntity,
                    syncCb = this@SettingsActivity::sync
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        sync(true)
    }

    override fun onStop() {
        super.onStop()

        sync(true)
    }
}

@Composable
fun AppPickerItem(pkg: String, name: String?, icon: Drawable?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(all = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            if (icon == null) {
                Icon(
                    imageVector = Icons.Default.DeviceUnknown,
                    contentDescription = "Unknown App",
                    modifier = Modifier
                        .size(50.dp)
                )
            } else {
                Image(
                    painter = rememberDrawablePainter(icon),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(all = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "App Name",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = name ?: "(Unknown)",
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AppRegistration,
                    contentDescription = "App Package",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = pkg,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SettingsContent(identEntities: List<IdentSettingEntity>, appEntities: List<AppMappingEntity>,
                    syncInfo: SyncInfoEntity, appInfoCb: (String) -> Pair<Drawable?, String?>,
                    pickAppCb: @Composable (String, (String?) -> Unit) -> Unit,
                    syncConfigCb: @Composable (SyncInfoEntity, (SyncInfoEntity?) -> Unit) -> Unit,
                    identSaveCb: (String?, IdentSettingEntity) -> Unit,
                    appSaveCb: (String?, AppMappingEntity) -> Unit,
                    syncSaveCb: (SyncInfoEntity) -> Unit,
                    identDeleteCb: (String) -> Unit, appDeleteCb: (String) -> Unit,
                    syncCb: () -> Unit, modifier: Modifier = Modifier) {
    val tabIndex = remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = tabIndex.intValue
        ) {
            arrayOf("Identifiers", "App Mapping", "Sync").forEachIndexed { index, title ->
                Tab(
                    text = {
                        Text(title)
                    },
                    onClick = {
                        tabIndex.intValue = index
                    },
                    selected = tabIndex.intValue == index
                )
            }
        }
        Column {
            when (tabIndex.intValue) {
                0 -> IdentSettings(
                    items = identEntities,
                    saveCb = identSaveCb,
                    deleteCb = identDeleteCb
                )
                1 -> AppSettings(
                    items = appEntities,
                    appInfoCb = appInfoCb,
                    pickAppCb = pickAppCb,
                    saveCb = appSaveCb,
                    deleteCb = appDeleteCb
                )
                2 -> SyncSettings(
                    info = syncInfo,
                    saveCb = syncSaveCb,
                    configCb = syncConfigCb,
                    syncCb = syncCb
                )
            }
        }
    }
}

@Composable
fun IdentItem(entity: IdentSettingEntity, editCb: () -> Unit, modifier: Modifier = Modifier) {
    val transform = ShowWhitespaceTransformation(MaterialTheme.colorScheme.primary)

    Row(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(all = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            )  {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Identifier",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = entity.identifier,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "Iteration",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = entity.iteration.toString(),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiSymbols,
                    contentDescription = "Symbols",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = transform.filter(AnnotatedString(entity.symbols)).text,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.TextIncrease,
                    contentDescription = if (entity.longpw) "Long Password" else "Short Password",
                    modifier = Modifier
                        .size(20.dp),
                    tint = if (entity.longpw) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        IconButton(
            onClick = editCb,
            modifier = Modifier
                .padding(start = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit"
            )
        }
    }
}

@Composable
fun IdentEditItem(initEntity: IdentSettingEntity, saveCb: (IdentSettingEntity) -> Unit,
                  modifier: Modifier = Modifier, deleteCb: (() -> Unit)? = null,
                  identValidator: (String) -> Boolean = { it.isNotEmpty() }) {

    val ident = remember { mutableStateOf(initEntity.identifier) }
    val iterText = remember { mutableStateOf(initEntity.iteration.toString()) }
    val iter = remember { mutableStateOf(initEntity.iteration.toUInt()) }
    val symbols = remember { mutableStateOf(initEntity.symbols) }
    val longpw = remember { mutableStateOf(initEntity.longpw) }
    val deleteConfirm = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            if (deleteConfirm.intValue > 0) deleteConfirm.intValue--
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(all = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            )  {
                CompactOutlinedTextField(
                    value = ident.value,
                    onValueChange = { ident.value = it },
                    singleLine = true,
                    isError = !identValidator(ident.value),
                    placeholder = {
                        Text(
                            text = "Identifier",
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier
                        .weight(.75f)
                        .padding(end = 5.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Identifier"
                        )
                    }
                )
                CompactOutlinedTextField(
                    value = iterText.value,
                    onValueChange = {
                        iterText.value = it

                        try {
                            if (it == "") iter.value = 0u
                            else {
                                iter.value = it.toUInt()
                                iterText.value = iter.value.toString()
                            }
                        } catch (_: NumberFormatException) {

                        }
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "It.",
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    isError = iterText.value != iter.value.toString(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(.25f)
                        .onFocusChanged {
                            if (!it.isFocused) {
                                iterText.value = iter.value.toString()
                            }
                        },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = "Iteration"
                        )
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                CompactOutlinedTextField(
                    value = symbols.value,
                    onValueChange = { symbols.value = it },
                    visualTransformation = ShowWhitespaceTransformation(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Symbols (empty = all)",
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .onFocusChanged {
                            if (!it.isFocused && symbols.value.isEmpty()) {
                                symbols.value = DEFAULT_SYMBOLS
                            }
                        },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.EmojiSymbols,
                            contentDescription = "Symbols"
                        )
                    }
                )
                FilledIconToggleButton(
                    checked = longpw.value,
                    onCheckedChange = { longpw.value = it },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Default.TextIncrease,
                        contentDescription = "Long Password"
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 5.dp, horizontal = 2.dp)
                .requiredWidth(.9.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            if (deleteCb != null) {
                FilledIconButton(
                    onClick = {
                        if (deleteConfirm.intValue > 0) {
                            deleteCb()
                        } else {
                            deleteConfirm.intValue = 30
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 5.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = if (deleteConfirm.intValue > 0) Icons.Default.DeleteForever
                        else Icons.Default.Delete,
                        contentDescription = if (deleteConfirm.intValue > 0) "Confirm Deletion"
                        else "Delete"
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    if (symbols.value.isEmpty()) {
                        symbols.value = DEFAULT_SYMBOLS
                    }
                    iterText.value = iter.value.toString()
                    saveCb(IdentSettingEntity(
                        ident.value, iter.value.toInt(), symbols.value, longpw.value
                    ))
                },
                enabled = identValidator(ident.value)
            ) {
                Icon(
                    imageVector = if (deleteCb != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Save"
                )
            }
        }
    }
}

@Composable
fun IdentSettings(items: List<IdentSettingEntity>, saveCb: (String?, IdentSettingEntity) -> Unit,
                  deleteCb: (String) -> Unit, modifier: Modifier = Modifier) {
    val editing = remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier
    ) {
        items(items) { entity ->
            if (editing.value.contains(entity.identifier)) {
                IdentEditItem(
                    initEntity = entity,
                    saveCb = {
                        editing.value -= entity.identifier
                        saveCb(entity.identifier, it)
                    },
                    deleteCb = {
                        editing.value -= entity.identifier
                        deleteCb(entity.identifier)
                    }
                )
            } else {
                IdentItem(
                    entity = entity,
                    editCb = {
                        editing.value += entity.identifier
                    }
                )
            }
            Divider(
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            IdentEditItem(
                initEntity = IdentSettingEntity(
                    identifier = "",
                    iteration = 0,
                    symbols = DEFAULT_SYMBOLS,
                    longpw = true
                ),
                saveCb = {
                    saveCb(null, it)
                },
                identValidator = {
                    it.isNotEmpty() && !items.any { i -> i.identifier == it }
                }
            )
        }
    }
}

@Composable
fun AppItem(entity: AppMappingEntity, appInfoCb: (String) -> Pair<Drawable?, String?>,
            editCb: () -> Unit, modifier: Modifier = Modifier) {

    val (appIcon, appName) = appInfoCb(entity.pkg)

    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(all = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            if (appIcon == null) {
                Icon(
                    imageVector = Icons.Default.DeviceUnknown,
                    contentDescription = "Unknown App",
                    modifier = Modifier
                        .size(50.dp)
                )
            } else {
                Image(
                    painter = rememberDrawablePainter(appIcon),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(all = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                Icon(
                    imageVector = if (appName == null) Icons.Default.AppRegistration
                                    else Icons.Default.Apps,
                    contentDescription = if (appName == null) "App Package" else "App Name",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = appName ?: entity.pkg,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Identifier",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = entity.identifier,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
            }
        }
        IconButton(
            onClick = editCb,
            modifier = Modifier
                .padding(start = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit"
            )
        }
    }
}

@Composable
fun AppEditItem(initEntity: AppMappingEntity, appInfoCb: (String) -> Pair<Drawable?, String?>,
                pickAppCb: @Composable (String, (String?) -> Unit) -> Unit,
                saveCb: (AppMappingEntity) -> Unit,
                modifier: Modifier = Modifier, deleteCb: (() -> Unit)? = null,
                pkgValidator: (String) -> Boolean = { it.isNotEmpty() }) {

    val pkg = remember { mutableStateOf(initEntity.pkg) }
    val ident = remember { mutableStateOf(initEntity.identifier) }
    val appIcon = remember { mutableStateOf<Drawable?>(null) }
    val appName = remember { mutableStateOf("(Unknown)") }

    val deleteConfirm = remember { mutableIntStateOf(0) }
    val pickingApp = remember { mutableStateOf(false) }

    fun updateAppInfo() {
        val res = appInfoCb(pkg.value)
        appIcon.value = res.first
        appName.value = res.second ?: "(Unknown)"
    }

    LaunchedEffect(Unit) {
        updateAppInfo()
        while (true) {
            delay(100)
            if (deleteConfirm.intValue > 0) deleteConfirm.intValue--
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .padding(all = 5.dp)
                .size(50.dp)
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(
                    onClickLabel = "Pick App"
                ) {
                    pickingApp.value = true
                    deleteConfirm.intValue = 0
                }
        ) {
            if (appIcon.value == null) {
                Icon(
                    imageVector = Icons.Default.DeviceUnknown,
                    contentDescription = "Unknown App",
                    modifier = Modifier
                        .size(50.dp)
                        .padding(all = 5.dp)
                )
            } else {
                Image(
                    painter = rememberDrawablePainter(appIcon.value),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(50.dp)
                        .padding(all = 3.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(all = 5.dp)
                .align(Alignment.CenterVertically)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "App Name",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = appName.value,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AppRegistration,
                    contentDescription = "App Package",
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(20.dp)
                )
                Text(
                    text = pkg.value.ifEmpty { "(None)" },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                CompactOutlinedTextField(
                    value = ident.value,
                    onValueChange = { ident.value = it },
                    singleLine = true,
                    isError = ident.value.isEmpty(),
                    placeholder = {
                        Text(
                            text = "Identifier",
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 5.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Identifier"
                        )
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 5.dp, horizontal = 2.dp)
                .requiredWidth(.9.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            if (deleteCb != null) {
                FilledIconButton(
                    onClick = {
                        if (deleteConfirm.intValue > 0) {
                            deleteCb()
                        } else {
                            deleteConfirm.intValue = 30
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 5.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = if (deleteConfirm.intValue > 0) Icons.Default.DeleteForever
                                        else Icons.Default.Delete,
                        contentDescription = if (deleteConfirm.intValue > 0) "Confirm Deletion"
                                                else "Delete"
                    )
                }
            }
            FilledIconButton(
                onClick = { saveCb(AppMappingEntity(pkg.value, ident.value)) },
                enabled = pkgValidator(pkg.value) && ident.value.isNotEmpty()
            ) {
                Icon(
                    imageVector = if (deleteCb != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Save"
                )
            }
        }
    }
    if (pickingApp.value) {
        pickAppCb(pkg.value) {
            if (it != null) {
                pkg.value = it
                updateAppInfo()
            }
            pickingApp.value = false
        }
    }
}

@Composable
fun AppSettings(items: List<AppMappingEntity>, appInfoCb: (String) -> Pair<Drawable?, String?>,
                pickAppCb: @Composable (String, (String?) -> Unit) -> Unit,
                saveCb: (String?, AppMappingEntity) -> Unit, deleteCb: (String) -> Unit,
                modifier: Modifier = Modifier) {
    val editing = remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier
    ) {
        items(items) { entity ->
            if (editing.value.contains(entity.pkg)) {
                AppEditItem(
                    initEntity = entity,
                    appInfoCb = appInfoCb,
                    pickAppCb = pickAppCb,
                    saveCb = {
                        editing.value -= entity.pkg
                        saveCb(entity.pkg, it)
                    },
                    deleteCb = {
                        editing.value -= entity.pkg
                        deleteCb(entity.pkg)
                    }
                )
            } else {
                AppItem(
                    entity = entity,
                    appInfoCb = appInfoCb,
                    editCb = {
                        editing.value += entity.pkg
                    }
                )
            }
            Divider(
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            AppEditItem(
                initEntity = AppMappingEntity(pkg = "", identifier = ""),
                appInfoCb = appInfoCb,
                pickAppCb = pickAppCb,
                saveCb = {
                    saveCb(null, it)
                },
                pkgValidator = {
                    it.isNotEmpty() && !items.any { i -> i.pkg == it }
                }
            )
        }
    }
}

fun durationString(duration: Duration): String {
    val absDur = duration.abs()!!
    val secondsExact = absDur.seconds + absDur.nano / 1.0e9
    val seconds = secondsExact.toInt()
    val minutes = (secondsExact / 60.0).toInt()
    val hours = (secondsExact / 3600.0).toInt()
    val days = (secondsExact / 86400.0).toInt()
    val weeks = (secondsExact / 604800.0).toInt()
    val months = (secondsExact / 2629743.83).toInt()
    val years = (secondsExact / 31556926.0).toInt()

    val str =
        if (years >= 1) years.toString() + if (years > 1) " years" else " year"
        else if (months >= 1) months.toString() + if (months > 1) " months" else " month"
        else if (weeks >= 1) weeks.toString() + if (weeks > 1) " weeks" else " week"
        else if (days >= 1) days.toString() + if (days > 1) " days" else " day"
        else if (hours >= 1) hours.toString() + if (hours > 1) " hours" else " hour"
        else if (minutes >= 1) minutes.toString() + if (minutes > 1) " minutes" else " minute"
        else if (seconds >= 1) seconds.toString() + if (seconds > 1) " seconds" else " second"
        else return "Now"

    return if (duration.isNegative) "$str ago"
    else "In $str"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettings(info: SyncInfoEntity, saveCb: (SyncInfoEntity) -> Unit,
                 configCb: @Composable (SyncInfoEntity, (SyncInfoEntity?) -> Unit) -> Unit,
                 syncCb: () -> Unit, modifier: Modifier = Modifier) {

    val time = remember { mutableStateOf(Instant.now()) }
    val syncInProgress = remember { mutableStateOf(SyncManager.isSyncInProgress) }
    val syncError = remember { mutableStateOf(!SyncManager.wasLastSyncSuccessful) }

    val editingConfig = remember { mutableStateOf(false) }

    LaunchedEffect(EmptyCoroutineContext) {
        while (true) {
            delay(200)
            time.value = Instant.now()
            syncInProgress.value = SyncManager.isSyncInProgress
            syncError.value = !SyncManager.wasLastSyncSuccessful
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        if (info.isValid) {
            Row(
                modifier = Modifier
                    .padding(vertical = 5.dp)
            ) {
                IconToggleButton(
                    checked = info.autoSync,
                    onCheckedChange = {
                        info.autoSync = it
                        saveCb(info)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Auto-Sync",
                        modifier = Modifier
                            .size(35.dp)
                    )
                }
                Text(
                    text = if (info.autoSync) "Auto-Sync Enabled"
                            else "Auto-Sync Disabled",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f)
                        .padding(start = 10.dp),
                    fontSize = 18.sp
                )
            }
            Card(
                onClick = { editingConfig.value = true },
                modifier = Modifier
                    .padding(horizontal = 30.dp, vertical = 10.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(all = 20.dp)
                ) {
                    Text(
                        text = "Sync Configuration",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("Server Host:", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Server Port:", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("User Token:", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(info.serverHost, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(info.serverPort.toString(), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "\u2022".repeat(8), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(all = 5.dp)
            ) {
                val date = info.lastSync.atOffset(ZoneOffset.UTC)
                val offset = Duration.between(time.value, info.lastSync)
                val timeString = if (date.year < 2000) "Never"
                                    else durationString(offset)
                var lastSyncLabel = "Last Sync: $timeString"
                if (syncError.value) lastSyncLabel += " (failed later)"

                Text(
                    text = if (syncInProgress.value) "Sync in progress..." else lastSyncLabel,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f)
                        .padding(end = 10.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    color = if (syncError.value) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = {
                        syncInProgress.value = true
                        syncCb()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterVertically),
                    enabled = !syncInProgress.value
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync"
                    )
                    Text(
                        text = "Sync Now"
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SyncProblem,
                    contentDescription = "Sync not configured",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(50.dp)
                )
                Text(
                    text = "Sync not configured",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    fontSize = 18.sp
                )
                Button(
                    onClick = { editingConfig.value = true },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = "Set up Sync"
                    )
                }
            }
        }
    }
    if (editingConfig.value) {
        configCb(info) {
            if (it != null) saveCb(it)
            editingConfig.value = false
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    ActivityContainer (
        heightClass = WindowHeightSizeClass.Medium,
        backCb = {},
        title = "PWManager Settings"
    ) {
        SettingsContent(
            listOf(),
            listOf(),
            SyncInfoEntity(),
            appInfoCb = { Pair(null, null) },
            pickAppCb = { _, _ -> },
            syncConfigCb = { _, _ -> },
            identSaveCb = { _, _ -> },
            appSaveCb = { _, _ -> },
            syncSaveCb = {},
            identDeleteCb = {},
            appDeleteCb = {},
            syncCb = {}
        )
    }
}