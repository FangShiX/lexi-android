package com.finisdiscago.leximvp.core.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationPolicyTest {
    @Test
    fun postsOnlyWhenTasksExistAndPermissionIsGranted() {
        assertFalse(ReminderNotificationPolicy.shouldPost(0, true))
        assertFalse(ReminderNotificationPolicy.shouldPost(3, false))
        assertTrue(ReminderNotificationPolicy.shouldPost(3, true))
    }
}
