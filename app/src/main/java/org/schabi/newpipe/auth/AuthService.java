package org.schabi.newpipe.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.database.RemoteDatabaseClient;
import org.schabi.newpipe.database.RemoteDatabaseException;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

public class AuthService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static volatile AuthService instance;

    public static final String LOGIN_ENDPOINT = "/auth/signin";
    public static final String SIGNUP_ENDPOINT = "/auth/signup";

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final RemoteDatabaseClient client;

    private String username;
    private String authToken;
    private Boolean loggedIn;

    public static AuthService getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AuthService.class) {
                if (instance == null) {
                    instance = new AuthService(context);
                }
            }
        }

        return instance;
    }

    private AuthService(Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        this.client = RemoteDatabaseClient.getInstance(context);
    }

    public boolean isLoggedIn(){
        if(null == loggedIn){
            loggedIn = sharedPreferences.getBoolean("logged_in", false);
        }
        return loggedIn;
    }

    public boolean skipLogin(){
        return sharedPreferences.getBoolean("skip_login", false);
    }

    public Completable login(String username, String password){

        return Completable.fromAction(() -> {
            String req = buildRequest(username, password);
            String response = client.post(LOGIN_ENDPOINT, req);
            JsonObject jsonObject = JsonParser.object().from(response);
            String tokenType = jsonObject.getString("tokenType");
            String accessToken = jsonObject.getString("accessToken");
            if(null == tokenType || null == accessToken) throw new RemoteDatabaseException("Unable to authenticate");

            // create encryption key
            EncryptionUtils encryptionUtils = EncryptionUtils.getInstance(context);
            encryptionUtils.createKey(context, username, password);

            // update shared prefs
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("username", username);
            editor.putString("token", tokenType + " " + accessToken);
            editor.putBoolean("logged_in", true);
            editor.apply();
            this.username = username;
            this.authToken = tokenType + " " + accessToken;
            this.loggedIn = true;
        }).subscribeOn(Schedulers.io());

    }

    public Completable logout(){

        return Completable.fromAction(() -> {
            // delete encryption key
            EncryptionUtils.getInstance(context).deleteKey(context);
            // update shared prefs
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("username");
            editor.remove("token");
            editor.putBoolean("logged_in", false);
            editor.apply();
            this.username = null;
            this.authToken = null;
            this.loggedIn = false;
        }).subscribeOn(Schedulers.io());

    }

    public Completable signup(String username, String password){

        return Completable.fromAction(() -> {
            String req = buildRequest(username, password);
            client.post(SIGNUP_ENDPOINT, req);
        }).andThen(login(username, password)).subscribeOn(Schedulers.io());

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals("token")){
            authToken = sharedPreferences.getString(key, null);
        }else if(key.equals("logged_in")){
            loggedIn = sharedPreferences.getBoolean(key, false);
        }else if(key.equals("username")){
            username = sharedPreferences.getString(key, null);
        }
    }

    @Nullable
    private String getHash(String input){
        String hash = null;
        try {
            hash = EncryptionUtils.getSHA(input);
        } catch (NoSuchAlgorithmException e) {
            // ?
        }
        return hash;
    }

    private String buildRequest(String username, String password){
        String usernameHash = getHash(username);
        String passwordHash = getHash(password);
        Objects.requireNonNull(usernameHash, "failed to calculate username hash");
        Objects.requireNonNull(passwordHash, "failed to calculate password hash");
        JsonBuilder json = JsonObject.builder();
        json.value("username", usernameHash);
        json.value("password", passwordHash);
        return JsonWriter.string(json.done());
    }

    public String getToken(){
        if(null == authToken){
            authToken = sharedPreferences.getString("token", null);
        }
        return authToken;
    }

    public String getUsername(){
        if(null == username){
            username = sharedPreferences.getString("username", null);
        }
        return username;
    }
}
