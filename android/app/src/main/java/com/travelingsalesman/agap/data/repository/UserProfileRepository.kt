package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.ProfileDao
import com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity
import com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity
import com.travelingsalesman.agap.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    fun getUserProfile(userId: Long): Flow<UserProfileEntity?> = profileDao.getUserProfileFlow(userId)
    
    fun getMedicalInfo(userId: Long): Flow<MedicalInfoEntity?> = profileDao.getMedicalInfoFlow(userId)
    
    fun getEmergencyContacts(userId: Long): Flow<List<EmergencyContactEntity>> = profileDao.getEmergencyContactsFlow(userId)
    
    suspend fun saveUserProfile(profile: UserProfileEntity) {
        profileDao.insertUserProfile(profile)
    }
    
    suspend fun saveMedicalInfo(info: MedicalInfoEntity) {
        profileDao.insertMedicalInfo(info)
    }
    
    suspend fun saveEmergencyContact(contact: EmergencyContactEntity) {
        profileDao.insertEmergencyContact(contact)
    }
}
