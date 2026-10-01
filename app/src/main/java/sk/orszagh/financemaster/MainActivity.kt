package sk.orszagh.financemaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import sk.orszagh.financemaster.ui.FinanceMasterApp
import sk.orszagh.financemaster.ui.ReceiptsViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<ReceiptsViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ReceiptsViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return ReceiptsViewModel((application as FinanceMasterApplication).repository) as T
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceMasterApp(model, (application as FinanceMasterApplication).recoveryError)
        }
    }
}
