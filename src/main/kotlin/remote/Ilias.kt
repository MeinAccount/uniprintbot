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
import java.util.regex.Pattern

object Ilias {
    private val ilias = Retrofit.Builder()
            .baseUrl(ILIAS_DOMAIN)
            .addConverterFactory(ScalarsConverterFactory.create())
            .client(OkHttpClient.Builder()
                    .cookieJar(CookieStore()).build())
            .build().create(IliasAPI::class.java)

    fun listFiles(type: String, pageId: String): List<RemoteFile> {
        var response = ilias.listPage(pageId, pageId).execute()
        if (response.body()?.contains("Übungsblätter") != true) {
            ilias.login(ILIAS_USER, ILIAS_PASSWORD, "Anmelden").execute()
            response = ilias.listPage(pageId, pageId).execute()
        }

        val matcher = Pattern.compile("""<div class="form-group">\s*<div[^>]*>(.*\.pdf)</div>\s*<div[^>]*>\s*<a href="([^"]*)"[^>]*>Download</a>\s*</div>\s*</div>""")
                .matcher(response.body())
        val files = mutableListOf<RemoteFile>()
        while (matcher.find()) {
            files.add(RemoteFile(type, matcher.group(1),
                    matcher.group(2).replace("&amp;", "&")))
        }

        return files
    }

    fun download(url: String): ByteArray {
        return ilias.download(url).execute().body()!!.source().readByteArray()
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

        @Streaming // TODO?
        @GET
        fun download(@Url url: String): Call<ResponseBody>
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
