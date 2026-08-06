package com.travelingsalesman.agap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,
    
    @ColumnInfo(name = "full_name")
    val fullName: String,
    
    @ColumnInfo(name = "dob")
    val dob: String,
    
    @ColumnInfo(name = "gender")
    val gender: String,
    
    @ColumnInfo(name = "phone")
    val phone: String,
    
    @ColumnInfo(name = "email")
    val email: String,
    
    @ColumnInfo(name = "address")
    val address: String,
    
    @ColumnInfo(name = "citizen_id")
    val citizenId: String,
    
    @ColumnInfo(name = "member_since")
    val memberSince: String
)

@Entity(tableName = "medical_info")
data class MedicalInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,
    
    @ColumnInfo(name = "blood_type")
    val bloodType: String,
    
    @ColumnInfo(name = "allergies")
    val allergies: String,
    
    @ColumnInfo(name = "conditions")
    val conditions: String,
    
    @ColumnInfo(name = "medications")
    val medications: String,
    
    @ColumnInfo(name = "emergency_notes")
    val emergencyNotes: String
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "contact_id")
    val contactId: Long = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: Long,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "phone")
    val phone: String,
    
    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean
)
