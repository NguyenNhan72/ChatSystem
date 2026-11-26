package com.example.chatsystem.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatsystem.ui.theme.MessengerBackground
import com.example.chatsystem.ui.theme.MessengerBlue
import com.example.chatsystem.ui.theme.MessengerOnlineGreen
import com.example.chatsystem.ui.theme.MessengerSurface
import com.example.chatsystem.ui.theme.MessengerTextMuted
import com.example.chatsystem.ui.theme.MessengerTextPrimary
import com.example.chatsystem.ui.theme.MessengerTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

// Bang mau hien dai cho avatar chu cai
private val AvatarBgColors = listOf(
    Color(0xFF0084FF), // Xanh Messenger
    Color(0xFF7B2CBF), // Tim dam
    Color(0xFF00A86B), // Xanh ngoc
    Color(0xFFEA580C), // Cam
    Color(0xFFE11D48), // Do hoa hong
    Color(0xFF0284C7), // Xanh da troi
    Color(0xFF16A34A), // Xanh luc bao
    Color(0xFF4F46E5), // Xanh cham
)

fun extractAvatarInitial(name: String): String {
    val clean = name.trim()
    if (clean.equals("ptien", ignoreCase = true)) return "T"
    if (clean.equals("pminh", ignoreCase = true)) return "M"
    if (clean.equals("nhan", ignoreCase = true)) return "N"
    val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    if (words.size > 1) {
        val lastWord = words.last()
        return lastWord.firstOrNull()?.uppercase() ?: "?"
    }
    return clean.firstOrNull()?.uppercase() ?: "?"
}

/**
 * Avatar nhom ket hop cho chat nhom Messenger.
 * Hien thi cac avatar thanh vien long nhau kem chu cai dau.
 */
@Composable
fun GroupAvatar(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    val cleanNames = names.map { it.trim() }.filter { it.isNotBlank() }
    when {
        cleanNames.isEmpty() -> Avatar(name = "Group", modifier = modifier, size = size)
        cleanNames.size == 1 -> Avatar(name = cleanNames[0], modifier = modifier, size = size)
        else -> {
            val childSize = size * 0.65f
            val borderWidth = if (size > 36.dp) 2.dp else 1.5.dp
            Box(modifier = modifier.size(size)) {
                // Avatar goc tren ben phai (Thanh vien 1)
                Avatar(
                    name = cleanNames[0],
                    size = childSize,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                // Avatar goc duoi ben trai (Thanh vien 2 voi vien vien nen)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(childSize)
                        .border(borderWidth, MessengerBackground, CircleShape)
                        .clip(CircleShape)
                        .background(AvatarBgColors[cleanNames[1].hashCode().absoluteValue % AvatarBgColors.size]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = extractAvatarInitial(cleanNames[1]),
                        fontSize = (childSize.value * 0.44f).coerceAtLeast(9f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Avatar phong cach Messenger voi chu cai dau va cham xanh bao online.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    online: Boolean = false,
    groupMemberNames: List<String> = emptyList(),
) {
    if (groupMemberNames.size >= 2) {
        GroupAvatar(names = groupMemberNames, modifier = modifier, size = size)
        return
    }
    val bg = AvatarBgColors[name.hashCode().absoluteValue % AvatarBgColors.size]
    val initial = extractAvatarInitial(name)
    val fontSize = (size.value * 0.44f).coerceAtLeast(10f).sp

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Cham xanh online voi vien trang
        if (online) {
            val dotSize = (size.value * 0.28f).coerceAtLeast(11f).dp
            val borderWidth = if (size > 32.dp) 2.5.dp else 1.5.dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .border(borderWidth, MessengerBackground, CircleShape)
                    .clip(CircleShape)
                    .background(MessengerOnlineGreen)
                    .semantics { contentDescription = "Active now" },
            )
        }
    }
}

/**
 * Thanh tim kiem dang vien thuoc (bo tron) phong cach Messenger.
 */
@Composable
fun MessengerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MessengerSurface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MessengerTextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MessengerTextMuted,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MessengerBlue),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    color = MessengerTextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Thanh trang thai hoat dong / story:
 * "Tin cua ban" + chi gom nhung nguoi thuc su co trong danh sach cuoc tro chuyen.
 */
@Composable
fun MessengerActiveTray(
    userDisplayName: String,
    activeContacts: List<ActiveUserItem>,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Muc tin cua ban
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(62.dp),
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    Avatar(name = userDisplayName, size = 56.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .border(2.dp, MessengerBackground, CircleShape)
                            .clip(CircleShape)
                            .background(MessengerBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add story",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your Story",
                    fontSize = 12.sp,
                    color = MessengerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Chi hien thi nhung nguoi lien he thuc te
        items(activeContacts, key = { it.id }) { contact ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(62.dp)
                    .clickable { onUserClick(contact.id) },
            ) {
                Avatar(name = contact.name, size = 56.dp, online = contact.online)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = contact.name.split(" ").firstOrNull() ?: contact.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MessengerTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

data class ActiveUserItem(
    val id: Long,
    val name: String,
    val online: Boolean,
)

/**
 * Thanh dieu huong duoi 3 tab cua Messenger.
 */
@Composable
fun MessengerBottomNav(
    selectedTab: Int = 0,
    unreadCount: Int = 0,
    onTabSelected: (Int) -> Unit = {},
) {
    Surface(
        color = MessengerBackground,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tab 0: Doan chat (Chats)
            BottomNavItem(
                icon = MessengerIcons.ChatBubble,
                label = "Chats",
                selected = selectedTab == 0,
                badgeCount = unreadCount,
                onClick = { onTabSelected(0) },
            )
            // Tab 1: Danh ba (People)
            BottomNavItem(
                icon = MessengerIcons.People,
                label = "People",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
            )
            // Tab 2: Kham pha (Discover)
            BottomNavItem(
                icon = MessengerIcons.Discover,
                label = "Discover",
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    val tint = if (selected) Color.Black else MessengerTextMuted

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (selected && badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MessengerBlue,
                        contentColor = Color.White,
                    ) {
                        Text(if (badgeCount > 99) "99+" else badgeCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        } else {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
        )
    }
}

/**
 * Thanh tha cam xuc noi (❤️, 😆, 😮, 😢, 😡, 👍).
 */
@Composable
fun FloatingReactionBar(
    onReactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reactions = listOf("❤️", "😆", "😮", "😢", "😡", "👍")

    Row(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MessengerBackground)
            .border(1.dp, MessengerSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { emoji ->
            Text(
                text = emoji,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable { onReactionClick(emoji) }
                    .padding(4.dp),
            )
        }
    }
}

/** Thong bao toan man hinh cho trang thai trong hoac loi. */
@Composable
fun CenteredMessage(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MessengerTextPrimary,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MessengerTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Banner canh bao nho tren dau noi dung khi mat ket noi mang. */
/** Socket mat ket noi: tin moi nam trong outbox, trang thai online cua nguoi khac bi an vi khong con cap nhat. */
const val OFFLINE_BANNER = "Mất kết nối — tin sẽ được gửi khi có mạng"

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFFFEBEA), modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            color = Color(0xFFD32F2F),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Dinh dang thoi gian: "HH:mm" cho hom nay, "Hom qua", hoac "dd/MM". */
fun formatListTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
        sameYear && dayDiff == 1 -> "Hôm qua"
        sameYear -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(epochMs))
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epochMs))
    }
}
