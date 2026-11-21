package com.globalchat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalchat.android.model.supportedCountries
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onAgree: () -> Unit) {
    val p = LocalWaPalette.current
    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(36.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(92.dp).clip(CircleShape).background(if (p.isDark) Color.Transparent else Color(0xFFE7FFDB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, null, tint = p.accent, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(44.dp))
            Text("Welcome to GlobalChat", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
            Spacer(Modifier.height(14.dp))
            val policyText = buildAnnotatedString {
                append("Read our ")
                withStyle(SpanStyle(color = p.linkBlue)) { append("Privacy Policies") }
                append(". Tap \"Agree and continue\" to accept our ")
                withStyle(SpanStyle(color = p.linkBlue)) { append("Terms of Service") }
                append(".")
            }
            Text(policyText, textAlign = TextAlign.Center, color = p.textSecondary, fontSize = 13.5.sp, lineHeight = 20.sp)
        }

        Button(
            onClick = onAgree,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Agree and continue", color = p.accentText, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
        }
    }
}

@Composable
fun PhoneEntryScreen(
    isLoading: Boolean,
    errorText: String?,
    onSubmit: (String, String, String) -> Unit
) {
    val p = LocalWaPalette.current
    var selectedCountry by remember { mutableStateOf(supportedCountries.first()) }
    var showCountryMenu by remember { mutableStateOf(false) }
    var localPhone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Enter your phone number", fontSize = 21.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
        Spacer(Modifier.height(12.dp))
        val sub = buildAnnotatedString {
            append("GlobalChat will verify your account and email a free 6-digit verification code. ")
            withStyle(SpanStyle(color = p.accent)) { append("Secure OTP") }
        }
        Text(sub, textAlign = TextAlign.Center, fontSize = 13.5.sp, color = p.textSecondary, lineHeight = 19.sp)
        Spacer(Modifier.height(28.dp))

        Box(modifier = Modifier.width(260.dp).clickable { showCountryMenu = true }) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(20.dp))
                    Text("${selectedCountry.flag}  ${selectedCountry.name}", color = p.textPrimary, fontSize = 16.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = p.accent)
                }
                HorizontalDivider(color = p.accent, thickness = 1.2.dp)
            }
            DropdownMenu(expanded = showCountryMenu, onDismissRequest = { showCountryMenu = false }) {
                supportedCountries.forEach { country ->
                    DropdownMenuItem(
                        text = { Text("${country.flag}  ${country.name} (${country.dialCode})") },
                        onClick = { selectedCountry = country; showCountryMenu = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.width(260.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.width(72.dp)) {
                Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+ ", color = p.textSecondary, fontSize = 16.sp)
                    Text(selectedCountry.dialCode.removePrefix("+"), color = p.textPrimary, fontSize = 16.sp)
                }
                HorizontalDivider(color = p.accent, thickness = 1.2.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = localPhone,
                    onValueChange = { localPhone = it },
                    singleLine = true,
                    cursorBrush = SolidColor(p.accent),
                    textStyle = TextStyle(color = p.textPrimary, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) {
                            if (localPhone.isEmpty()) Text("Phone number", color = p.textSecondary, fontSize = 16.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = p.accent, thickness = 1.6.dp)
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(modifier = Modifier.width(260.dp)) {
            BasicTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                cursorBrush = SolidColor(p.accent),
                textStyle = TextStyle(color = p.textPrimary, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                decorationBox = { inner ->
                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                        if (email.isEmpty()) Text("Email address (for 6-digit OTP)", color = p.textSecondary, fontSize = 14.5.sp)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = p.surfaceVariant, thickness = 1.2.dp)
        }

        if (errorText != null) {
            Spacer(Modifier.height(14.dp))
            Text(errorText, color = p.danger, fontSize = 12.5.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.weight(1f))

        val canProceed = !isLoading && localPhone.count { it.isDigit() } >= 6 && email.contains("@")
        Button(
            onClick = { onSubmit(selectedCountry.dialCode, localPhone, email) },
            enabled = canProceed,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, disabledContainerColor = p.surface),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            if (isLoading) CircularProgressIndicator(color = p.accentText, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Next", color = if (canProceed) p.accentText else p.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    email: String,
    isLoading: Boolean,
    errorText: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onWrongNumber: () -> Unit
) {
    val p = LocalWaPalette.current
    var otpCode by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Verifying $phoneNumber", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text("We sent a 6-digit code to $email.", fontSize = 13.5.sp, color = p.textSecondary, textAlign = TextAlign.Center)
        TextButton(onClick = onWrongNumber) { Text("Wrong number or email?", color = p.linkBlue) }

        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
                singleLine = true,
                cursorBrush = SolidColor(p.accent),
                textStyle = TextStyle(color = p.textPrimary, fontSize = 24.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (otpCode.isEmpty()) Text("- - -  - - -", color = p.textSecondary, fontSize = 22.sp)
                        inner()
                    }
                }
            )
            HorizontalDivider(color = p.accent, thickness = 1.6.dp)
        }

        if (errorText != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorText, color = p.danger, fontSize = 12.5.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (secondsLeft > 0) "Resend code in 0:${secondsLeft.toString().padStart(2, '0')}" else "Didn't receive code? Resend",
            fontSize = 13.5.sp,
            color = if (secondsLeft > 0) p.textSecondary else p.accent,
            fontWeight = if (secondsLeft == 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clickable(enabled = secondsLeft == 0 && !isLoading) {
                secondsLeft = 60
                onResend()
            }
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onVerify(otpCode) },
            enabled = !isLoading && otpCode.length == 6,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, disabledContainerColor = p.surface),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            if (isLoading) CircularProgressIndicator(color = p.accentText, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Verify", color = if (otpCode.length == 6) p.accentText else p.textSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ProfileSetupScreen(onComplete: (String, String, Uri?) -> Unit) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("Hey there! I am using GlobalChat.") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }

    val previewBitmap = remember(avatarUri) {
        avatarUri?.let { MediaHelper.uriToCompressedBase64(context, it, 300)?.let(MediaHelper::decodeBase64ToImage) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Profile info", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Please provide your name and an optional profile photo", fontSize = 13.sp, color = p.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(p.surface)
                .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(previewBitmap, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.AddAPhoto, null, tint = p.textSecondary, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Type your name here") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.textPrimary,
                unfocusedTextColor = p.textPrimary,
                focusedBorderColor = p.accent,
                unfocusedBorderColor = p.surfaceVariant,
                focusedLabelColor = p.accent,
                unfocusedLabelColor = p.textSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            label = { Text("About") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.textPrimary,
                unfocusedTextColor = p.textPrimary,
                focusedBorderColor = p.accent,
                unfocusedBorderColor = p.surfaceVariant,
                focusedLabelColor = p.accent,
                unfocusedLabelColor = p.textSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onComplete(name, about, avatarUri) },
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, disabledContainerColor = p.surface),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Next", color = if (name.isNotBlank()) p.accentText else p.textSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}