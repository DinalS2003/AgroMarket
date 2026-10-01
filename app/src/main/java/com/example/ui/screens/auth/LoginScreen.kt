package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onSessionReady: () -> Unit,
    onNeedsOnboarding: () -> Unit
) {
    val state by viewModel.authState.collectAsState()

    if (state.isSessionReady) {
        onSessionReady()
        return
    }

    if (state.needsOnboarding) {
        onNeedsOnboarding()
        return
    }

    val scrollState = rememberScrollState()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            // Hero Icon
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Agriculture,
                        contentDescription = "AgroMarket Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "AgroMarket",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Text(
                text = "Sri Lankan Agricultural Marketplace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (!state.isOtpSent) {
                // Phone Entry Step
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Login with Mobile",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter your Sri Lankan mobile number to receive a 6-digit verification code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = state.phoneInput,
                            onValueChange = viewModel::onPhoneChange,
                            label = { Text("Mobile Number") },
                            placeholder = { Text("0771234567") },
                            leadingIcon = {
                                Icon(Icons.Filled.Phone, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = state.phoneError != null,
                            supportingText = {
                                if (state.phoneError != null) {
                                    Text(state.phoneError!!, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Format: 07XXXXXXXX or +947XXXXXXXX")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::requestOtp,
                            enabled = !state.isLoading && state.phoneInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_otp_button")
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send Verification Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // OTP Entry Step
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Verify Code",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter the 6-digit code sent to ${state.phoneInput}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!state.otpStatusMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.otpStatusMessage!!,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = state.otpCode,
                            onValueChange = viewModel::onOtpChange,
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("Enter 6-digit code") },
                            leadingIcon = {
                                Icon(Icons.Filled.Security, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.otpError != null,
                            supportingText = {
                                if (state.otpError != null) {
                                    Text(state.otpError!!, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Enter the code received on your phone")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::verifyOtp,
                            enabled = !state.isLoading && state.otpCode.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_otp_button")
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Verify & Login", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = viewModel::resetToPhoneInput,
                                modifier = Modifier.testTag("change_phone_button")
                            ) {
                                Text("Change Number")
                            }

                            if (state.resendCountdown > 0) {
                                Text(
                                    text = "Resend in ${state.resendCountdown}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                TextButton(
                                    onClick = viewModel::requestOtp,
                                    modifier = Modifier.testTag("resend_otp_button")
                                ) {
                                    Text("Resend Code")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.isSuspended) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Account Suspended",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.suspendedReason ?: "Your account has been suspended by administration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
}
