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
    BLOCK_CALL,
    ANSWER_CALL,
    END_CALL,
    RECORD_AUDIO,
    WARN_USER,
    SCREEN_CALL,
    SEND_SMS,
    NOTIFY_USER,
    ALLOW_CALL,
    CREATE_REMINDER,
    CREATE_CALENDAR_EVENT,
    SAVE_DELIVERY_INSTRUCTION,
    REQUEST_CALLBACK
}

enum class NextAction {
    ASK,
    RESOLVE,
    TERMINATE,
    ESCALATE
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
