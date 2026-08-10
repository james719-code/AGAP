package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.ProfileDao
import com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity
import com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity
import com.travelingsalesman.agap.data.local.entity.UserProfileEntity
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
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

    suspend fun deleteEmergencyContact(userId: Long, contactId: Long) {
        profileDao.deleteEmergencyContact(userId, contactId)
    }

    suspend fun replaceEmergencyContacts(userId: Long, contacts: List<EmergencyContactDto>) {
        profileDao.deleteEmergencyContacts(userId)
        contacts.forEach { profileDao.insertEmergencyContact(it.toEntity(userId)) }
    }

    suspend fun saveUserSnapshot(user: UserDto) {
        profileDao.insertUserProfile(
            UserProfileEntity(
                userId = user.id,
                fullName = user.name ?: user.username,
                dob = user.birthday.orEmpty(),
                gender = user.gender.orEmpty(),
                phone = user.phoneNumber.orEmpty(),
                email = user.email.orEmpty(),
                address = "",
                citizenId = user.displayId,
                memberSince = user.createdAt.orEmpty(),
                username = user.username,
                role = user.role,
                deviceId = user.deviceId,
                agencyId = user.agencyId,
                medicalInfo = user.medicalInfo,
                nationalId = user.nationalId,
                accountStatus = user.accountStatus,
                isVerified = user.isVerified,
                imageUrl = user.imageUrl
            )
        )
        profileDao.insertMedicalInfo(
            MedicalInfoEntity(
                userId = user.id,
                bloodType = "",
                allergies = "",
                conditions = user.medicalInfo.orEmpty(),
                medications = "",
                emergencyNotes = ""
            )
        )
        profileDao.deleteEmergencyContacts(user.id)
        user.emergencyContacts.forEach { contact ->
            profileDao.insertEmergencyContact(contact.toEntity())
        }
    }

    suspend fun clearUser(userId: Long) = profileDao.deleteUserData(userId)

    private fun EmergencyContactDto.toEntity(forUserId: Long = userId) = EmergencyContactEntity(
        contactId = id,
        userId = forUserId,
        name = contactName,
        phone = phoneNumber,
        relationship = relationship,
        isPrimary = isPrimary
    )
}
