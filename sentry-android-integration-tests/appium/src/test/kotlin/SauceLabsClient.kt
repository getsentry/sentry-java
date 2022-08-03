import com.google.common.hash.Hashing
import com.jayway.jsonpath.JsonPath
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import kotlin.io.path.name
import kotlin.io.path.readBytes

class SauceLabs {
    companion object {
        val user: String = System.getenv("SAUCE_USERNAME")
        val key: String = System.getenv("SAUCE_ACCESS_KEY")
        const val region = "us-west-1"
    }
}

/**
 * REST client for SauceLabs
 */
class SauceLabsClient {
    companion object {
        private val logger: Logger = Logger.getLogger(SauceLabsClient::class.simpleName)
        private const val baseUrl = "https://api.${SauceLabs.region}.saucelabs.com"
        private val client = HttpClient(CIO) {
            expectSuccess = true
            install(Auth) {
                basic {
                    sendWithoutRequest { true }
                    credentials {
                        BasicAuthCredentials(username = SauceLabs.user, password = SauceLabs.key)
                    }
                }
            }
        }

        // https://docs.saucelabs.com/dev/api/storage/#upload-file-to-app-storage
        fun uploadApp(app: AppInfo): String {
            val fileId = findAppOnServer(app)
            if (fileId != null) {
                logger.info("Skipping app ${app.name} upload - the same file already exists on the server as $fileId")
                return fileId
            }

            logger.info("Uploading app ${app.name} to SauceLabs from ${app.path}")

            return runBlocking {
                val response = client.submitFormWithBinaryData(
                    url = "$baseUrl/v1/storage/upload",
                    formData = formData {
                        append("name", app.path.name)
                        append("payload", app.path.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "application/octet-stream")
                            append(HttpHeaders.ContentDisposition, "filename=\"${app.path.name}\"")
                        })
                    }
                )

                logger.info("App ${app.name} uploaded successfully")

                val json = JsonPath.parse(response.bodyAsText())
                json.read<String>("item.id")!!
            }
        }

        // https://docs.saucelabs.com/dev/api/storage/#get-app-storage-files
        private fun findAppOnServer(app: AppInfo): String? {
            val hash = Hashing.sha256().hashBytes(app.path.readBytes())

            return runBlocking {
                val response = client.get("$baseUrl/v1/storage/files") {
                    url {
                        parameters.append("sha256", hash.toString())
                    }
                }
                val json = JsonPath.parse(response.bodyAsText())
                val fileIds = json.read<List<String>>("items[*].id")
                fileIds.firstOrNull()
            }
        }
    }
}
