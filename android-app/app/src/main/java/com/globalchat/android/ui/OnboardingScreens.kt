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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalchat.android.model.supportedCountries
import kotlinx.coroutines.delay

private val WaGreen = Color(0xFF008069)
private val WaBrightGreen = Color(0xFF1DAB61)

@Composable
fun WelcomeScreen(onAgree: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape).background(Color(0xFFE7FFDB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Forum, null, tint = WaGreen, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text("Welcome to GlobalChat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111B21))
            Spacer(Modifier.height(14.dp))
            Text(
                "Simple, reliable, private messaging across the world. Tap \"Agree and continue\" to accept our Terms of Service and Privacy Policy.",
                textAlign = TextAlign.Center,
                color = Color(0xFF667781),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Button(
            onClick = onAgree,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Agree and continue", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PhoneEntryScreen(onSubmit: (String, String) -> Unit) {
    var selectedCountry by remember { mutableStateOf(supportedCountries.first()) }
    var showCountryMenu by remember { mutableStateOf(false) }
    var localPhone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Enter your phone number", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WaGreen)
        Spacer(Modifier.height(10.dp))
        Text(
            "GlobalChat will verify your phone number. Carrier SMS charges may apply once live OTP is enabled.",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = Color(0xFF667781)
        )
        Spacer(Modifier.height(28.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = "${selectedCountry.flag}  ${selectedCountry.name}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Country") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                modifier = Modifier.fillMaxWidth().clickable { showCountryMenu = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { showCountryMenu = true })
            DropdownMenu(expanded = showCountryMenu, onDismissRequest = { showCountryMenu = false }) {
                supportedCountries.forEach { country ->
                    DropdownMenuItem(
                        text = { Text("${country.flag}  ${country.name} (${country.dialCode})") },
                        onClick = {
                            selectedCountry = country
                            showCountryMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = selectedCountry.dialCode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Code") },
                modifier = Modifier.width(96.dp)
            )
            OutlinedTextField(
                value = localPhone,
                onValueChange = { localPhone = it },
                label = { Text("Phone number") },
                placeholder = { Text("8012345678") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSubmit(selectedCountry.dialCode, localPhone) },
            enabled = localPhone.count { it.isDigit() } >= 6,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            modifier = Modifier.width(140.dp).height(46.dp)
        ) {
            Text("Next", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    errorText: String?,
    onVerify: (String) -> Unit,
    onWrongNumber: () -> Unit
) {
    var otpCode by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Verifying $phoneNumber", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WaGreen)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onWrongNumber) {
            Text("Wrong number?", color = Color(0xFF027EB5))
        }
        Spacer(Modifier.height(12.dp))

        Surface(
            color = Color(0xFFFFF8E1),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Pre-Firebase Test Mode: Enter 123456 below to continue. Real SIM SMS arrives after Upgrade 3 (Firebase Auth).",
                fontSize = 12.sp,
                color = Color(0xFF6D4C41),
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = otpCode,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
            label = { Text("Enter 6-digit code") },
            placeholder = { Text("123456") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(220.dp)
        )
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (secondsLeft > 0) "Resend SMS in 0:${secondsLeft.toString().padStart(2, '0')}" else "Didn't receive code? Tap Resend (test code: 123456)",
            fontSize = 13.sp,
            color = Color(0xFF667781),
            modifier = Modifier.clickable(enabled = secondsLeft == 0) { secondsLeft = 60 }
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onVerify(otpCode) },
            enabled = otpCode.length == 6,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            modifier = Modifier.width(160.dp).height(46.dp)
        ) {
            Text("Verify", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ProfileSetupScreen(onComplete: (String, String, Uri?) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("Hey there! I am using GlobalChat.") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }

    val previewBitmap = remember(avatarUri) {
        avatarUri?.let {
            MediaHelper.uriToCompressedBase64(context, it, 300)?.let(MediaHelper::decodeBase64ToImage)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Profile info", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WaGreen)
        Spacer(Modifier.height(8.dp))
        Text("Please provide your name and an optional profile photo", fontSize = 13.sp, color = Color(0xFF667781))
        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFFE9EDEF))
                .clickable {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(previewBitmap, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.AddAPhoto, null, tint = Color(0xFF667781), modifier = Modifier.size(38.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Type your name here") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            label = { Text("About") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onComplete(name, about, avatarUri) },
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaBrightGreen),
            modifier = Modifier.width(150.dp).height(46.dp)
        ) {
            Text("Next", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}