package com.hiczp.caeruleum.test

import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hiczp.caeruleum.annotation.*
import com.hiczp.caeruleum.create
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockHttpResponse
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test

const val LOCALHOST = "https://localhost/"

val httpMockEngine = MockEngine {
    MockHttpResponse(
        call,
        HttpStatusCode.OK,
        ByteReadChannel(
            json {
                "header" to headers.toString()
                "method" to method.value
                "url" to url.toString()
                "contentLength" to content.contentLength
            }.toString()
        ),
        headersOf("Content-Type", ContentType.Application.Json.toString())
    )
}

@Suppress("DeferredIsResult")
@BaseUrl(LOCALHOST)
interface Service {
    @Get
    fun noReturnValue(): Job

    @Get
    fun withReturnValue(): Deferred<JsonElement>

    @Get("user")
    fun withUrl(): Deferred<JsonElement>

    @Get("users/{user}/repos")
    fun withPathVariable(@Path user: String): Deferred<JsonElement>

    @Get("users")
    fun withQueryParam(@Query param1: String, @Query param2: String): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postFormUrlEncoded(@Field arg1: String, @Field("czp") arg2: String? = null): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postEmptyContent(): Deferred<JsonElement>

    @Post("user")
    @FormUrlEncoded
    fun postWithMap(@FieldMap fieldMap: Map<String, String>): Deferred<JsonElement>

    @Delete
    fun delete(): Deferred<JsonElement>

    @Post("user")
    fun postWithoutBody(): Deferred<JsonElement>

    fun nonAbstract(arg1: String) = arg1

    suspend fun nonAbstractAndSuspend(arg1: String) = arg1

    @JvmDefault
    fun jvmDefault(arg1: String) = arg1

    @JvmDefault
    suspend fun jvmDefaultAndSuspend(arg1: String) = arg1

    @Post
    fun postJson(@Body body: JsonObject): Deferred<JsonElement>

    @Get
    fun url(@Url url: String, @Query arg1: String): Deferred<JsonElement>

    @Get("user/{path}/repos")
    fun pathWithSlash(@Path path: String): Deferred<JsonElement>

    @Get
    fun queryEncode(@Query arg1: String = "!"): Deferred<JsonElement>

    @Post
    @FormUrlEncoded
    fun fieldEncode(
        @FieldMap map: Map<String, String> = mapOf(
            "param1" to "1 2!+/"
        )
    ): Deferred<JsonElement>

    @Get
    fun paramNullable(@Query arg: String?): Deferred<JsonElement>

    companion object {
        fun functionInCompanionObject(arg: String) = arg

        @JvmStatic
        fun jvmStatic(arg: String) = arg
    }
}

@TestMethodOrder(NatureOrder::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Test {
    private val httpClient = HttpClient(httpMockEngine) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    private lateinit var service: Service

    @BeforeAll
    fun init() {
        service = httpClient.create()
    }

    @Test
    fun noReturnValue() = runBlocking {
        service.noReturnValue().join()
    }

    @Test
    fun withReturnValue() = runBlocking {
        service.withReturnValue().await().url.assert {
            LOCALHOST
        }
    }

    @Test
    fun withUrl() = runBlocking {
        service.withUrl().await().url.assert {
            LOCALHOST + "user"
        }
    }

    @Test
    fun withPathVariable() = runBlocking {
        service.withPathVariable("czp3009").await().url.assert {
            LOCALHOST + "users/czp3009/repos"
        }
    }

    @Test
    fun withQueryParam() = runBlocking {
        service.withQueryParam("czp1", "czp2").await().url.assert {
            LOCALHOST + "users?param1=czp1&param2=czp2"
        }
    }

    @Test
    fun postFormUrlEncoded() = runBlocking {
        service.postFormUrlEncoded("01", "02").await().contentLength.assert {
            "arg1=01&czp=02".length
        }
    }

    @Test
    fun repeatPost() = runBlocking {
        service.postFormUrlEncoded("001", "002").await().contentLength.assert {
            "arg1=001&czp=002".length
        }
    }

    @Test
    fun postWithNullValue() = runBlocking {
        service.postFormUrlEncoded("01").await().contentLength.assert {
            "arg1=01".length
        }
    }

    @Test
    fun postEmptyContent() = runBlocking {
        service.postEmptyContent().await().contentLength.assert {
            0
        }
    }

    @Test
    fun postWithMap() = runBlocking {
        service.postWithMap(
            mapOf(
                "arg1" to "02",
                "arg2" to "01"
            )
        ).await().contentLength.assert {
            "arg1=02&arg2=01".length
        }
    }

    @Test
    fun deleteMethod() = runBlocking {
        service.delete().await().method.assert {
            HttpMethod.Delete.value
        }
    }

    @Test
    fun postWithoutBody() = runBlocking {
        service.postWithoutBody().await().contentLength.assert {
            null
        }
    }

    @Test
    fun nonAbstract() = service.nonAbstract("hello").assert {
        "hello"
    }

    @Test
    fun repeatNonAbstract() = service.nonAbstract("hello2").assert {
        "hello2"
    }

    @Test
    fun nonAbstractAndSuspend() = runBlocking {
        service.nonAbstractAndSuspend("hello").assert {
            "hello"
        }
    }

    @Test
    fun jvmDefault() = service.jvmDefault("hello").assert {
        "hello"
    }

    @Test
    fun jvmDefaultAndSuspend() = runBlocking {
        service.jvmDefaultAndSuspend("hello").assert {
            "hello"
        }
    }

    @Test
    fun postJson() = runBlocking {
        val jsonObject = jsonObject(
            "key1" to "value1"
        )
        service.postJson(jsonObject).await().contentLength.assert {
            jsonObject.toString().length
        }
    }

    @Test
    fun url() = runBlocking {
        service.url("user", "value1").await().url.assert {
            LOCALHOST + "user?arg1=value1"
        }
    }

    @Test
    fun pathWithSlash() = runBlocking {
        service.pathWithSlash("a/b/c").await().url.assert {
            LOCALHOST + "user/a/b/c/repos"
        }
    }

    @Test
    fun queryWithEncode() = runBlocking {
        service.queryEncode().await().url.assert {
            "$LOCALHOST?arg1=%21"
        }
    }

    @Test
    fun filedEncode() = runBlocking {
        service.fieldEncode().await().contentLength.assert {
            "param1=1+2%21%2B%2F".length
        }
    }

    @Test
    fun paramNullable() = runBlocking {
        service.paramNullable(null).await().url.assert {
            LOCALHOST
        }
    }

    @Suppress("RemoveRedundantQualifierName")
    @Test
    fun functionInCompanionObject() = Service.functionInCompanionObject("hello").assert {
        "hello"
    }

    @Suppress("RemoveRedundantQualifierName")
    @Test
    fun jvmStatic() = Service.jvmStatic("hello").assert {
        "hello"
    }

    @AfterAll
    fun dispose() {
        httpClient.close()
    }
}
