package dev.leafledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class CategoryIcon(val image: ImageVector, val color: Color) {
    RECEIPT(Icons.AutoMirrored.Outlined.ReceiptLong, Color(0xFF5F646D)),
    DINING(Icons.Outlined.Restaurant, Color(0xFF9E502E)),
    COFFEE(Icons.Outlined.LocalCafe, Color(0xFF825438)),
    CAR(Icons.Outlined.DirectionsCar, Color(0xFF3C638C)),
    SHOPPING(Icons.Outlined.ShoppingBag, Color(0xFF6E5791)),
    HOME(Icons.Outlined.Home, Color(0xFF756047)),
    UTILITIES(Icons.Outlined.Bolt, Color(0xFF8E620E)),
    PHONE(Icons.Outlined.PhoneAndroid, Color(0xFF4F6187)),
    HEALTH(Icons.Outlined.MedicalServices, Color(0xFFA04E59)),
    ENTERTAINMENT(Icons.Outlined.Movie, Color(0xFF805586)),
    EDUCATION(Icons.Outlined.School, Color(0xFF4F638B)),
    TRAVEL(Icons.Outlined.Flight, Color(0xFF366C83)),
    SPORT(Icons.Outlined.FitnessCenter, Color(0xFF426E70)),
    PET(Icons.Outlined.Pets, Color(0xFF87573F)),
    GIFT(Icons.Outlined.CardGiftcard, Color(0xFF994863)),
    WALLET(Icons.Outlined.AccountBalanceWallet, Color(0xFF856021))
}

internal fun iconForCategoryName(name: String): CategoryIcon {
    fun matches(vararg words: String) = words.any { name.contains(it, ignoreCase = true) }
    return when {
        matches("咖啡", "奶茶", "茶饮", "coffee") -> CategoryIcon.COFFEE
        matches("餐", "外卖", "早饭", "午饭", "晚饭", "零食", "饮食", "美食") -> CategoryIcon.DINING
        matches("交通", "打车", "停车", "公交", "地铁", "加油", "车费") -> CategoryIcon.CAR
        matches("购物", "日用", "买菜", "超市", "服饰", "百货") -> CategoryIcon.SHOPPING
        matches("水电", "电费", "水费", "燃气") -> CategoryIcon.UTILITIES
        matches("通讯", "话费", "通信", "宽带", "流量") -> CategoryIcon.PHONE
        matches("居家", "房租", "住房", "物业", "装修") -> CategoryIcon.HOME
        matches("医疗", "健康", "药品", "医院", "体检") -> CategoryIcon.HEALTH
        matches("娱乐", "电影", "游戏", "演出", "音乐") -> CategoryIcon.ENTERTAINMENT
        matches("学习", "教育", "书籍", "培训", "学费") -> CategoryIcon.EDUCATION
        matches("旅行", "旅游", "酒店", "住宿", "机票") -> CategoryIcon.TRAVEL
        matches("运动", "健身", "球场", "游泳") -> CategoryIcon.SPORT
        matches("宠物", "猫粮", "狗粮") -> CategoryIcon.PET
        matches("礼物", "礼品", "红包", "礼金", "人情") -> CategoryIcon.GIFT
        matches("工资", "薪资", "奖金", "收入", "报销") -> CategoryIcon.WALLET
        else -> CategoryIcon.RECEIPT
    }
}

@Composable
internal fun CategoryBadge(icon: CategoryIcon, size: Dp = 44.dp) {
    val color = icon.color
    val dark = isSystemInDarkTheme()
    val background = color.copy(alpha = if (dark) 0.34f else 0.14f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val foreground = if (dark) lerp(color, Color.White, 0.58f) else color
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = background) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon.image, contentDescription = null, modifier = Modifier.size(size * 0.53f), tint = foreground)
        }
    }
}
