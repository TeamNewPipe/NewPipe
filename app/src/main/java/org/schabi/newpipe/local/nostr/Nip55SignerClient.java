package org.schabi.newpipe.local.nostr;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

final class Nip55SignerClient {
    private static final String TAG = "Nip55SignerClient";
    private static final String OP_SIGN_EVENT = "SIGN_EVENT";
    private static final String OP_NIP44_ENCRYPT = "NIP44_ENCRYPT";
    private static final String OP_NIP44_DECRYPT = "NIP44_DECRYPT";

    private Nip55SignerClient() {
    }

    @Nullable
    static String nip44Encrypt(@NonNull final Context context,
                               @NonNull final String signerPackage,
                               @NonNull final String plainText,
                               @NonNull final String recipientPubKeyHex,
                               @NonNull final String currentUserPubKeyHex) {
        final QueryResult queryResult = querySigner(context, signerPackage, OP_NIP44_ENCRYPT,
                new String[]{plainText, recipientPubKeyHex, currentUserPubKeyHex});
        if (queryResult == null) {
            return null;
        }
        if (queryResult.rejected) {
            Log.w(TAG, "Signer rejected NIP-44 encrypt: " + queryResult.errorMessage);
            return null;
        }
        return queryResult.result;
    }

    @Nullable
    static String nip44Decrypt(@NonNull final Context context,
                               @NonNull final String signerPackage,
                               @NonNull final String cipherText,
                               @NonNull final String senderPubKeyHex,
                               @NonNull final String currentUserPubKeyHex) {
        final QueryResult queryResult = querySigner(context, signerPackage, OP_NIP44_DECRYPT,
                new String[]{cipherText, senderPubKeyHex, currentUserPubKeyHex});
        if (queryResult == null) {
            return null;
        }
        if (queryResult.rejected) {
            Log.w(TAG, "Signer rejected NIP-44 decrypt: " + queryResult.errorMessage);
            return null;
        }
        return queryResult.result;
    }

    @Nullable
    static JSONObject signEvent(@NonNull final Context context,
                                @NonNull final String signerPackage,
                                @NonNull final JSONObject unsignedEvent,
                                @NonNull final String currentUserPubKeyHex,
                                @NonNull final String fallbackEventId) {
        final QueryResult queryResult = querySigner(context, signerPackage, OP_SIGN_EVENT,
                new String[]{unsignedEvent.toString(), "", currentUserPubKeyHex});
        if (queryResult == null) {
            return null;
        }
        if (queryResult.rejected) {
            Log.w(TAG, "Signer rejected SIGN_EVENT: " + queryResult.errorMessage);
            return null;
        }

        try {
            JSONObject signedEvent = null;
            if (!TextUtils.isEmpty(queryResult.eventJson)) {
                signedEvent = new JSONObject(queryResult.eventJson);
            } else if (!TextUtils.isEmpty(queryResult.result)) {
                final String result = queryResult.result.trim();
                if (result.startsWith("{")) {
                    signedEvent = new JSONObject(result);
                } else {
                    signedEvent = new JSONObject(unsignedEvent.toString())
                            .put("sig", result);
                }
            }
            if (signedEvent == null) {
                return null;
            }

            if (TextUtils.isEmpty(signedEvent.optString("pubkey", null))) {
                signedEvent.put("pubkey", unsignedEvent.optString("pubkey", ""));
            }
            if (TextUtils.isEmpty(signedEvent.optString("id", null))) {
                signedEvent.put("id", fallbackEventId);
            }
            return signedEvent;
        } catch (final JSONException e) {
            Log.w(TAG, "Invalid signer event response", e);
            return null;
        }
    }

    @Nullable
    private static QueryResult querySigner(@NonNull final Context context,
                                           @NonNull final String signerPackage,
                                           @NonNull final String operation,
                                           @NonNull final String[] arguments) {
        final Uri uri = Uri.parse("content://" + signerPackage + "." + operation);
        final ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri, arguments, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            final String result = firstNonEmpty(
                    readCursorString(cursor, "result"),
                    readCursorString(cursor, "signature"),
                    readCursorString(cursor, "event"),
                    readCursorString(cursor, 0)
            );
            final String eventJson = readCursorString(cursor, "event");
            final boolean rejected = readCursorBoolean(cursor, "rejected");
            final String errorMessage = readCursorString(cursor, "error");
            return new QueryResult(result, eventJson, rejected, errorMessage);
        } catch (final SecurityException e) {
            Log.w(TAG, "Signer query blocked by OS permissions", e);
            return null;
        } catch (final RuntimeException e) {
            Log.w(TAG, "Signer query failed for " + operation, e);
            return null;
        }
    }

    private static boolean readCursorBoolean(@NonNull final Cursor cursor,
                                             @NonNull final String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        if (index < 0 || cursor.isNull(index)) {
            return false;
        }
        final String value = cursor.getString(index);
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    @Nullable
    private static String readCursorString(@NonNull final Cursor cursor,
                                           @NonNull final String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        if (index < 0 || cursor.isNull(index)) {
            return null;
        }
        final String value = cursor.getString(index);
        return TextUtils.isEmpty(value) ? null : value;
    }

    @Nullable
    private static String readCursorString(@NonNull final Cursor cursor,
                                           final int columnIndex) {
        if (columnIndex < 0
                || columnIndex >= cursor.getColumnCount()
                || cursor.isNull(columnIndex)) {
            return null;
        }
        final String value = cursor.getString(columnIndex);
        return TextUtils.isEmpty(value) ? null : value;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable final String... values) {
        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private static final class QueryResult {
        @Nullable
        final String result;
        @Nullable
        final String eventJson;
        final boolean rejected;
        @Nullable
        final String errorMessage;

        QueryResult(@Nullable final String result,
                    @Nullable final String eventJson,
                    final boolean rejected,
                    @Nullable final String errorMessage) {
            this.result = result;
            this.eventJson = eventJson;
            this.rejected = rejected;
            this.errorMessage = errorMessage;
        }
    }
}
