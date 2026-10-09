package com.yagay.ysuite.feature.ypower.api

/**
 * Root-only preference changes must never force-stop an application.
 * Active Hook changes require restarting the target process because
 * writing remote preferences does not install or remove hooks.
 */
fun requiresHookRestart(
    lastAppliedRevision: String?,
    previouslyActive: Boolean,
    desiredRevision: String,
    desiredActive: Boolean,
): Boolean =
    (previouslyActive || desiredActive) &&
        (lastAppliedRevision != desiredRevision || previouslyActive != desiredActive)
