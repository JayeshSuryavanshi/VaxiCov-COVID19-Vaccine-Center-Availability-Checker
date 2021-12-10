package com.akshaychavan.vaxicov.network;

import androidx.annotation.NonNull;

import com.akshaychavan.vaxicov.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Lazily built, process-wide Retrofit service for the CoWIN API. A single
 * OkHttp client is shared; the browser User-Agent CoWIN insists on is added
 * by an interceptor instead of being repeated on every endpoint.
 */
public final class ApiClient {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
    private static final long TIMEOUT_SECONDS = 30;

    private static volatile CowinApi cowinApi;

    private ApiClient() {
    }

    @NonNull
    public static CowinApi cowin() {
        if (cowinApi == null) {
            synchronized (ApiClient.class) {
                if (cowinApi == null) {
                    cowinApi = build(BuildConfig.COWIN_BASE_URL);
                }
            }
        }
        return cowinApi;
    }

    /** Builds a fresh service for the given base URL (exposed for tests and alternative deployments). */
    @NonNull
    public static CowinApi build(@NonNull String baseUrl) {
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(new HeaderInterceptor("User-Agent", BROWSER_USER_AGENT));
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            client.addInterceptor(logging);
        }
        Gson gson = new GsonBuilder().setLenient().create();
        return new Retrofit.Builder()
                .baseUrl(withTrailingSlash(baseUrl))
                .client(client.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(CowinApi.class);
    }

    /** Retrofit insists on a trailing slash; be forgiving about how the URL was configured. */
    @NonNull
    static String withTrailingSlash(@NonNull String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    /** Adds a fixed header to every request. */
    private static final class HeaderInterceptor implements Interceptor {
        private final String name;
        private final String value;

        HeaderInterceptor(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request().newBuilder().header(name, value).build();
            return chain.proceed(request);
        }
    }
}
