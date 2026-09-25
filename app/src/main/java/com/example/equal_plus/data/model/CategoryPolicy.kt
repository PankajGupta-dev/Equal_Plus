package com.example.equal_plus.data.model

data class CategoryPolicy(
    val category: PolicyCategory,
    val autoBlock: Boolean = false,
    val autoScreen: Boolean = true,
    val autoRecord: Boolean = false,
    val minRiskThreshold: RiskLevel = RiskLevel.MEDIUM,
    val notificationsEnabled: Boolean = true,
    val customPrompt: String = "",
    val allowedNumbers: Set<String> = emptySet(),
    val blockedNumbers: Set<String> = emptySet()
)
