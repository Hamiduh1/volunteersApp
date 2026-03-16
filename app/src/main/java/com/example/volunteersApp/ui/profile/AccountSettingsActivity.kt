package com.example.volunteersApp.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class AccountSettingsActivity : ComponentActivity() {

    private val viewModel: AccountSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolunteersAppTheme {
                AccountSettingsNavHost()
            }
        }
    }

    @Composable
    private fun AccountSettingsNavHost() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "settings_main") {
            composable("settings_main") {
                AccountSettingsScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("change_username") {
                val vm: ChangeUserNameViewModel = viewModel()
                ChangeUserNameScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSuccess = {
                        Toast.makeText(this@AccountSettingsActivity, "Username updated!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    viewModel = vm
                )
            }
            composable("change_password") {
                val vm: ChangePasswordViewModel = viewModel()
                ChangePasswordScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSuccess = {
                        Toast.makeText(this@AccountSettingsActivity, "Password updated!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    viewModel = vm
                )
            }
            composable("change_phone") {
                val vm: ChangePhoneNumberViewModel = viewModel()
                ChangePhoneNumberScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSuccess = {
                        Toast.makeText(this@AccountSettingsActivity, "Phone number updated!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    viewModel = vm
                )
            }
        }
    }
}
