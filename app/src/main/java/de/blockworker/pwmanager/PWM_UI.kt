package de.blockworker.pwmanager

import android.annotation.SuppressLint
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.blockworker.pwmanager.settings.IdentSettingEntity
import de.blockworker.pwmanager.ui.theme.PWManagerTheme
import kotlinx.coroutines.delay
import java.lang.NumberFormatException
import kotlin.reflect.full.memberFunctions

const val DEFAULT_SYMBOLS = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

//returns action that clears the master password
@SuppressLint("ComposableNaming")
@Composable
fun PWM_UI(genCb: (String) -> Unit, modifier: Modifier = Modifier,
           initIdent: String = "", genLabel: String = "Generate",
           identSwCb: ((Boolean) -> String)? = null,
           identChangeCb : ((String) -> IdentSettingEntity?)? = null,
           identHighlight : (String) -> Boolean = { false },
           saveCb: ((IdentSettingEntity) -> Unit)? = null): () -> Unit {

    val master = remember { mutableStateOf("") }
    val ident = remember { mutableStateOf(initIdent) }
    val identToggle = remember { mutableStateOf(false) }
    val iterText = remember { mutableStateOf("0") }
    val iter = remember { mutableStateOf(0u) }
    val symbols = remember { mutableStateOf(DEFAULT_SYMBOLS) }
    val longpw = remember { mutableStateOf(true) }
    val save = remember { mutableStateOf(false) }

    val passLastShow = remember { mutableIntStateOf(0) }

    val focusRequester = FocusRequester()

    fun updateIdent() {
        if (identChangeCb != null) {
            val ret = identChangeCb(ident.value)
            if (ret != null) {
                iter.value = ret.iteration.toUInt()
                iterText.value = ret.iteration.toString()
                symbols.value = ret.symbols
                longpw.value = ret.longpw
                save.value = false //disable saving/overwriting of known value by default
            }
        }
    }

    LaunchedEffect(Unit) {
        updateIdent()
        focusRequester.requestFocus()
        while (true) {
            delay(100)
            if (passLastShow.intValue > 0) passLastShow.intValue--
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = master.value,
            onValueChange = {
                if (it.length > master.value.length) {
                    passLastShow.intValue = 10
                } else {
                    passLastShow.intValue = 0
                }
                master.value = it
            },
            visualTransformation = if (passLastShow.intValue > 0) PasswordLastShowTransformation()
                                    else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Master Password",
                    color = MaterialTheme.colorScheme.outline
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
                .focusRequester(focusRequester),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = "Master Password"
                )
            }
        )
        OutlinedTextField(
            value = ident.value,
            onValueChange = { ident.value = it },
            singleLine = true,
            placeholder = {
                Text(
                    text = "Identifier",
                    color = MaterialTheme.colorScheme.outline
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .onFocusChanged {
                    if (!it.isFocused) {
                        updateIdent()
                    }
                },
            leadingIcon = {
                if (identHighlight(ident.value)) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Identifier Known",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Identifier"
                    )
                }
            },
            trailingIcon = {
                if (identSwCb != null) {
                    IconToggleButton(
                        checked = identToggle.value,
                        onCheckedChange = {
                            identToggle.value = it
                            ident.value = identSwCb(it)
                            updateIdent()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FirstPage,
                            contentDescription = "Include subdomain"
                        )
                    }
                }
            }
        )
        OutlinedTextField(
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
                    text = "Iteration",
                    color = MaterialTheme.colorScheme.outline
                )
            },
            isError = iterText.value != iter.value.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
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
        OutlinedTextField(
            value = symbols.value,
            onValueChange = { symbols.value = it },
            visualTransformation = ShowWhitespaceTransformation(MaterialTheme.colorScheme.primary),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Symbols",
                    color = MaterialTheme.colorScheme.outline
                )
            },
            isError = symbols.value.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.EmojiSymbols,
                    contentDescription = "Symbols"
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { symbols.value = DEFAULT_SYMBOLS }
                ) {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = "All Symbols"
                    )
                }
            }
        )
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)) {
            FilledIconToggleButton(
                checked = longpw.value,
                onCheckedChange = { longpw.value = it },
                modifier = Modifier
                    .alignByBaseline()
            ) {
                Icon(
                    imageVector = Icons.Default.TextIncrease,
                    contentDescription = "Long Password"
                )
            }
            if (saveCb != null) {
                FilledIconToggleButton(
                    checked = save.value,
                    onCheckedChange = { save.value = it },
                    modifier = Modifier
                        .alignByBaseline(),
                    enabled = ident.value.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Preset"
                    )
                }
            }
            Button(
                contentPadding = PaddingValues(0.dp),
                onClick = {
                    updateIdent()
                    iterText.value = iter.value.toString()

                    if (saveCb != null && save.value && ident.value.isNotEmpty()) {
                        saveCb(IdentSettingEntity(
                            identifier = ident.value,
                            iteration = iter.value.toInt(),
                            symbols = symbols.value,
                            longpw = longpw.value
                        ))
                    }

                    genCb(PasswordGenerator.generate(master.value, ident.value,
                        iter.value, symbols.value, longpw.value))
                    master.value = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp)
                    .alignByBaseline(),
                enabled = symbols.value.isNotEmpty()
            ) {
                Text(text = genLabel)
            }
        }
    }

    return { master.value = "" }
}

