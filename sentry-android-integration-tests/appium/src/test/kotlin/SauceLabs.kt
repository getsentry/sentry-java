import com.google.common.hash.Hashing
import com.jayway.jsonpath.JsonPath
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import kotlin.io.path.name
import kotlin.io.path.readBytes

class SauceLabs {
    companion object {
        val user: String = System.getenv("SAUCE_USERNAME")
        val key: String = System.getenv("SAUCE_ACCESS_KEY")
        const val region = "us-west-1"

        private val logger: Logger = Logger.getLogger(SauceLabs::class.simpleName)
        private const val baseUrl = "https://api.$region.saucelabs.com"
        private val client = HttpClient(CIO) {
            expectSuccess = true
            install(Auth) {
                basic {
                    sendWithoutRequest { true }
                    credentials {
                        BasicAuthCredentials(username = user, password = key)
                    }
                }
            }
        }

        // https://docs.saucelabs.com/dev/api/storage/#upload-file-to-app-storage
        fun uploadApp(app: AppInfo): String {
            val fileId = findAppOnServer(app)
            if (fileId != null) {
                logger.info("App '${app.name}' - skipping upload - the same file already exists on the server as $fileId")
                return fileId
            }

            logger.info("App '${app.name}' - uploading to SauceLabs from ${app.path}")

            return runBlocking {
                val response = client.submitFormWithBinaryData(
                    url = "$baseUrl/v1/storage/upload",
                    formData = formData {
                        append("name", app.path.name)
                        append(
                            "payload", app.path.readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"${app.path.name}\"")
                            }
                        )
                    }
                )

                logger.info("App '${app.name}' - uploaded successfully")

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
