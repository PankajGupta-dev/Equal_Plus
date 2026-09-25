package com.example.equal_plus.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.equal_plus.data.model.CategoryPolicy
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.data.model.RiskLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "equal_plus_policy_settings")

class PolicyDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_GLOBAL_SCREENING_ENABLED = booleanPreferencesKey("key_global_screening_enabled")
        val KEY_GLOBAL_AUTO_BLOCK_SCAM = booleanPreferencesKey("key_global_auto_block_scam")
        val KEY_GLOBAL_DEFAULT_RISK_THRESHOLD = stringPreferencesKey("key_global_default_risk_threshold")

        private fun autoBlockKey(category: PolicyCategory) =
            booleanPreferencesKey("policy_${category.name.lowercase()}_auto_block")

        private fun autoScreenKey(category: PolicyCategory) =
            booleanPreferencesKey("policy_${category.name.lowercase()}_auto_screen")

        private fun autoRecordKey(category: PolicyCategory) =
            booleanPreferencesKey("policy_${category.name.lowercase()}_auto_record")

        private fun riskThresholdKey(category: PolicyCategory) =
            stringPreferencesKey("policy_${category.name.lowercase()}_risk_threshold")

        private fun notificationsKey(category: PolicyCategory) =
            booleanPreferencesKey("policy_${category.name.lowercase()}_notifications")

        private fun customPromptKey(category: PolicyCategory) =
            stringPreferencesKey("policy_${category.name.lowercase()}_custom_prompt")

        private fun allowedNumbersKey(category: PolicyCategory) =
            stringSetPreferencesKey("policy_${category.name.lowercase()}_allowed_numbers")

        private fun blockedNumbersKey(category: PolicyCategory) =
            stringSetPreferencesKey("policy_${category.name.lowercase()}_blocked_numbers")
    }

    val isGlobalScreeningEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_GLOBAL_SCREENING_ENABLED] ?: true
        }

    fun getPolicyForCategory(category: PolicyCategory): Flow<CategoryPolicy> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val defaultPolicy = getDefaultPolicyForCategory(category)
            CategoryPolicy(
                category = category,
                autoBlock = preferences[autoBlockKey(category)] ?: defaultPolicy.autoBlock,
                autoScreen = preferences[autoScreenKey(category)] ?: defaultPolicy.autoScreen,
                autoRecord = preferences[autoRecordKey(category)] ?: defaultPolicy.autoRecord,
                minRiskThreshold = preferences[riskThresholdKey(category)]?.let {
                    try { RiskLevel.valueOf(it) } catch (_: Exception) { defaultPolicy.minRiskThreshold }
                } ?: defaultPolicy.minRiskThreshold,
                notificationsEnabled = preferences[notificationsKey(category)] ?: defaultPolicy.notificationsEnabled,
                customPrompt = preferences[customPromptKey(category)] ?: defaultPolicy.customPrompt,
                allowedNumbers = preferences[allowedNumbersKey(category)] ?: defaultPolicy.allowedNumbers,
                blockedNumbers = preferences[blockedNumbersKey(category)] ?: defaultPolicy.blockedNumbers
            )
        }

    fun getAllPolicies(): Flow<List<CategoryPolicy>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            PolicyCategory.entries.map { category ->
                val defaultPolicy = getDefaultPolicyForCategory(category)
                CategoryPolicy(
                    category = category,
                    autoBlock = preferences[autoBlockKey(category)] ?: defaultPolicy.autoBlock,
                    autoScreen = preferences[autoScreenKey(category)] ?: defaultPolicy.autoScreen,
                    autoRecord = preferences[autoRecordKey(category)] ?: defaultPolicy.autoRecord,
                    minRiskThreshold = preferences[riskThresholdKey(category)]?.let {
                        try { RiskLevel.valueOf(it) } catch (_: Exception) { defaultPolicy.minRiskThreshold }
                    } ?: defaultPolicy.minRiskThreshold,
                    notificationsEnabled = preferences[notificationsKey(category)] ?: defaultPolicy.notificationsEnabled,
                    customPrompt = preferences[customPromptKey(category)] ?: defaultPolicy.customPrompt,
                    allowedNumbers = preferences[allowedNumbersKey(category)] ?: defaultPolicy.allowedNumbers,
                    blockedNumbers = preferences[blockedNumbersKey(category)] ?: defaultPolicy.blockedNumbers
                )
            }
        }

    suspend fun updatePolicy(policy: CategoryPolicy) {
        dataStore.edit { preferences ->
            preferences[autoBlockKey(policy.category)] = policy.autoBlock
            preferences[autoScreenKey(policy.category)] = policy.autoScreen
            preferences[autoRecordKey(policy.category)] = policy.autoRecord
            preferences[riskThresholdKey(policy.category)] = policy.minRiskThreshold.name
            preferences[notificationsKey(policy.category)] = policy.notificationsEnabled
            preferences[customPromptKey(policy.category)] = policy.customPrompt
            preferences[allowedNumbersKey(policy.category)] = policy.allowedNumbers
            preferences[blockedNumbersKey(policy.category)] = policy.blockedNumbers
        }
    }

    suspend fun setGlobalScreeningEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_GLOBAL_SCREENING_ENABLED] = enabled
        }
    }

    suspend fun resetCategoryPolicy(category: PolicyCategory) {
        val defaultPolicy = getDefaultPolicyForCategory(category)
        updatePolicy(defaultPolicy)
    }

    suspend fun resetAllPolicies() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun getDefaultPolicyForCategory(category: PolicyCategory): CategoryPolicy {
        return when (category) {
            PolicyCategory.SCAM -> CategoryPolicy(
                category = PolicyCategory.SCAM,
                autoBlock = true,
                autoScreen = true,
                autoRecord = true,
                minRiskThreshold = RiskLevel.HIGH,
                notificationsEnabled = true,
                customPrompt = "Identify if caller is a scammer or fraudulent party."
            )
            PolicyCategory.TELEMARKETING -> CategoryPolicy(
                category = PolicyCategory.TELEMARKETING,
                autoBlock = false,
                autoScreen = true,
                autoRecord = false,
                minRiskThreshold = RiskLevel.MEDIUM,
                notificationsEnabled = true,
                customPrompt = "Ask for caller purpose and offer to decline promotional offers."
            )
            PolicyCategory.DELIVERY -> CategoryPolicy(
                category = PolicyCategory.DELIVERY,
                autoBlock = false,
                autoScreen = false,
                autoRecord = false,
                minRiskThreshold = RiskLevel.LOW,
                notificationsEnabled = true,
                customPrompt = "Ask delivery details and where to leave the package."
            )
            PolicyCategory.FINANCIAL -> CategoryPolicy(
                category = PolicyCategory.FINANCIAL,
                autoBlock = false,
                autoScreen = true,
                autoRecord = true,
                minRiskThreshold = RiskLevel.MEDIUM,
                notificationsEnabled = true,
                customPrompt = "Verify financial institution name and purpose without disclosing credentials."
            )
            PolicyCategory.UNKNOWN_CALLER -> CategoryPolicy(
                category = PolicyCategory.UNKNOWN_CALLER,
                autoBlock = false,
                autoScreen = true,
                autoRecord = false,
                minRiskThreshold = RiskLevel.LOW,
                notificationsEnabled = true,
                customPrompt = "Politely ask who is calling and reason for calling."
            )
            PolicyCategory.PERSONAL -> CategoryPolicy(
                category = PolicyCategory.PERSONAL,
                autoBlock = false,
                autoScreen = false,
                autoRecord = false,
                minRiskThreshold = RiskLevel.LOW,
                notificationsEnabled = true,
                customPrompt = ""
            )
            PolicyCategory.GENERAL -> CategoryPolicy(
                category = PolicyCategory.GENERAL,
                autoBlock = false,
                autoScreen = true,
                autoRecord = false,
                minRiskThreshold = RiskLevel.MEDIUM,
                notificationsEnabled = true,
                customPrompt = "Screen call on behalf of the user."
            )
        }
    }
}
