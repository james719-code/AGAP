package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleVisibilityPolicyTest {

    private val policy = BleVisibilityPolicy()

    @Test
    fun citizen_onlySeesOwnReports() {
        assertTrue(policy.shouldShowInUi(UserRole.CITIZEN, isOwnReport = true, emergencyType = "MEDICAL"))
        assertFalse(policy.shouldShowInUi(UserRole.CITIZEN, isOwnReport = false, emergencyType = "MEDICAL"))
    }

    @Test
    fun admin_seesAllReports() {
        assertTrue(policy.shouldShowInUi(UserRole.ADMIN, isOwnReport = true, emergencyType = "FIRE"))
        assertTrue(policy.shouldShowInUi(UserRole.ADMIN, isOwnReport = false, emergencyType = "FIRE"))
    }

    @Test
    fun responder_filtersByAgencyRelevance() {
        assertTrue(policy.shouldShowInUi(UserRole.RESPONDER, isOwnReport = false, emergencyType = "FIRE", responderAgencyType = "FIRE"))
        assertFalse(policy.shouldShowInUi(UserRole.RESPONDER, isOwnReport = false, emergencyType = "CRIME", responderAgencyType = "FIRE"))
    }
}
