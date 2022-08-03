import com.google.common.hash.Hashing
import com.jayway.jsonpath.JsonPath
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
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
        private val baseUrl = "https://api.${SauceLabs.region}.saucelabs.com"
        private val client = OkHttpClient.Builder()
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.request.header("Authorization") != null) {
                        return null // Give up, we've already attempted to authenticate.
                    }
                    return response.request.newBuilder()
                        .header("Authorization", Credentials.basic(SauceLabs.user, SauceLabs.key))
                        .build()
                }
            })
            .build()

        // https://docs.saucelabs.com/dev/api/storage/#upload-file-to-app-storage
        fun uploadApp(app: AppInfo): String {
            val fileId = findAppOnServer(app)
            if (fileId != null) {
                logger.info("Skipping app ${app.name} upload - the same file already exists on the server as $fileId")
                return fileId
            }

            logger.info("Uploading app ${app.name} to SauceLabs from ${app.path}")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", app.path.name)
                .addFormDataPart(
                    "payload", app.path.name,
                    app.path.toFile().asRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/v1/storage/upload")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("App upload to SauceLabs failed: $response\n${response.body?.string()}")
                }

                logger.info("App ${app.name} uploaded successfully")
                val jsonString = response.body!!.string()
                val json = JsonPath.parse(jsonString)
                return json.read<String>("item.id")!!
            }
        }

        // https://docs.saucelabs.com/dev/api/storage/#get-app-storage-files
        private fun findAppOnServer(app: AppInfo): String? {
            val hash = Hashing.sha256().hashBytes(app.path.readBytes())

            val request = Request.Builder()
                .url("$baseUrl/v1/storage/files?sha256=${hash}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "Looking for the app in SauceLabs failed: $response\n" +
                            "${response.body?.string()}"
                    )
                }

                val jsonString = response.body!!.string()
                val json = JsonPath.parse(jsonString)
                val fileIds = json.read<List<String>>("items[*].id")
                return fileIds.firstOrNull()
            }
        }
    }
}
