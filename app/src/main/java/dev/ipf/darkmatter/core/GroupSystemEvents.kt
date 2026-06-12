package dev.ipf.darkmatter.core

import org.json.JSONObject

/**
 * Parsed content of a kind-1210 group system row (schema v1). These rows are
 * synthesized from MLS-authenticated group state and must never render as
 * chat bubbles; see spec/foundation/application-messages.md.
 */
data class GroupSystemEvent(
    val systemType: String,
    val text: String,
    val actor: String?,
    val subject: String?,
    val name: String?,
)

/**
 * Localized format strings for group system rows, threaded in from string
 * resources the same way as [MessageTextCopy]. Active forms take the actor's
 * resolved display name; passive forms cover unattributed changes (e.g. a
 * convergence reorg where the committer isn't resolved).
 */
data class GroupSystemCopy(
    val memberAddedFormat: String,
    val memberAddedPassiveFormat: String,
    val memberRemovedFormat: String,
    val memberRemovedPassiveFormat: String,
    val memberLeftFormat: String,
    val adminAddedFormat: String,
    val adminAddedPassiveFormat: String,
    val adminRemovedFormat: String,
    val adminRemovedPassiveFormat: String,
    val renamedFormat: String,
    val renamedPassiveFormat: String,
    val avatarChangedFormat: String,
    val avatarChangedPassive: String,
    val someone: String,
    val fallback: String,
) {
    companion object {
        val Default =
            GroupSystemCopy(
                memberAddedFormat = "%1\$s added %2\$s",
                memberAddedPassiveFormat = "%1\$s was added",
                memberRemovedFormat = "%1\$s removed %2\$s",
                memberRemovedPassiveFormat = "%1\$s was removed",
                memberLeftFormat = "%1\$s left",
                adminAddedFormat = "%1\$s made %2\$s an admin",
                adminAddedPassiveFormat = "%1\$s was made an admin",
                adminRemovedFormat = "%1\$s removed %2\$s as admin",
                adminRemovedPassiveFormat = "%1\$s is no longer an admin",
                renamedFormat = "%1\$s renamed the group to “%2\$s”",
                renamedPassiveFormat = "The group was renamed to “%1\$s”",
                avatarChangedFormat = "%1\$s changed the group avatar",
                avatarChangedPassive = "The group avatar changed",
                someone = "Someone",
                fallback = "Group updated",
            )
    }
}

object GroupSystemEvents {
    private const val TypeMemberAdded = "member_added"
    private const val TypeMemberRemoved = "member_removed"
    private const val TypeMemberLeft = "member_left"
    private const val TypeAdminAdded = "admin_added"
    private const val TypeAdminRemoved = "admin_removed"
    private const val TypeGroupRenamed = "group_renamed"
    private const val TypeGroupAvatarChanged = "group_avatar_changed"

    /**
     * Parses kind-1210 JSON content. Null when [plaintext] isn't a group
     * system payload — the caller still must not fall back to chat-body
     * rendering for a kind-1210 record; use [GroupSystemCopy.fallback].
     */
    fun parse(plaintext: String): GroupSystemEvent? =
        runCatching {
            val json = JSONObject(plaintext)
            val systemType = json.optString("system_type").takeIf { it.isNotBlank() } ?: return null
            val data = json.optJSONObject("data")
            GroupSystemEvent(
                systemType = systemType,
                text = json.optString("text"),
                actor = data?.optString("actor")?.takeIf { it.isNotBlank() },
                subject = data?.optString("subject")?.takeIf { it.isNotBlank() },
                name = data?.optString("name")?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()

    /**
     * The hex pubkey to attribute the change to: the payload's `actor` when
     * named, otherwise the event signer — a peer that omits `data.actor` but
     * signs as the committing member still names the actor via the envelope.
     * Null (passive voice) only when neither is present, e.g. a synthesized
     * row for a convergence reorg.
     */
    fun actorHex(
        event: GroupSystemEvent,
        senderHex: String,
    ): String? = event.actor ?: senderHex.takeIf { it.isNotBlank() }

    /**
     * One-line summary rendered from `system_type` + `data` per the spec —
     * the embedded `text` is a last-resort fallback only, since synthesized
     * rows often carry it empty and names should re-resolve as profiles load.
     * [actorName]/[subjectName] are the caller's resolved display names.
     */
    fun summary(
        event: GroupSystemEvent,
        actorName: String?,
        subjectName: String?,
        copy: GroupSystemCopy = GroupSystemCopy.Default,
    ): String {
        val subject = subjectName ?: copy.someone
        return when (event.systemType) {
            TypeMemberAdded ->
                actorName?.let { String.format(copy.memberAddedFormat, it, subject) }
                    ?: String.format(copy.memberAddedPassiveFormat, subject)
            TypeMemberRemoved ->
                actorName?.let { String.format(copy.memberRemovedFormat, it, subject) }
                    ?: String.format(copy.memberRemovedPassiveFormat, subject)
            TypeMemberLeft -> String.format(copy.memberLeftFormat, actorName ?: subject)
            TypeAdminAdded ->
                actorName?.let { String.format(copy.adminAddedFormat, it, subject) }
                    ?: String.format(copy.adminAddedPassiveFormat, subject)
            TypeAdminRemoved ->
                actorName?.let { String.format(copy.adminRemovedFormat, it, subject) }
                    ?: String.format(copy.adminRemovedPassiveFormat, subject)
            TypeGroupRenamed ->
                event.name?.let { name ->
                    actorName?.let { String.format(copy.renamedFormat, it, name) }
                        ?: String.format(copy.renamedPassiveFormat, name)
                } ?: event.text.ifBlank { copy.fallback }
            TypeGroupAvatarChanged ->
                actorName?.let { String.format(copy.avatarChangedFormat, it) }
                    ?: copy.avatarChangedPassive
            else -> event.text.ifBlank { copy.fallback }
        }
    }

    /**
     * Name-free summary for chat-list previews and notifications, where no
     * profile resolution is available: the localized passive form.
     */
    fun previewText(
        plaintext: String,
        copy: GroupSystemCopy = GroupSystemCopy.Default,
    ): String {
        val event = parse(plaintext) ?: return copy.fallback
        return summary(event, actorName = null, subjectName = null, copy = copy)
    }
}
