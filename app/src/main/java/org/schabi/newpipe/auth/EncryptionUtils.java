package org.schabi.newpipe.auth;

import android.content.Context;
import android.util.Log;

import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.Registry;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.google.crypto.tink.proto.AesSivKey;
import com.google.crypto.tink.subtle.Base64;
import com.google.protobuf.ByteString;

import org.schabi.newpipe.MainActivity;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class EncryptionUtils {

    private EncryptionUtils() {
    }

    private static volatile EncryptionUtils instance;

    private DeterministicAead daead;

    public static final String ENC_KEY_FILE = "encKeyFile";

    private final String TAG = "EncryptionUtils@" + Integer.toHexString(hashCode());
    private final boolean DEBUG = MainActivity.DEBUG;

    public static EncryptionUtils getInstance(Context context) throws GeneralSecurityException, IOException {

        if (instance == null) {
            synchronized (EncryptionUtils.class) {
                if (instance == null) {
                    DeterministicAeadConfig.register();
                    instance = new EncryptionUtils();
                    instance.loadKey(context);
                }
            }
        }
        return instance;
    }

    private void loadKey(Context context) throws GeneralSecurityException {
        try(FileInputStream fis = context.openFileInput(ENC_KEY_FILE)){
            AesSivKey aesSivKey = AesSivKey.parseFrom(fis);
            this.daead = Registry.getPrimitive(DeterministicAeadConfig.AES_SIV_TYPE_URL, aesSivKey, DeterministicAead.class);
        } catch (IOException e) {
            if(DEBUG) Log.d(TAG, "unable to load encKey", e);
        }
    }

    protected void createKey(Context context, String username, String password) throws IOException, GeneralSecurityException {
        SecretKey key = generateKey(password.toCharArray(), username.getBytes(StandardCharsets.UTF_8));
        AesSivKey aesSivKey = AesSivKey.newBuilder().setKeyValue(ByteString.copyFrom(key.getEncoded())).build();

        try(FileOutputStream fos = context.openFileOutput(ENC_KEY_FILE, Context.MODE_PRIVATE)){
            aesSivKey.writeTo(fos);
        }

        this.daead = Registry.getPrimitive(DeterministicAeadConfig.AES_SIV_TYPE_URL, aesSivKey, DeterministicAead.class);
    }

    protected void deleteKey(Context context){
        context.deleteFile(EncryptionUtils.ENC_KEY_FILE);
        this.daead = null;
    }

    private String encrypt(byte[] plainText) throws GeneralSecurityException {
        assertDaeadNotNull();
        byte[] cipherText = daead.encryptDeterministically(plainText, "".getBytes());
        return Base64.encode(cipherText);
    }

    public String encrypt(long data) throws GeneralSecurityException {
        byte[] plainText = BigInteger.valueOf(data).toByteArray();
        return encrypt(plainText);
    }

    public String encrypt(String data) throws GeneralSecurityException {
        byte[] plainText = data.getBytes(StandardCharsets.UTF_8);
        return encrypt(plainText);
    }

    private byte[] decrypt(byte[] cipherText) throws GeneralSecurityException {
        assertDaeadNotNull();
        byte[] decrypted = daead.decryptDeterministically(cipherText, "".getBytes());
        return decrypted;
    }

    public long decryptAsLong(String data) throws GeneralSecurityException {
        byte[] decoded = Base64.decode(data);
        byte[] decrypted = decrypt(decoded);
        BigInteger bigInteger = new BigInteger(decrypted);
        return bigInteger.longValue();
    }

    public String decrypt(String data) throws GeneralSecurityException {
        byte[] decoded = Base64.decode(data);
        byte[] decrypted = decrypt(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private void assertDaeadNotNull(){
        if(null == daead) throw new IllegalStateException("encryption key not set");
    }

    public SecretKey generateKey(char[] passphraseOrPin, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        final int iterations = 1000;

        // Generate a 512-bit key
        final int outputKeyLength = 512;

        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec keySpec = new PBEKeySpec(passphraseOrPin, salt, iterations, outputKeyLength);
        SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
        return secretKey;
    }

    public static String getSHA(String input) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return toHexString(hash);
    }

    public static String toHexString(byte[] hash)
    {
        BigInteger number = new BigInteger(1, hash);
        StringBuilder hexString = new StringBuilder(number.toString(16));

        while (hexString.length() < 32) {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }
}
