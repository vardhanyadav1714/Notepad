package `in`.innovaticshub.notepad.ui.canvas.collab

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke

data class PeerInkPreview(val stroke: Stroke, val receivedAt: Long = System.currentTimeMillis())

/** Deliberately separate from document state, undo history and autosave. */
data class RoomPresenceState(
    val members: List<RoomMember> = emptyList(),
    val previews: Map<String, PeerInkPreview> = emptyMap()
) {
    fun expire(now: Long): RoomPresenceState = copy(
        members = members.map { member ->
            if (member.cursor != null && now - member.receivedAt > 4_000)
                member.copy(activity = "idle", cursor = null) else member
        },
        previews = previews.filterValues { now - it.receivedAt <= 4_000 }
    )
}
