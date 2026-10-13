package com.roziqrizal.habitflow.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.ui.AppIcons
import com.roziqrizal.habitflow.ui.theme.tokens
import kotlinx.coroutines.delay

/*
 * Komponen layar auth (tahap 31G). Nilainya dicatat di docs/design/README.md bagian Masuk, Daftar, Lupa kata sandi.
 */

private val FieldShape = RoundedCornerShape(12.dp)

/** Kolom isian dengan label di atasnya. Kata sandi mendapat ikon mata untuk tampil/sembunyikan. */
@Composable
fun AuthTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    password: Boolean = false,
    hint: String? = null,
) {
    val focus = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { onImeAction?.invoke() },
            ),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = if (visible) "Sembunyikan kata sandi" else "Tampilkan kata sandi",
                        )
                    }
                }
            } else {
                null
            },
            supportingText = (error ?: hint)?.let { text ->
                { Text(text, modifier = Modifier.semantics { if (error != null) liveRegion = LiveRegionMode.Polite }) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        )
    }
}

/**
 * Kolom kode 6 angka yang digambar sebagai 6 kotak. Di bawahnya tetap satu kolom teks sungguhan (tak terlihat),
 * jadi kode bisa ditempel dan diisi otomatis dari SMS atau email, dan pembaca layar membacanya sebagai satu kolom.
 */
@Composable
fun CodeField(
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // Kolom nonaktif selama permintaan berjalan dan kehilangan fokus (keyboard menutup): minta lagi begitu aktif kembali.
    LaunchedEffect(autoFocus, enabled) { if (autoFocus && enabled) runCatching { focusRequester.requestFocus() } }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = "Kode verifikasi ${AuthConfig.CODE_LENGTH} angka"
                },
            decorationBox = { inner ->
                Box {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(AuthConfig.CODE_LENGTH) { index ->
                            val active = focused && enabled && index == value.length.coerceAtMost(AuthConfig.CODE_LENGTH - 1)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 52.dp)
                                    .border(
                                        border = BorderStroke(
                                            width = if (active) 2.dp else 1.dp,
                                            color = when {
                                                error != null -> MaterialTheme.colorScheme.error
                                                active -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outlineVariant
                                            },
                                        ),
                                        shape = FieldShape,
                                    ),
                            ) {
                                Text(
                                    text = value.getOrNull(index)?.toString().orEmpty(),
                                    style = TextStyle(
                                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    // Kolom sungguhan menutupi kotak-kotak tapi tak terlihat: ketuk di mana pun memfokuskannya.
                    Box(modifier = Modifier.matchParentSize().alpha(0f)) { inner() }
                }
            },
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Kartu status: banner "Sesi berakhir", "Cek email kamu", dan sejenisnya (`primaryFixed` di atas `onPrimaryContainer`). */
@Composable
fun StatusCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.tokens.primaryFixed, FieldShape)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Galat di bawah form. Galat jaringan memakai warna netral karena bukan salah pengguna. */
@Composable
fun ErrorLine(state: AuthUiState, modifier: Modifier = Modifier) {
    val error = state.error ?: return
    Text(
        error,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.networkProblem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Garis kiri-kanan dengan teks "atau" di tengah, sebelum tombol Google. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "atau",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Waktu sekarang yang diperbarui tiap detik, untuk hitung mundur kirim ulang dan masa berlaku kode. */
@Composable
fun rememberTickingNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** "9:42" dari jumlah detik. */
fun formatCountdown(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
