package com.nextcloud.contacts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Consumes leftover scroll at the list edge so the sheet does not stretch.
 * The scrollable also needs `overscrollEffect = null`.
 */
@Composable
fun rememberModalBottomSheetScrollNestedScrollConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = available
        }
    }
}

@Composable
fun Modifier.modalBottomSheetScrollableContent(): Modifier =
    nestedScroll(rememberModalBottomSheetScrollNestedScrollConnection())
