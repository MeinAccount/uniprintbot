package remote

import ILIAS_CLIENT_ID
import ILIAS_DOMAIN
import ILIAS_PASSWORD
import ILIAS_USER
import okhttp3.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.xml.bind.DatatypeConverter

object Ilias {
    private val ilias = Retrofit.Builder()
            .baseUrl(ILIAS_DOMAIN)
            .addConverterFactory(ScalarsConverterFactory.create())
            .client(OkHttpClient.Builder()
                    .cookieJar(CookieStore()).build())
            .build().create(IliasAPI::class.java)

    fun listIliasResourcesGoto(url: String, nameShortener: (String) -> String): List<Pair<String, String>> {
        var response = ilias.download(url).execute().body()?.string()
        if (response?.contains("Inhalt") != true) {
            ilias.login(ILIAS_USER, ILIAS_PASSWORD, "Anmelden").execute()
            response = ilias.download(url).execute().body()?.string()
        }

        val matcher = Pattern.compile("""<h4 class="il_ContainerItemTitle"><a href="([^"]+)"[^>]*>([^<]+)</a>.*?</a>""")
                .matcher(response ?: "")

        val iliasResources = mutableListOf<Pair<String, String>>()
        while (matcher.find()) {
            iliasResources.add("${nameShortener.invoke(matcher.group(2).trim())}.pdf" to
                    matcher.group(1).replace("&amp;", "&"))
        }

        return iliasResources.toList()
    }

    fun listWebResources(downloadUrl: String, regex: String, baseUrl: String = downloadUrl,
                         nameShortener: (String) -> String): List<Pair<String, String>> {
        val matcher = Pattern.compile(regex)
                .matcher(ilias.download(downloadUrl).execute().body()?.string())

        val iliasResources = mutableListOf<Pair<String, String>>()
        while (matcher.find()) {
            iliasResources.add("${nameShortener.invoke(matcher.group(2))}.pdf" to baseUrl + matcher.group(1))
        }

        return iliasResources.toList()
    }


    fun downloadRefresh(type: String, name: String, url: String, previous: IliasResource?): IliasResource? {
        var response = ilias.download(url, previous?.hash).execute()
        if (response.raw().request().url().encodedPath().contains("login")) {
            ilias.login(ILIAS_USER, ILIAS_PASSWORD, "Anmelden").execute()
            response = ilias.download(url, previous?.hash).execute()
        }

        if (previous != null && response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            println("$type $name unchanged ${previous.hash}")
            return previous.copy(name = name)
        } else if (response.isSuccessful) {
            val bytes = response.body()!!.bytes()
            val hash = response.headers()["ETag"]
                    ?: DatatypeConverter.printHexBinary(MessageDigest.getInstance("MD5").digest(bytes))

            if (previous != null && previous.hash == hash) {
                println("$type $name unchaged MD5 $hash")
                return previous.copy(name = name)
            } else {
                println("$type $name ${previous?.let { "changed ${it.hash}" } ?: "uncached"} $hash")
                return IliasResource(type, name, url, hash, TelegramResource.LocalTelegramResource(bytes))
            }
        }

        println("Failed to fetch $type $name!")
        println(response)
        return null
    }


    private interface IliasAPI {
        @FormUrlEncoded
        @POST("ilias.php?client_id=$ILIAS_CLIENT_ID&cmd=post&baseClass=ilStartUpGUI")
        fun login(@Field("username") username: String,
                  @Field("password") password: String,
                  @Field("cmd[doStandardAuthentication]") authentication: String): Call<String>

        @GET("ilias.php?cmd=showOverview&baseClass=ilexercisehandlergui")
        fun listPage(@Query("ref_id") ref_id: String,
                     @Query("target") target: String): Call<String>

        @GET
        fun download(@Url url: String, @Header("If-None-Match") etag: String? = null): Call<ResponseBody>
    }

    private data class CookieStore(private var cookies: List<Cookie> = emptyList()) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookies
        }
    }
}
