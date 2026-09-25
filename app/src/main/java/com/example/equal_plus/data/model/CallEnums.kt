package com.example.equal_plus.data.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}

enum class CallStatus {
    IDLE,
    RINGING,
    SCREENING,
    ACTIVE,
    COMPLETED,
    BLOCKED,
    MISSED,
    REJECTED
}

enum class RiskLevel {
    UNKNOWN,
    SAFE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class SpeakerType {
    CALLER,
    ASSISTANT,
    USER,
    SYSTEM
}

enum class ActionType {
    BLOCK_NUMBER,
    ANSWER_CALL,
    END_CALL,
    RECORD_AUDIO,
    WARN_USER,
    SCREEN_CALL,
    SEND_SMS,
    NOTIFY_USER,
    ALLOW_CALL
}

enum class ActionStatus {
    PENDING,
    IN_PROGRESS,
    EXECUTED,
    FAILED,
    CANCELLED
}

enum class PolicyCategory {
    SCAM,
    TELEMARKETING,
    DELIVERY,
    FINANCIAL,
    UNKNOWN_CALLER,
    PERSONAL,
    GENERAL
}
