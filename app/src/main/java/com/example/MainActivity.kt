package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.TradeRepository
import com.example.ui.BotViewModel
import com.example.ui.BotViewModelFactory
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Room Database & Repository
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = TradeRepository(database.tradeDao())

    // Initialize ViewModel using Factory
    val viewModel = ViewModelProvider(
        this,
        BotViewModelFactory(application, repository)
    )[BotViewModel::class.java]

    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          DashboardScreen(
              viewModel = viewModel,
              modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
