package com.thelightphone.lp3keyboard

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem
import com.thelightphone.lp3keyboard.voice.VoiceModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Lp3SetupScreen()
        }
    }
}

private val LightBlack = Color(0xFF000000)
private val LightWhite = Color(0xFFFFFFFF)
private val LightGray = Color(0xFF999999)
private val LightRed = Color(0xFFFF4444)

@Composable
fun Lp3SetupScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Track IME status
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeDefault by remember { mutableStateOf(isImeDefault(context)) }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                imeEnabled = isImeEnabled(context)
                imeDefault = isImeDefault(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            observer
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 34.dp, vertical = 24.dp)
    ) {
        Text(
            text = "LPIII Keyboard",
            style = TextStyle(
                color = LightWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "A faithful recreation of the Light Phone 3 keyboard for use in all your apps.",
            style = TextStyle(
                color = LightGray,
                fontSize = 16.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Step 1: Enable
        SetupStep(
            number = 1,
            label = "Enable keyboard",
            isDone = imeEnabled,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        // Step 2: Select
        SetupStep(
            number = 2,
            label = "Select keyboard",
            isDone = imeDefault,
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Settings
        Text(
            text = "Settings",
            style = TextStyle(color = LightWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LayoutSettingRow()
        
        VoiceSettingRow()

        Spacer(modifier = Modifier.height(24.dp))

        // Try it out
        Text(
            text = "Try the keyboard",
            style = TextStyle(color = LightWhite, fontSize = 20.sp),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        var textValue by remember { mutableStateOf(TextFieldValue("")) }
        TextField(
            value = textValue,
            onValueChange = { textValue = it },
            textStyle = TextStyle(color = LightWhite, fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                cursorColor = LightWhite,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                textColor = LightWhite
            ),
            placeholder = {
                Text("Type here...", color = LightGray, fontSize = 20.sp)
            },
            trailingIcon = {
                Text(" ▾", color = LightGray, fontSize = 20.sp)
            }
        )
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun SetupStep(number: Int, label: String, isDone: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number. $label",
            style = TextStyle(color = LightWhite, fontSize = 20.sp),
            modifier = Modifier.weight(1f)
        )
        if (isDone) {
            Text(text = "✓", color = LightWhite, fontSize = 20.sp)
        } else {
            Text(text = "→", color = LightWhite, fontSize = 20.sp)
        }
    }
}

@Composable
fun LayoutSettingRow() {
    val context = LocalContext.current
    var selectedLayout by remember { mutableStateOf(LayoutPreferences.getActiveLayout(context)) }
    
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = "Keyboard Layout",
            style = TextStyle(color = LightWhite, fontSize = 20.sp),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LayoutRegistryItem.entries.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedLayout = item
                        LayoutPreferences.setActiveLayout(context, item)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(if (item == selectedLayout) LightWhite else Color.Transparent)
                        .then(if (item != selectedLayout) Modifier.padding(2.dp) else Modifier)
                ) {
                    if (item != selectedLayout) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(color = LightWhite, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = item.label, color = LightWhite, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun VoiceSettingRow() {
    val context = LocalContext.current
    var voiceEnabled by remember { mutableStateOf(LayoutPreferences.isVoiceEnabled(context)) }
    var autocorrectEnabled by remember { mutableStateOf(LayoutPreferences.isAutocorrectEnabled(context)) }
    var autoCapitalizeEnabled by remember { mutableStateOf(LayoutPreferences.isAutoCapitalizeEnabled(context)) }
    var autoPeriodEnabled by remember { mutableStateOf(LayoutPreferences.isAutoPeriodEnabled(context)) }
    var isInstalled by remember { mutableStateOf(VoiceModel.isInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        LightToggleRow(
            label = "Autocorrect",
            checked = autocorrectEnabled,
            onCheckedChange = {
                autocorrectEnabled = it
                LayoutPreferences.setAutocorrectEnabled(context, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LightToggleRow(
            label = "Auto-Capitalization",
            checked = autoCapitalizeEnabled,
            onCheckedChange = {
                autoCapitalizeEnabled = it
                LayoutPreferences.setAutoCapitalizeEnabled(context, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LightToggleRow(
            label = "Auto-Period (Double Space)",
            checked = autoPeriodEnabled,
            onCheckedChange = {
                autoPeriodEnabled = it
                LayoutPreferences.setAutoPeriodEnabled(context, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LightToggleRow(
            label = "Voice Dictation",
            checked = voiceEnabled,
            enabled = !downloading,
            onCheckedChange = {
                voiceEnabled = it
                LayoutPreferences.setVoiceEnabled(context, it)
            }
        )

        if (isInstalled) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline model installed (40MB)",
                    color = LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete",
                    color = LightRed,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        VoiceModel.remove(context)
                        isInstalled = false
                    }
                )
            }
        } else if (downloading) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Downloading voice model... ${(progress * 100).toInt()}%",
                    color = LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = LightWhite,
                    backgroundColor = LightGray.copy(alpha = 0.3f)
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Voice model not installed (40MB)",
                    color = LightGray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Download Model",
                    color = LightWhite,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            downloading = true
                            error = null
                            VoiceModel.install(context,
                                onProgress = { progress = it / 100f },
                                onDone = {
                                    downloading = false
                                    isInstalled = true
                                },
                                onError = {
                                    downloading = false
                                    error = it
                                }
                            )
                        }
                )
                error?.let {
                    Text(text = it, color = LightRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun LightToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp)
            .then(if (!enabled) Modifier.background(Color.Transparent).padding(0.dp) else Modifier), // placeholder
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (enabled) LightWhite else LightGray,
                fontSize = 20.sp
            ),
            modifier = Modifier.weight(1f)
        )
        LightToggleMark(checked = checked, enabled = enabled)
    }
}

@Composable
fun LightToggleMark(checked: Boolean, enabled: Boolean) {
    val alpha = if (enabled) 1f else 0.4f
    val color = LightWhite.copy(alpha = alpha)
    
    Canvas(modifier = Modifier.size(width = 32.dp, height = 20.dp)) {
        val cy = size.height / 2f
        val circleDiam = 13.dp.toPx()
        val r = circleDiam / 2f
        val lineWidth = 19.dp.toPx()
        val lineHeight = 3.dp.toPx()
        val border = 3.dp.toPx()

        if (checked) {
            // Line on left, filled dot on right
            drawRect(
                color = color,
                topLeft = Offset(0f, cy - lineHeight / 2),
                size = Size(lineWidth, lineHeight)
            )
            drawCircle(
                color = color,
                center = Offset(lineWidth + r, cy),
                radius = r
            )
        } else {
            // Hollow dot on left, line on right
            drawCircle(
                color = color,
                center = Offset(r, cy),
                radius = r - border / 2,
                style = Stroke(width = border)
            )
            drawRect(
                color = color,
                topLeft = Offset(circleDiam, cy - lineHeight / 2),
                size = Size(lineWidth, lineHeight)
            )
        }
    }
}

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isImeDefault(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return current?.contains(context.packageName) == true
}
