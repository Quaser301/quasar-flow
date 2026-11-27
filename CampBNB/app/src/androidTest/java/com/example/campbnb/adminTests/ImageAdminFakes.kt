package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

/**
 * In-memory stand-ins shared by the admin image intent tests (US 03.03.01, US 03.06.01);
 * nothing here touches Firebase.
 */

import com.example.campbnb.EmptyLotteryRepository
import com.example.campbnb.EmptyRemovalRepository
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Notification
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel

class ImageFakeUserRepository(users: List<User>) : UserRepository() {
    val users = users.toMutableList()

    override fun findByDeviceId(deviceId: String, onSuccess: (User?) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(users.firstOrNull { it.id == deviceId })
    }

    override fun saveProfileImage(userId: String, image: String, onComplete: (Result<Exception?>) -> Unit) {
        val index = users.indexOfFirst { it.id == userId }
        users[index] = users[index].copy(profileImg = image)
        onComplete(Result.success(null))
    }

    override fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(users.toList())
    }
}

class ImageFakeCampsiteRepository(campsites: List<Campsite>) : CampsiteRepository() {
    val campsites = campsites.toMutableList()

    override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(campsites.toList())
    }

    override fun getById(campsiteId: String, onSuccess: (Campsite?) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(campsites.firstOrNull { it.id == campsiteId })
    }

    override fun saveCampsiteImage(campsiteId: String, image: String, onComplete: (Result<Exception?>) -> Unit) {
        val index = campsites.indexOfFirst { it.id == campsiteId }
        val old = campsites[index]
        val updated = Campsite(ownerId = old.ownerId, campName = old.campName, campImg = image)
        updated.id = old.id
        campsites[index] = updated
        onComplete(Result.success(null))
    }
}

class ImageFakeNotificationRepository : NotificationRepository {
    val saved = mutableListOf<Notification>()

    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> {
                saved.addAll(command.records)
                command.onDone.invoke(Result.success(Unit))
            }
            is NotificationCommand.Read -> command.onSuccess.invoke(saved.toList())
            is NotificationCommand.Watch -> command.onListening.invoke {}
            is NotificationCommand.MarkRead -> command.onDone.invoke(Result.success(Unit))
        }
    }
}

/** The admin's controller wired to the fakes, with an admin signed in. */
fun imageAdminController(
    userRepo: ImageFakeUserRepository,
    campsiteRepo: ImageFakeCampsiteRepository,
    notifications: ImageFakeNotificationRepository,
    signedIn: User
): AdminController {
    val userModel = UserModel()
    val userController = UserController(userModel, userRepo)
    userModel.setCurrentUser(signedIn)
    return AdminController(
        userController,
        userRepo,
        campsiteRepo,
        EmptyLotteryRepository(),
        NotificationController(notifications),
        EmptyRemovalRepository()
    )
}

val imageTestAdmin = User(deviceId = "admin-1", name = "Ada Admin", email = "ada@example.com", roles = listOf(Role.ADMIN))