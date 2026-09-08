package cn.buddykeymanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.buddykeymanager.app.theme.BuddyTheme
import cn.buddykeymanager.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BuddyTheme {
                AppRoot()
            }
        }
    }
}
