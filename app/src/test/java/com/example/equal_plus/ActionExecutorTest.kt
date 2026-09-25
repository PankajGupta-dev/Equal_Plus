package com.example.equal_plus

import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutorTest {

    @Test
    fun `test ActionEntity creation for system actions`() {
        val action = ActionEntity(
            callId = "call_test_123",
            actionType = ActionType.ALLOW_CALL,
            status = ActionStatus.PENDING,
            description = "Allowing known call"
        )
        assertNotNull(action)
        assertEquals("call_test_123", action.callId)
        assertEquals(ActionType.ALLOW_CALL, action.actionType)
    }

    @Test
    fun `test ActionType coverage for automation pipeline`() {
        val types = ActionType.values()
        assertTrue(types.contains(ActionType.CREATE_REMINDER))
        assertTrue(types.contains(ActionType.CREATE_CALENDAR_EVENT))
        assertTrue(types.contains(ActionType.SAVE_DELIVERY_INSTRUCTION))
        assertTrue(types.contains(ActionType.BLOCK_NUMBER))
        assertTrue(types.contains(ActionType.SEND_SMS))
        assertTrue(types.contains(ActionType.NOTIFY_USER))
    }
}
