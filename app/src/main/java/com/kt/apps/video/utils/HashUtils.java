package com.kt.apps.video.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {
    private HashUtils() {

    }
    public static String hashSC(final String appUser, final String appSCK, final long currentTimeMillis) {
        byte[] bytesOfMessage;

        try {
            bytesOfMessage = (appUser + currentTimeMillis).getBytes(StandardCharsets.UTF_8);

            final MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] firstDigest = md.digest(bytesOfMessage);
            String fistHashText = getHashStringFromByteArray(firstDigest);

            String finalHash = appSCK + fistHashText;
            byte[] bytesOfMessageSecond = finalHash.getBytes(StandardCharsets.UTF_8);
            byte[] secondDigest = md.digest(bytesOfMessageSecond);

            return getHashStringFromByteArray(secondDigest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return "";
    }

    private static String getHashStringFromByteArray(byte[] firstDigest) {
        BigInteger bigInt = new BigInteger(1, firstDigest);
        StringBuilder fistHashText = new StringBuilder(bigInt.toString(16));

        while(fistHashText.length() < 32 ){
            fistHashText.insert(0, "0");
        }
        return fistHashText.toString();
    }

    public static String hashSHA256(String lbkClientId, Long currentTime, String key) {
        return hashSHA256(currentTime + hashSHA256(lbkClientId + key));
    }

    public static String hashSHA256(String strHash) {
        try {
            byte[] bytesOfMessage = strHash.getBytes(StandardCharsets.UTF_8);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytesDigest = messageDigest.digest(bytesOfMessage);
            return bytesToHex(bytesDigest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String calculateMD5(File file) {
        MessageDigest digest;
        InputStream is;
        try {
            digest = MessageDigest.getInstance("MD5");
            is = new FileInputStream(file);
        } catch (NoSuchAlgorithmException | FileNotFoundException e) {
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static boolean checkMD5(String md5, File file) {
        if (md5.isEmpty() || file == null) {
            return false;
        }

        String calculatedDigest = calculateMD5(file);
        if (calculatedDigest == null) {
            return false;
        }
        return calculatedDigest.equalsIgnoreCase(md5);
    }
}
