package com.example.equal_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.equal_plus.data.local.entity.KnownContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownContactDao {

    @Query("SELECT * FROM known_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<KnownContactEntity>>

    @Query("SELECT * FROM known_contacts WHERE number = :number LIMIT 1")
    suspend fun findContactByNumber(number: String): KnownContactEntity?

    @Query("SELECT * FROM known_contacts WHERE number LIKE '%' || :numberSnippet LIMIT 1")
    suspend fun findContactBySuffix(numberSnippet: String): KnownContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<KnownContactEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: KnownContactEntity): Long

    @Query("DELETE FROM known_contacts")
    suspend fun clearAllContacts(): Int

    @Query("SELECT COUNT(*) FROM known_contacts")
    suspend fun getContactCount(): Int
}
