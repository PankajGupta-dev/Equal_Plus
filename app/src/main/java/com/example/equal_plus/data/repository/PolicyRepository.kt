package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.PolicyDataStore
import com.example.equal_plus.data.model.CategoryPolicy
import com.example.equal_plus.data.model.PolicyCategory
import kotlinx.coroutines.flow.Flow

interface PolicyRepository {
    val isGlobalScreeningEnabled: Flow<Boolean>
    fun getPolicyForCategory(category: PolicyCategory): Flow<CategoryPolicy>
    fun getAllPolicies(): Flow<List<CategoryPolicy>>
    suspend fun updatePolicy(policy: CategoryPolicy)
    suspend fun setGlobalScreeningEnabled(enabled: Boolean)
    suspend fun resetCategoryPolicy(category: PolicyCategory)
    suspend fun resetAllPolicies()
}

class PolicyRepositoryImpl(
    private val policyDataStore: PolicyDataStore
) : PolicyRepository {

    override val isGlobalScreeningEnabled: Flow<Boolean> =
        policyDataStore.isGlobalScreeningEnabled

    override fun getPolicyForCategory(category: PolicyCategory): Flow<CategoryPolicy> =
        policyDataStore.getPolicyForCategory(category)

    override fun getAllPolicies(): Flow<List<CategoryPolicy>> =
        policyDataStore.getAllPolicies()

    override suspend fun updatePolicy(policy: CategoryPolicy) =
        policyDataStore.updatePolicy(policy)

    override suspend fun setGlobalScreeningEnabled(enabled: Boolean) =
        policyDataStore.setGlobalScreeningEnabled(enabled)

    override suspend fun resetCategoryPolicy(category: PolicyCategory) =
        policyDataStore.resetCategoryPolicy(category)

    override suspend fun resetAllPolicies() =
        policyDataStore.resetAllPolicies()
}
