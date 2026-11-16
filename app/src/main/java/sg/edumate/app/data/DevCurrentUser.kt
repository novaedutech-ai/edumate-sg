package sg.edumate.app.data

import sg.edumate.domain.model.Role
import sg.edumate.domain.usecase.CurrentUser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Development identity. Production replaces this with the OIDC session (Google Workspace for
 * Education or the school's IdP) exchanged for a backend token; role and schoolId come from claims.
 */
@Singleton
class DevCurrentUser @Inject constructor() : CurrentUser {
    override val id = "teacher-demo"
    override var role: Role = Role.TEACHER
    override val schoolId = "school-demo"
}
