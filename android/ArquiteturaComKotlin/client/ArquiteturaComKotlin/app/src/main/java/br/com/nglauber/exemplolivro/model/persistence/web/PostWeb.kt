package br.com.nglauber.exemplolivro.model.persistence.web

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import br.com.nglauber.exemplolivro.App
import br.com.nglauber.exemplolivro.model.auth.AccessManager
import br.com.nglauber.exemplolivro.model.data.Post
import br.com.nglauber.exemplolivro.model.persistence.PostDataSource
import br.com.nglauber.exemplolivro.model.persistence.file.Media
import com.google.gson.GsonBuilder
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class PostWeb(private val username : String = AccessManager.instance.currentUser?.email!!,
              private val context : Context = App.instance) : PostDataSource {

    val service : PostAPI

    companion object {
        val SERVER_PATH = "http://192.168.25.29/postservice/"
    }

    init {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY

        val httpClient = OkHttpClient.Builder()
        httpClient.addInterceptor(logging)

        val gson = GsonBuilder()
                .setLenient()
                .create()

        val retrofit = Retrofit.Builder()
                .baseUrl(SERVER_PATH)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient.build())
                .build()

        service = retrofit.create<PostAPI>(PostAPI::class.java)
    }

    override fun loadPosts(callback: (List<Post>) -> Unit) {
        doAsync {
            val posts = service.list(username).execute()
            val postsList = posts.body().map { it.toDomain() }

            uiThread {
                callback(postsList)
            }
        }
    }


    override fun loadPost(postId: Long, callback: (Post?) -> Unit) {
        doAsync {
            val post = service.loadPost(postId, username).execute()
            val postDomain = post.body()?.toDomain()
            uiThread {
                callback(postDomain)
            }
        }
    }

    override fun savePost(post: Post, callback: (Boolean) -> Unit) {
        doAsync {
            var result : Boolean

            if (post.id == 0L) {
                post.id = service.insert(PostMapper(post, username)).execute().body().id
                result = post.id != 0L

            } else {
                val id = service.update(post.id, PostMapper(post, username)).execute().body().id
                result = id != 0L
            }

            if (result && !TextUtils.isEmpty(post.photoUrl)){
                result = uploadFile(post)
            }

            uiThread {
                callback(result)
            }
        }
    }

    override fun deletePost(post: Post, callback: (Boolean) -> Unit) {
        doAsync {
            val result = service.delete(post.id).execute()
            val resultBool = result.body().id != 0L

            uiThread {
                callback(resultBool)
            }
        }
    }

    private fun uploadFile(post : Post) : Boolean {

        val file = File(context.getExternalFilesDir(null), "${post.id}.jpg")
        if (file.exists() &&  !file.delete()) return false

        if (Media.saveImageFromUri(context, Uri.parse(post.photoUrl), file)) {
            val requestFile = RequestBody.create(
                    MediaType.parse(context.contentResolver.getType(Uri.parse(post.photoUrl))),
                    file
            )

            val body = MultipartBody.Part.createFormData("arquivo", file.getName(), requestFile)
            val description = RequestBody.create(MultipartBody.FORM, post.id.toString())
            val response = service.uploaPhoto(description, body).execute()
            return response.isSuccessful
        }
        return false
    }
}