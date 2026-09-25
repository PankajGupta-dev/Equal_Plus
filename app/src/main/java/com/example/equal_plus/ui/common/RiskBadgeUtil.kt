package com.example.equal_plus.ui.common

import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.equal_plus.R
import com.example.equal_plus.data.model.RiskLevel

object RiskBadgeUtil {

    fun applyRiskBadge(textView: TextView, riskLevel: RiskLevel) {
        val context = textView.context
        val bgDrawableRes = when (riskLevel) {
            RiskLevel.SAFE -> R.drawable.badge_risk_safe
            RiskLevel.LOW -> R.drawable.badge_risk_low
            RiskLevel.MEDIUM -> R.drawable.badge_risk_medium
            RiskLevel.HIGH -> R.drawable.badge_risk_high
            RiskLevel.CRITICAL -> R.drawable.badge_risk_critical
            RiskLevel.UNKNOWN -> R.drawable.badge_risk_unknown
        }

        val textColor = when (riskLevel) {
            RiskLevel.SAFE -> Color.parseColor("#2E7D32") // Dark Green
            RiskLevel.LOW -> Color.parseColor("#0277BD") // Dark Light Blue
            RiskLevel.MEDIUM -> Color.parseColor("#E65100") // Deep Orange
            RiskLevel.HIGH -> Color.parseColor("#C62828") // Red
            RiskLevel.CRITICAL -> Color.parseColor("#FFFFFF") // White on deep red
            RiskLevel.UNKNOWN -> Color.parseColor("#616161") // Grey
        }

        textView.background = ContextCompat.getDrawable(context, bgDrawableRes)
        textView.setTextColor(textColor)
        textView.text = getRiskLabel(riskLevel)
    }

    fun getRiskLabel(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "SAFE CALLER"
            RiskLevel.LOW -> "LOW RISK"
            RiskLevel.MEDIUM -> "SUSPICIOUS / UNVERIFIED"
            RiskLevel.HIGH -> "HIGH RISK / SCAM DETECTED"
            RiskLevel.CRITICAL -> "CRITICAL SCAM THREAT"
            RiskLevel.UNKNOWN -> "ANALYZING RISK..."
        }
    }

    fun getRiskColor(riskLevel: RiskLevel): Int {
        return when (riskLevel) {
            RiskLevel.SAFE -> Color.parseColor("#2E7D32")
            RiskLevel.LOW -> Color.parseColor("#0277BD")
            RiskLevel.MEDIUM -> Color.parseColor("#E65100")
            RiskLevel.HIGH -> Color.parseColor("#C62828")
            RiskLevel.CRITICAL -> Color.parseColor("#B71C1C")
            RiskLevel.UNKNOWN -> Color.parseColor("#616161")
        }
    }
}
