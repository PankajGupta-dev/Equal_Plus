package com.example.equal_plus

import com.example.equal_plus.data.model.CategoryPolicy
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.data.repository.PolicyRepository
import com.example.equal_plus.ui.policy.AiPolicyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiPolicyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakePolicyRepo : PolicyRepository {
        val globalEnabledFlow = MutableStateFlow(true)
        val policiesFlow = MutableStateFlow(
            listOf(
                CategoryPolicy(category = PolicyCategory.UNKNOWN_CALLER, autoScreen = true, autoBlock = false),
                CategoryPolicy(category = PolicyCategory.FINANCIAL, autoScreen = true, autoRecord = true),
                CategoryPolicy(category = PolicyCategory.TELEMARKETING, autoScreen = true, autoBlock = true)
            )
        )

        override val isGlobalScreeningEnabled: Flow<Boolean> = globalEnabledFlow

        override fun getPolicyForCategory(category: PolicyCategory): Flow<CategoryPolicy> =
            MutableStateFlow(policiesFlow.value.find { it.category == category } ?: CategoryPolicy(category))

        override fun getAllPolicies(): Flow<List<CategoryPolicy>> = policiesFlow

        override suspend fun updatePolicy(policy: CategoryPolicy) {
            val current = policiesFlow.value.toMutableList()
            current.removeAll { it.category == policy.category }
            current.add(policy)
            policiesFlow.value = current
        }

        override suspend fun setGlobalScreeningEnabled(enabled: Boolean) {
            globalEnabledFlow.value = enabled
        }

        override suspend fun resetCategoryPolicy(category: PolicyCategory) {
            updatePolicy(CategoryPolicy(category))
        }

        override suspend fun resetAllPolicies() {
            policiesFlow.value = listOf(
                CategoryPolicy(category = PolicyCategory.UNKNOWN_CALLER, autoScreen = true, autoBlock = false),
                CategoryPolicy(category = PolicyCategory.FINANCIAL, autoScreen = true, autoRecord = true)
            )
            globalEnabledFlow.value = true
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPolicyViewModelTogglesAndPersistence() = runTest {
        val fakeRepo = FakePolicyRepo()
        val viewModel = AiPolicyViewModel(fakeRepo)

        // 1. Initial State
        assertTrue(viewModel.uiState.value.isGlobalScreeningEnabled)
        assertEquals(true, viewModel.uiState.value.policies[PolicyCategory.UNKNOWN_CALLER]?.autoScreen)

        // 2. Toggle Master Screening
        viewModel.toggleGlobalScreening(false)
        assertFalse(viewModel.uiState.value.isGlobalScreeningEnabled)

        // 3. Update Category Policy (e.g. Unknown Callers autoBlock = true)
        viewModel.updatePolicy(PolicyCategory.UNKNOWN_CALLER, autoBlock = true)
        val updatedUnknown = viewModel.uiState.value.policies[PolicyCategory.UNKNOWN_CALLER]
        assertEquals(true, updatedUnknown?.autoBlock)

        // 4. Reset All To Defaults
        viewModel.resetAllToDefaults()
        assertTrue(viewModel.uiState.value.isGlobalScreeningEnabled)
    }
}
