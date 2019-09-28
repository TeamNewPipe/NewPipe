package org.schabi.newpipe.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.auth.AuthService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RemoteDatabaseClient implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static RemoteDatabaseClient INSTANCE;
    private static final String AUTHORIZATION = "Authorization";
    private static final String MEDIA_TYPE = "application/json; charset=UTF-8";
    private static final String DEFAULT_URL = "https://65d3af6c-9704-4d26-b936-47b59dda70b0.pub.cloud.scaleway.com/api";

    private final OkHttpClient client;
    private String url;
    private final Context context;

    private RemoteDatabaseClient(Context context) {
        this.client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                //.cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"), 16 * 1024 * 1024))
                .build();
        this.context = context;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(this);
        this.url  = preferences.getString(context.getString(R.string.sync_server_url_key), DEFAULT_URL);
    }

    synchronized public static RemoteDatabaseClient getInstance(Context context) {
        if (null == INSTANCE) {
            INSTANCE = new RemoteDatabaseClient(context);
        }
        return INSTANCE;
    }

    public String get(String endpoint) {

        Request request = new Request.Builder()
                .url(url + endpoint)
                .addHeader(AUTHORIZATION, getAuthorization())
                .build();

        return execute(request);
    }

    public String get(String endpoint, Map<String, String> params) {
        HttpUrl.Builder httpBuilder = HttpUrl.get(url + endpoint).newBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            httpBuilder.addQueryParameter(param.getKey(), param.getValue());
        }
        Request request = new Request.Builder()
                .url(httpBuilder.build())
                .addHeader(AUTHORIZATION, getAuthorization())
                .build();

        return execute(request);
    }

    public String post(String endpoint, String body) {

        RequestBody requestBody = RequestBody.create(MediaType.parse(MEDIA_TYPE), body);

        Request.Builder builder = new Request.Builder()
                .url(url + endpoint)
                .post(requestBody);

        if (!(endpoint.equals(AuthService.LOGIN_ENDPOINT) || endpoint.equals(AuthService.SIGNUP_ENDPOINT))) {
            builder.addHeader(AUTHORIZATION, getAuthorization());
        }

        return execute(builder.build());
    }

    public String put(String endpoint, String body) {
        RequestBody requestBody = RequestBody.create(MediaType.parse(MEDIA_TYPE), body);

        Request request = new Request.Builder()
                .url(url + endpoint)
                .addHeader(AUTHORIZATION, getAuthorization())
                .put(requestBody)
                .build();

        return execute(request);
    }

    public String delete(String endpoint) {
        Request request = new Request.Builder()
                .url(url + endpoint)
                .addHeader(AUTHORIZATION, getAuthorization())
                .delete()
                .build();

        return execute(request);
    }

    @NonNull
    private String execute(Request request) {
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new RemoteDatabaseException("Error communicating with server");
            }
            return response.body().string();
        } catch (IOException e) {
            throw new RemoteDatabaseException("Error communicating with server", e);
        }
    }

    private String getAuthorization() {
        return AuthService.getInstance(context).getToken();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(context.getString(R.string.sync_server_url_key))){
            this.url = sharedPreferences.getString(key, DEFAULT_URL);
        }
    }
}
