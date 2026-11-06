package com.example.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var count by remember { mutableStateOf(0) }
            androidx.compose.material3.Surface {
                androidx.compose.material3.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(text = "Count: $count", fontSize = 48.sp)
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.padding(16.dp))
                    Button(onClick = { count++ }) {
                        Text(text = "Increment", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
