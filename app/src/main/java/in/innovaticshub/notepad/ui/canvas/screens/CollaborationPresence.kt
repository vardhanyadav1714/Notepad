package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.collab.RoomMember
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel

@Composable
internal fun RoomMembersButton(model: DrawingViewModel, connected: Boolean, onClick: () -> Unit) {
    val presence by model.presence.collectAsState()
    IconButton(onClick = onClick, modifier = Modifier.testTag("room-members-button")) {
        if (!connected || presence.members.isEmpty()) {
            Icon(Icons.Outlined.Group, "Collaboration", Modifier.size(23.dp),
                tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        } else {
            Box(Modifier.size(42.dp, 34.dp).semantics {
                contentDescription = "Collaboration, ${presence.members.size} connected artists"
            }) {
                presence.members.take(2).reversed().forEachIndexed { index, member ->
                    MemberAvatar(member, Modifier.align(if (index == 0) Alignment.CenterEnd else Alignment.CenterStart), 27)
                }
                if (presence.members.size > 2) Text("+${presence.members.size - 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(horizontal = 2.dp))
            }
        }
    }
}

@Composable
internal fun MemberAvatar(member: RoomMember, modifier: Modifier = Modifier, size: Int = 36) {
    val color = Color(member.colorArgb.toInt())
    Box(modifier.size(size.dp).semantics {
        contentDescription = if (member.isSelf) "You" else member.label
    }) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(color)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (member.isSelf) "Y" else member.label.takeLast(2), color = Color.White,
                style = if (size < 30) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
        }
        if (member.activity != "idle") Box(Modifier.align(Alignment.BottomEnd).size(10.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp).background(color, CircleShape))
    }
}

@Composable
internal fun RoomMemberList(model: DrawingViewModel) {
    val presence by model.presence.collectAsState()
    if (presence.members.isNotEmpty()) {
        Text("In this room · ${presence.members.size}", style = MaterialTheme.typography.titleSmall)
        presence.members.forEach { member ->
            Row(Modifier.fillMaxWidth().testTag("room-member-${member.id}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(member)
                Column(Modifier.weight(1f)) {
                    Text(if (member.isSelf) "You" else member.label, style = MaterialTheme.typography.bodyMedium)
                    Text(when (member.activity) {
                        "drawing" -> "Drawing now"
                        "erasing" -> "Erasing"
                        "selecting" -> "Selecting ink"
                        else -> "In the room"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!member.isSelf) TextButton(onClick = model::blockCollaborationRoom) { Text("Block & leave") }
            }
        }
        Text("Artists use temporary labels and colours. No profile photo or real name is needed.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
