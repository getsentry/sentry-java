package io.sentry.compose

import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.TransactionNameSource

public fun wrapClickable(clickable: () -> Unit, clickLabel: String?): () -> Unit {
    return {
        val hub = Sentry.getCurrentHub()
        if (clickLabel.isNullOrEmpty()) {
            hub.options
                .logger
                .log(
                    SentryLevel.DEBUG,
                    "Modifier.clickable clickLabel is null, skipping breadcrumb/transaction creation."
                )
        } else {
            startTransaction(hub, clickLabel)
            addBreadcrumb(hub, clickLabel)
        }

        clickable()
    }
}

private fun addBreadcrumb(hub: IHub, label: String) {
    if (!hub.options.isEnableUserInteractionBreadcrumbs) {
        return
    }

    val breadcrumb = Breadcrumb.userInteraction(
        "action.click",
        label,
        null,
        emptyMap()
    )
    hub.addBreadcrumb(breadcrumb)
}

private fun startTransaction(hub: IHub, label: String) {
    if (!(hub.options.isTracingEnabled && hub.options.isEnableUserInteractionTracing)) {
        return
    }

    hub.configureScope { scope: Scope? ->
        scope?.withTransaction { scopeTransaction: ITransaction? ->
            if (scopeTransaction == null) {
                val transactionOptions = TransactionOptions().apply {
                    isWaitForChildren = true
                    idleTimeout = hub.options.idleTimeout
                    isTrimEnd = true
                }

                val transaction: ITransaction = hub.startTransaction(
                    TransactionContext(label, TransactionNameSource.COMPONENT, "ui.action.click"),
                    transactionOptions
                )
                scope.transaction = transaction
            } else {
                hub.options
                    .logger
                    .log(
                        SentryLevel.DEBUG,
                        "Transaction '%s' won't be bound to the Scope since there's one already in there.",
                        label
                    )
            }
        }
    }
}