@Preview(showBackground = true)
@Composable
fun PWMUIPreview() {
    PWManagerTheme {
        ActivityContainer(
            heightClass = WindowHeightSizeClass.Medium,
            backCb = {},
            settingsCb = {}
        ) {
            PWM_UI(
                genCb = {},
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 10.dp),
                initIdent = "sub.domain.com",
                identSwCb = { "" },
                identChangeCb = { IdentSettingEntity("sub.domain.com", 1,
                    DEFAULT_SYMBOLS, true) },
                saveCb = {}
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContainer(heightClass: WindowHeightSizeClass, backCb: (() -> Unit)? = null,
                      settingsCb: (() -> Unit)? = null, title: String = "PWManager",
                      inner: @Composable (RowScope.() -> Unit)) {
    val scrollBehavior =
        if (heightClass == WindowHeightSizeClass.Compact)
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        else
            TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    PWManagerTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (backCb != null) {
                            IconButton(
                                onClick = backCb
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (settingsCb != null) {
                            IconButton(
                                onClick = settingsCb
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer),
                    scrollBehavior = scrollBehavior
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            ) {
                inner()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 5.dp)
) {
    val tcf = TextFieldColors::class.memberFunctions.firstOrNull { it.name == "textColor" }
    val ccf = TextFieldColors::class.memberFunctions.firstOrNull { it.name == "cursorColor" }

    var textColor = MaterialTheme.colorScheme.onSurface
    if (tcf != null) {
        textColor = textStyle.color.takeOrElse {
            (tcf.call(colors, enabled, isError, interactionSource, currentComposer, 0) as State<Color>)
                .value
        }
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    BasicTextField(
        value, onValueChange, modifier, enabled, readOnly, mergedTextStyle, keyboardOptions,
        keyboardActions, singleLine, maxLines, minLines, visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(
            if (ccf != null) (
                (ccf.call(colors, isError, currentComposer, 0) as State<Color>).value
            ) else MaterialTheme.colorScheme.onSurface
        )
    ) { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value, innerTextField, enabled, singleLine, visualTransformation,
            interactionSource, isError, label, placeholder, leadingIcon, trailingIcon,
            prefix, suffix, supportingText, shape, colors, contentPadding
        ) {
            OutlinedTextFieldDefaults.ContainerBox(
                enabled,
                isError,
                interactionSource,
                colors,
                shape
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 5.dp)
) {
    val tcf = TextFieldColors::class.memberFunctions.firstOrNull { it.name == "textColor" }
    val ccf = TextFieldColors::class.memberFunctions.firstOrNull { it.name == "cursorColor" }

    var textColor = MaterialTheme.colorScheme.onSurface
    if (tcf != null) {
        textColor = textStyle.color.takeOrElse {
            (tcf.call(colors, enabled, isError, interactionSource, currentComposer, 0) as State<Color>)
                .value
        }
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    BasicTextField(
        value, onValueChange, modifier, enabled, readOnly, mergedTextStyle, keyboardOptions,
        keyboardActions, singleLine, maxLines, minLines, visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(
            if (ccf != null) (
                    (ccf.call(colors, isError, currentComposer, 0) as State<Color>).value
                    ) else MaterialTheme.colorScheme.onSurface
        )
    ) { innerTextField ->
        TextFieldDefaults.DecorationBox(
            value, innerTextField, enabled, singleLine, visualTransformation,
            interactionSource, isError, label, placeholder, leadingIcon, trailingIcon,
            prefix, suffix, supportingText, shape, colors, contentPadding
        ) {
            TextFieldDefaults.ContainerBox(
                enabled,
                isError,
                interactionSource,
                colors,
                shape
            )
        }
    }
}


/**
 * Like PasswordVisualTransformation, but showing the last character in plain text
 */
class PasswordLastShowTransformation(val mask: Char = '\u2022') : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val masked = if (text.isEmpty()) AnnotatedString("")
                        else AnnotatedString(mask.toString().repeat(text.text.length - 1)) +
                                text.subSequence(text.lastIndex, text.lastIndex + 1)

        return TransformedText(
            masked,
            OffsetMapping.Identity
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordLastShowTransformation) return false
        if (mask != other.mask) return false
        return true
    }

    override fun hashCode(): Int {
        return mask.hashCode()
    }
}

/**
 * Like VisualTransformation that highlights whitespace
 */
class ShowWhitespaceTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styles = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        val sb = StringBuilder()

        text.forEachIndexed { index, c ->
            if (c == ' ') {
                sb.append('␣')
                styles.add(AnnotatedString.Range(
                    SpanStyle(color = color),
                    index, index + 1
                ))
            } else {
                sb.append(c)
            }
        }

        return TransformedText(
            AnnotatedString(sb.toString(), styles),
            OffsetMapping.Identity
        )
    }
}
