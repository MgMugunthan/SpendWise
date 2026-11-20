package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpendWiseLogoMark(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.28f),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Image(
            painter = painterResource(id = R.drawable.spendwise_logo),
            contentDescription = "SpendWise Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        )
    }
}

object CategoryIcons {
    fun forKey(iconKey: String): ImageVector = when (iconKey.lowercase()) {
        "food" -> Icons.Rounded.Restaurant
        "restaurant" -> Icons.Rounded.Restaurant
        "groceries" -> Icons.Rounded.ShoppingCart
        "shopping" -> Icons.Rounded.ShoppingBag
        "transport" -> Icons.Rounded.DirectionsCar
        "fuel" -> Icons.Rounded.LocalGasStation
        "bills" -> Icons.Rounded.ReceiptLong
        "electricity" -> Icons.Rounded.Bolt
        "rent" -> Icons.Rounded.Home
        "entertainment" -> Icons.Rounded.Movie
        "healthcare" -> Icons.Rounded.LocalHospital
        "education" -> Icons.Rounded.School
        "travel" -> Icons.Rounded.Flight
        "subscriptions" -> Icons.Rounded.Subscriptions
        "person" -> Icons.Rounded.Person
        "charges" -> Icons.Rounded.AccountBalance
        "transfer" -> Icons.AutoMirrored.Rounded.CompareArrows
        "atm" -> Icons.Rounded.LocalAtm
        "salary" -> Icons.Rounded.Work
        "refund" -> Icons.Rounded.Replay
        "received" -> Icons.Rounded.Payments
        "interest" -> Icons.Rounded.TrendingUp
        "cash" -> Icons.Rounded.Savings
        "other_income" -> Icons.Rounded.AccountBalanceWallet
        else -> Icons.Rounded.Category
    }

    val selectableKeys: List<Pair<String, String>> = listOf(
        "food" to "Food",
        "groceries" to "Groceries",
        "shopping" to "Shopping",
        "transport" to "Transport",
        "bills" to "Bills",
        "rent" to "Home",
        "entertainment" to "Fun",
        "healthcare" to "Health",
        "education" to "Education",
        "travel" to "Travel",
        "subscriptions" to "Media",
        "person" to "People",
        "charges" to "Bank Fees",
        "salary" to "Income"
    )
}

@Composable
fun CategoryIconBadge(
    iconKey: String,
    colorHex: Long,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val tint = Color(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryIcons.forKey(iconKey),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun BankBrandBadge(
    bankName: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val (bg, monogram) = bankVisuals(bankName)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (monogram.isNotEmpty()) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

fun bankVisuals(bankName: String): Pair<Color, String> {
    val lower = bankName.lowercase()
    return when {
        lower.contains("sbi") || lower.contains("state bank") -> Color(0xFF1D4ED8) to "SBI"
        lower.contains("hdfc") -> Color(0xFF2563EB) to "HDFC"
        lower.contains("icici") -> Color(0xFFEA580C) to "ICICI"
        lower.contains("axis") -> Color(0xFFC2410C) to "AXIS"
        lower.contains("kotak") -> Color(0xFFF97316) to "KTK"
        lower.contains("canara") -> Color(0xFF3B82F6) to "CNR"
        lower.contains("indian bank") -> Color(0xFF2563EB) to "IB"
        lower.contains("pnb") || lower.contains("punjab") -> Color(0xFFEA580C) to "PNB"
        lower.contains("chase") -> Color(0xFF1D4ED8) to "CHS"
        lower.contains("revolut") -> Color(0xFF059669) to "REV"
        lower.contains("cash") -> Color(0xFF059669) to "CASH"
        else -> Color(0xFF059669) to bankName.trim().take(3).uppercase()
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = actionLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionRowItem(
    transaction: TransactionModel,
    category: CategoryModel?,
    account: BankAccountModel?,
    hideBalances: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val timeFormatter = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    val amountColor = when (transaction.transactionType) {
        TransactionType.CREDIT -> swColors.income
        TransactionType.DEBIT -> swColors.expense
        TransactionType.INTERNAL_TRANSFER -> swColors.transfer
    }

    val amountPrefix = when (transaction.transactionType) {
        TransactionType.CREDIT -> "+"
        TransactionType.DEBIT -> "-"
        TransactionType.INTERNAL_TRANSFER -> "⇄ "
    }

    val formattedAmount = if (hideBalances) {
        "••••••"
    } else {
        "$amountPrefix${MoneyFormatter.formatMinorUnits(transaction.amountMinorUnits, transaction.currencyCode)}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_row_${transaction.transactionId}")
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                iconKey = category?.iconKey ?: if (transaction.transactionType == TransactionType.INTERNAL_TRANSFER) "transfer" else "other",
                colorHex = category?.colorHex ?: 0xFF2563EB
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.displayCounterparty,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (transaction.sourceType == TransactionSource.BANK_NOTIFICATION) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Auto-detected",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    if (transaction.transactionStatus == TransactionStatus.PENDING_REVIEW) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = swColors.attentionContainer
                        ) {
                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.labelSmall,
                                color = swColors.attention,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                val partySuffix = transaction.counterpartyAccountSuffix
                val subtitleParts = buildList {
                    add(category?.categoryName ?: "Other")
                    if (!partySuffix.isNullOrBlank() && !transaction.displayCounterparty.contains(partySuffix)) {
                        val dirLabel = if (transaction.transactionType == TransactionType.CREDIT) "From" else "To"
                        add("$dirLabel XX$partySuffix")
                    }
                    account?.let {
                        val ownSuffix = if (it.accountNumberSuffix.isNotEmpty()) "XX${it.accountNumberSuffix}" else it.bankName
                        add("${it.accountNickname} · $ownSuffix")
                    } ?: add("Unassigned account")
                }
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "${transaction.paymentMethod.displayName} · ${timeFormatter.format(Date(transaction.transactionDateMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = swColors.mutedText,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = SpendWiseTextStyles.RowAmount,
                    color = amountColor
                )
                Text(
                    text = transaction.transactionType.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = swColors.mutedText
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
