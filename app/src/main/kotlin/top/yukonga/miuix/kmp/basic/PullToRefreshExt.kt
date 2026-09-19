package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extension to support `rememberPullToRefreshState(refreshThreshold = 64.dp)` in Miuix 0.9.3.
 */
@Composable
fun rememberPullToRefreshState(refreshThreshold: Dp): PullToRefreshState {
    return rememberPullToRefreshState()
}

/**
 * Extension to support `PullToRefresh(state = ..., isRefreshing = ..., onRefresh = ...)` parameter alias.
 */
@Composable
fun PullToRefresh(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        pullToRefreshState = state,
        contentPadding = contentPadding,
        content = content,
    )
}
