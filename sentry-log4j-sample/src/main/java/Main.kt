import io.sentry.core.Sentry
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.SystemOutLogger
import org.apache.log4j.Logger

val log: Logger = Logger.getLogger("Sentry log4j Sample")

fun main() {
    val initializer: (SentryOptions) -> Unit = {
        it.dsn = "https://f1f12e8b32b24284b41b0e365770e1d3@o408263.ingest.sentry.io/5278819"
        it.cacheDirPath = "/tmp"
        it.connectionTimeoutMillis = 2000
        it.isEnableNdk = false
        it.setLogger(SystemOutLogger())
        it.setDiagnosticLevel(SentryLevel.DEBUG)
        it.isDebug = true
    }

    println("Started.")
    Sentry.init(initializer)
    println("Sentry initialized.")
    log.info("Sentry through log4j sending events.")
    println("Event sent.")
    Sentry.flush(1000)
    Sentry.close()
}
