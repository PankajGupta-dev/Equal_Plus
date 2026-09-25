package com.example.equal_plus

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.equal_plus.data.local.AuthDataStore
import com.example.equal_plus.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope
import com.example.equal_plus.data.local.PolicyDataStore
import com.example.equal_plus.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val policyDataStore = PolicyDataStore(applicationContext)
        val isComplete = runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500) {
                    policyDataStore.isOnboardingComplete.first()
                }
            }
        }.getOrNull() ?: false

        if (!isComplete) {
            OnboardingActivity.start(this)
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.liveAiCallFragment,
            R.id.callHistoryFragment,
            R.id.aiPolicyFragment
        )

        val appBarConfiguration = AppBarConfiguration(topLevelDestinations)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on detail / auth screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.conversationDetailsFragment,
                R.id.phoneVerificationFragment,
                R.id.otpEntryFragment -> binding.bottomNav.visibility = View.GONE
                else -> binding.bottomNav.visibility = View.VISIBLE
            }
        }

        // ── Auth gate ────────────────────────────────────────────────────────
        // Read is_verified once on startup.  If already verified, immediately
        // skip the auth back-stack and navigate straight to HomeFragment.
        // This runs AFTER the nav graph is wired (so the NavController is ready).
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val authDataStore = AuthDataStore(applicationContext)
                val isVerified = authDataStore.isVerified.first()
                if (isVerified) {
                    navController.navigate(
                        R.id.homeFragment,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, inclusive = true)
                            .build()
                    )
                }
                // else: stay on phoneVerificationFragment (graph start destination)
            }
        }
    }
}
