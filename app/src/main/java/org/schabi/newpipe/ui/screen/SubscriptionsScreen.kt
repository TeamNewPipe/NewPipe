package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.SubscriptionsViewModel

@Composable
fun SubscriptionsScreen(
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Subscriptions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No subscriptions yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Subscribe to channels you like to see their latest videos here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subscriptions) { subscription ->
                    SubscriptionItem(
                        subscription = subscription,
                        onClick = {
                            if (!subscription.url.isNullOrEmpty()) {
                                navigator.navigateTo(AppDestination.Channel(subscription.url!!))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionItem(
    subscription: SubscriptionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = subscription.avatarUrl,
                contentDescription = subscription.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.name ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${subscription.subscriberCount ?: 0} subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

