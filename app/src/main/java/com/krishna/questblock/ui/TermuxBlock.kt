package com.krishna.questblock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * v207: the lock page in the Termux theme. Every kind of lock (app, site, Shorts/Reels, YouTube, fun limit,
 * study first, Focus lock) is printed in the terminal; unlocking and going home are commands there.
 */
@Composable
fun TermuxBlockScreen(
    pkg: String,
    nonce: Int,
    funLimit: Boolean,
    onHome: () -> Unit,
    onMain: () -> Unit,
    onOpen: () -> Unit,
    vm: TermuxViewModel = hiltViewModel(),
) {
    LaunchedEffect(pkg, nonce, funLimit) {
        vm.enterBlock(TermuxBlockCtx(pkg, funLimit, onHome, onMain, onOpen), "")
    }
    TermuxTerminal(host = vm)
}
