package dev.ipf.whitenoise.android.state

import dev.ipf.marmotkit.AppBlobEndpointFfi
import dev.ipf.marmotkit.AppGroupEncryptedMediaComponentFfi
import dev.ipf.marmotkit.AppGroupMemberRecordFfi
import dev.ipf.marmotkit.AppGroupRecordFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAddableGroupsTest {
    @Test
    fun includesOnlyAdminGroupsWhereTargetIsMissing() {
        val self = "self"
        val target = "target"
        val eligible = item("eligible", name = "Friends", admins = listOf(self), members = listOf(self, "bob"))
        val notAdmin =
            item("not-admin", name = "Nope", admins = emptyList(), members = listOf(self, "bob"))
        val alreadyMember =
            item("already", name = "Already", admins = listOf(self), members = listOf(self, target))
        val pendingInvite =
            item(
                "pending",
                name = "Pending",
                admins = listOf(self),
                members = listOf(self),
                pending = true,
            )
        val directMessage = item("dm", name = "", admins = listOf(self), members = listOf(self, "bob"))

        assertEquals(
            listOf("eligible"),
            profileAddableGroupItems(
                items = listOf(eligible, notAdmin, alreadyMember, pendingInvite, directMessage),
                targetAccountIdHex = target,
                activeAccountIdHex = self,
            ).map { it.group.groupIdHex },
        )
    }

    @Test
    fun selfProfileHasNoAddableGroups() {
        assertTrue(
            profileAddableGroupItems(
                items =
                    listOf(
                        item(
                            "group",
                            name = "Friends",
                            admins = listOf("self"),
                            members = listOf("self", "bob"),
                        ),
                    ),
                targetAccountIdHex = "SELF",
                activeAccountIdHex = "self",
            ).isEmpty(),
        )
    }

    private fun item(
        groupId: String,
        name: String,
        admins: List<String>,
        members: List<String>,
        pending: Boolean = false,
    ) = ChatListItem(
        group = group(groupId, name, admins, pending),
        latest = null,
        otherMemberAccount = null,
        memberCount = members.size,
        memberSnapshot = GroupMemberSnapshot(members.map { member(it) }),
    )

    private fun group(
        groupId: String,
        name: String,
        admins: List<String>,
        pending: Boolean,
    ) = AppGroupRecordFfi(
        groupIdHex = groupId,
        endpoint = "endpoint",
        name = name,
        description = "",
        admins = admins,
        relays = listOf("wss://relay.example"),
        nostrGroupIdHex = "nostr-$groupId",
        avatarUrl = null,
        avatarDim = null,
        avatarThumbhash = null,
        encryptedMedia = encryptedMedia(),
        archived = false,
        pendingConfirmation = pending,
        welcomerAccountIdHex = null,
        viaWelcomeMessageIdHex = null,
        disappearingMessageSecs = 0uL,
    )

    private fun member(memberId: String) =
        AppGroupMemberRecordFfi(
            memberIdHex = memberId,
            account = memberId,
            local = false,
        )

    private fun encryptedMedia() =
        AppGroupEncryptedMediaComponentFfi(
            componentId = 0x8008u,
            component = "marmot.group.encrypted-media.v1",
            required = true,
            mediaFormat = "encrypted-media-v1",
            allowedLocatorKinds = listOf("blossom-v1"),
            defaultBlobEndpoints =
                listOf(
                    AppBlobEndpointFfi(
                        locatorKind = "blossom-v1",
                        baseUrl = "https://blossom.primal.net",
                    ),
                ),
        )
}
