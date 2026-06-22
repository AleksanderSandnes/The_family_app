package com.example.mainactivity.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Single entry point to all persistence. Hashes credentials before storage as outlined
 * in the system design document (server-side hashing of user secrets).
 */
class FamilyRepository(
    private val db: AppDatabase,
    val session: SessionManager
) {
    val userDao get() = db.userDao()
    val familyDao get() = db.familyDao()
    val shoppingDao get() = db.shoppingDao()
    val mealPlanDao get() = db.mealPlanDao()
    val calendarDao get() = db.calendarDao()
    val birthdayDao get() = db.birthdayDao()
    val wishlistDao get() = db.wishlistDao()
    val chatDao get() = db.chatDao()

    val currentUserId: Flow<Long?> = session.currentUserId

    val themeMode: Flow<ThemeMode> = session.themeMode

    suspend fun setThemeMode(mode: ThemeMode) = session.setThemeMode(mode)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUser: Flow<UserEntity?> = currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(null) else userDao.observe(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentFamily: Flow<FamilyEntity?> = currentUser.flatMapLatest { user ->
        val familyId = user?.familyId
        if (familyId == null) flowOf(null) else familyDao.observe(familyId)
    }

    // ---- Auth ----
    suspend fun register(
        name: String,
        email: String,
        password: String,
        birthday: String,
        mobile: String
    ): Result<Long> {
        if (userDao.countByEmail(email.trim().lowercase()) > 0) {
            return Result.failure(IllegalStateException("An account with that email already exists."))
        }
        val id = userDao.insert(
            UserEntity(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = hash(password),
                birthday = birthday,
                mobile = mobile,
                avatarColor = palette(name)
            )
        )
        session.signIn(id)
        return Result.success(id)
    }

    suspend fun login(email: String, password: String): Result<Long> {
        val user = userDao.findByEmail(email.trim().lowercase())
            ?: return Result.failure(IllegalStateException("No account found for that email."))
        if (user.password != hash(password)) {
            return Result.failure(IllegalStateException("Incorrect password."))
        }
        session.signIn(user.id)
        return Result.success(user.id)
    }

    suspend fun signOut() = session.signOut()

    // ---- Family ----
    suspend fun createFamily(name: String, password: String, userId: Long): Long {
        val familyId = familyDao.insert(FamilyEntity(name = name.trim(), password = password, adminId = userId))
        userDao.findById(userId)?.let { userDao.update(it.copy(familyId = familyId)) }
        return familyId
    }

    suspend fun joinFamily(name: String, password: String, userId: Long): Result<Long> {
        val family = familyDao.authenticate(name.trim(), password)
            ?: return Result.failure(IllegalStateException("Family name or code is incorrect."))
        userDao.findById(userId)?.let { userDao.update(it.copy(familyId = family.id)) }
        return Result.success(family.id)
    }

    suspend fun leaveFamily(userId: Long) {
        userDao.findById(userId)?.let { userDao.update(it.copy(familyId = null)) }
    }

    suspend fun updateProfile(user: UserEntity) = userDao.update(user)

    companion object {
        @Volatile private var INSTANCE: FamilyRepository? = null

        fun get(context: Context): FamilyRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FamilyRepository(
                    AppDatabase.get(context),
                    SessionManager(context.applicationContext)
                ).also { INSTANCE = it }
            }

        fun hash(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private val avatarColors = intArrayOf(
            0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
            0xFFF59E0B.toInt(), 0xFF8B5CF6.toInt(), 0xFF06B6D4.toInt(),
            0xFFEF4444.toInt(), 0xFF10B981.toInt()
        )

        fun palette(seed: String): Int =
            avatarColors[(seed.hashCode() and 0x7FFFFFFF) % avatarColors.size]
    }
}
