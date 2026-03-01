package org.schabi.newpipe.local.nostr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.database.subscription.NotificationMode;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.subscription.FeedGroupIcon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class NostrSyncManager {
    private static final String TAG = "NostrSyncManager";

    private static final String PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED =
            "nostr_sync_watch_history_enabled";
    private static final String PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED =
            "nostr_sync_subscriptions_enabled";
    private static final String PREF_NOSTR_NSEC = "nostr_nsec";
    private static final String PREF_NOSTR_NPUB = "nostr_npub";
    private static final String PREF_NOSTR_EXTERNAL_SIGNER = "nostr_external_signer";
    private static final String PREF_NOSTR_SIGNER_PACKAGE = "nostr_signer_package";
    private static final String PREF_NOSTR_RELAYS = "nostr_relays";
    private static final String PREF_NOSTR_ENABLED_RELAYS = "nostr_enabled_relays";
    private static final String PREF_NOSTR_SYNC_DEVICE_ID = "nostr_sync_device_id";
    private static final String PREF_NOSTR_LAST_CONNECTED_RELAYS =
            "nostr_sync_last_connected_relays";
    private static final String PREF_NOSTR_LAST_TOTAL_RELAYS = "nostr_sync_last_total_relays";

    private static final List<String> DEFAULT_RELAYS = Arrays.asList(
            "wss://relay.primal.net",
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://nostr.oxtr.dev",
            "wss://nos.lol",
            "wss://nostr.bitcoiner.social",
            "wss://nostr.semisol.dev",
            "wss://shu01.shugur.net",
            "wss://shu02.shugur.net",
            "wss://shu03.shugur.net",
            "wss://shu04.shugur.net",
            "wss://shu05.shugur.net"
    );
    private static final Set<String> DEFAULT_ENABLED_RELAYS = new HashSet<>(Arrays.asList(
            "wss://relay.primal.net",
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://nostr.oxtr.dev",
            "wss://nos.lol",
            "wss://nostr.bitcoiner.social",
            "wss://nostr.semisol.dev"
    ));
    private static final String AMBER_PACKAGE_NAME = "com.greenart7c3.nostrsigner";
    private static final String NIP55_URI = "nostrsigner:";
    private static final String NIP55_TYPE = "type";
    private static final String NIP55_TYPE_GET_PUBLIC_KEY = "get_public_key";
    private static final String CATEGORY_WATCH_HISTORY = "watch_history";
    private static final String CATEGORY_SUBSCRIPTIONS = "subscriptions";
    private static final String D_TAG_HISTORY_PREFIX = "newpipe-sync-watch-history:";
    private static final String D_TAG_SUBSCRIPTIONS_PREFIX = "newpipe-sync-subscriptions:";
    private static final int KIND_PROFILE_METADATA = 0;
    private static final int KIND_APP_DATA = 30078;
    private static final int RELAY_CONNECT_TIMEOUT_MS = 6000;
    private static final int RELAY_EOSE_TIMEOUT_MS = 7000;
    private static final int RELAY_PUBLISH_TIMEOUT_MS = 4500;
    private static final int MAX_HISTORY_RECORDS_PER_SNAPSHOT = 150;
    private static final int MAX_SUBSCRIPTIONS_PER_SNAPSHOT = 500;
    private static final int MAX_CATEGORY_DATA_BYTES = 28 * 1024;

    private static final AtomicBoolean SYNC_RUNNING = new AtomicBoolean(false);
    private static final OkHttpClient WS_CLIENT = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build();

    private NostrSyncManager() {
    }

    public static boolean isSyncRunning() {
        return SYNC_RUNNING.get();
    }

    @NonNull
    public static RelayStatus getRelayStatus(@NonNull final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        final int connected = Math.max(0,
                preferences.getInt(PREF_NOSTR_LAST_CONNECTED_RELAYS, 0));

        final Set<String> enabledRelays = getEnabledRelays(preferences);
        final int storedTotal = preferences.getInt(PREF_NOSTR_LAST_TOTAL_RELAYS, 0);
        final int total = Math.max(enabledRelays.size(), storedTotal);
        return new RelayStatus(Math.min(connected, total), total);
    }

    public static void requestSync(@NonNull final Context context) {
        if (!SYNC_RUNNING.compareAndSet(false, true)) {
            return;
        }

        Completable.fromAction(() -> syncBlocking(context.getApplicationContext()))
                .subscribeOn(Schedulers.io())
                .doFinally(() -> SYNC_RUNNING.set(false))
                .subscribe(
                        () -> {
                        },
                        throwable -> Log.w(TAG, "Sync failed", throwable)
                );
    }

    public static void publishProfileMetadata(@NonNull final Context context,
                                              @NonNull final String nsec,
                                              @Nullable final String name,
                                              @Nullable final String displayName,
                                              @Nullable final String pictureUrl) {
        final String normalizedNsec = trimToNull(nsec);
        final String normalizedName = trimToNull(name);
        final String normalizedDisplayName = trimToNull(displayName);
        final String normalizedPictureUrl = trimToNull(pictureUrl);
        if (TextUtils.isEmpty(normalizedNsec)) {
            return;
        }
        if (TextUtils.isEmpty(normalizedName)
                && TextUtils.isEmpty(normalizedDisplayName)
                && TextUtils.isEmpty(normalizedPictureUrl)) {
            return;
        }

        Completable.fromAction(() -> publishProfileMetadataBlocking(
                        context.getApplicationContext(),
                        normalizedNsec,
                        normalizedName,
                        normalizedDisplayName,
                        normalizedPictureUrl
                ))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {
                        },
                        throwable -> Log.w(TAG, "Profile metadata publish failed", throwable)
                );
    }

    private static void syncBlocking(@NonNull final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final boolean syncHistory = preferences.getBoolean(
                PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED, false
        );
        final boolean syncSubscriptions = preferences.getBoolean(
                PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED, false
        );
        if (!syncHistory && !syncSubscriptions) {
            return;
        }

        final String npub = preferences.getString(PREF_NOSTR_NPUB, null);
        if (TextUtils.isEmpty(npub)) {
            Log.d(TAG, "Skipping sync: missing npub");
            return;
        }

        final String nsec = preferences.getString(PREF_NOSTR_NSEC, null);
        final String localNsec = TextUtils.isEmpty(nsec) ? null : nsec;
        final boolean hasExternalSigner = preferences.getBoolean(PREF_NOSTR_EXTERNAL_SIGNER, false);
        if (localNsec == null && !hasExternalSigner) {
            Log.d(TAG, "Skipping sync: missing local nsec and no external signer");
            return;
        }

        final String pubKeyHex;
        try {
            pubKeyHex = localNsec != null
                    ? NostrKeyUtils.derivePublicKeyHexFromNsec(localNsec)
                    : NostrKeyUtils.toPublicKeyHex(npub);
        } catch (final RuntimeException e) {
            Log.w(TAG, "Skipping sync: invalid nostr identity", e);
            return;
        }

        final String signerPackage = localNsec == null
                ? resolveSignerPackage(context, preferences)
                : null;
        if (localNsec == null && TextUtils.isEmpty(signerPackage)) {
            Log.d(TAG, "Skipping sync: external signer package unavailable");
            return;
        }

        final String deviceId = getOrCreateDeviceId(preferences);
        final Set<String> relays = getEnabledRelays(preferences);
        updateRelayStatus(preferences, 0, relays.size());
        Log.d(TAG, "Starting sync. relays=" + relays.size()
                + " history=" + syncHistory + " subscriptions=" + syncSubscriptions);
        final List<JSONObject> relayEvents = fetchSyncEvents(relays, pubKeyHex);
        Log.d(TAG, "Fetched " + relayEvents.size() + " candidate events");

        final AppDatabase database = NewPipeDatabase.getInstance(context);

        if (syncHistory) {
            final Map<String, HistoryRecord> localHistory = readLocalHistory(database);
            final Map<String, HistoryRecord> remoteHistory =
                    readHistoryFromEvents(
                            relayEvents,
                            context,
                            localNsec,
                            signerPackage,
                            pubKeyHex
                    );
            final Map<String, HistoryRecord> mergedHistory =
                    mergeHistoryMaps(
                            remoteHistory,
                            localHistory
                    );
            Log.d(TAG, "History merge local=" + localHistory.size()
                    + " remote=" + remoteHistory.size()
                    + " merged=" + mergedHistory.size());
            applyHistoryToDatabase(database, mergedHistory);
            final int acceptedRelays = publishCategorySnapshot(
                    context,
                    relays,
                    localNsec,
                    signerPackage,
                    pubKeyHex,
                    CATEGORY_WATCH_HISTORY,
                    D_TAG_HISTORY_PREFIX + deviceId,
                    historyToJson(readLocalHistory(database))
            );
            updateRelayStatus(preferences, acceptedRelays, relays.size());
        }

        if (syncSubscriptions) {
            final Map<String, SubscriptionRecord> localSubscriptions =
                    readLocalSubscriptions(database);
            final Map<String, SubscriptionRecord> remoteSubscriptions =
                    readSubscriptionsFromEvents(
                            relayEvents,
                            context,
                            localNsec,
                            signerPackage,
                            pubKeyHex
                    );
            final Map<String, SubscriptionRecord> mergedSubscriptions =
                    mergeSubscriptionMaps(
                            remoteSubscriptions,
                            localSubscriptions
                    );
            Log.d(TAG, "Subscription merge local=" + localSubscriptions.size()
                    + " remote=" + remoteSubscriptions.size()
                    + " merged=" + mergedSubscriptions.size());
            applySubscriptionsToDatabase(database, mergedSubscriptions);
            final int acceptedRelays = publishCategorySnapshot(
                    context,
                    relays,
                    localNsec,
                    signerPackage,
                    pubKeyHex,
                    CATEGORY_SUBSCRIPTIONS,
                    D_TAG_SUBSCRIPTIONS_PREFIX + deviceId,
                    subscriptionsToJson(readLocalSubscriptions(database))
            );
            updateRelayStatus(preferences, acceptedRelays, relays.size());
        }
    }

    private static void publishProfileMetadataBlocking(@NonNull final Context context,
                                                       @NonNull final String nsec,
                                                       @Nullable final String name,
                                                       @Nullable final String displayName,
                                                       @Nullable final String pictureUrl) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final Set<String> relays = getEnabledRelays(preferences);
        if (relays.isEmpty()) {
            Log.d(TAG, "Skipping profile metadata publish: no enabled relays");
            return;
        }

        final String pubKeyHex;
        try {
            pubKeyHex = NostrKeyUtils.derivePublicKeyHexFromNsec(nsec);
        } catch (final RuntimeException e) {
            Log.w(TAG, "Skipping profile metadata publish: invalid nsec", e);
            return;
        }

        final JSONObject metadata = new JSONObject();
        try {
            if (!TextUtils.isEmpty(name)) {
                metadata.put("name", name);
            }
            if (!TextUtils.isEmpty(displayName)) {
                metadata.put("display_name", displayName);
            }
            if (!TextUtils.isEmpty(pictureUrl)) {
                metadata.put("picture", pictureUrl);
            }
        } catch (final JSONException e) {
            Log.w(TAG, "Skipping profile metadata publish: metadata serialization error", e);
            return;
        }

        final String content = metadata.toString();
        final long now = Instant.now().getEpochSecond();
        final JSONArray tags = new JSONArray();
        final JSONObject unsignedEvent = new JSONObject();
        try {
            unsignedEvent.put("pubkey", pubKeyHex);
            unsignedEvent.put("created_at", now);
            unsignedEvent.put("kind", KIND_PROFILE_METADATA);
            unsignedEvent.put("tags", tags);
            unsignedEvent.put("content", content);
        } catch (final JSONException e) {
            return;
        }

        final String serialized = serializeEventForId(
                pubKeyHex,
                now,
                KIND_PROFILE_METADATA,
                tags,
                content
        );
        final byte[] eventHash = sha256(serialized.getBytes(StandardCharsets.UTF_8));
        final String eventId = bytesToHex(eventHash);
        final JSONObject signedEvent;
        try {
            final String signature = NostrKeyUtils.signEventId(nsec, eventHash);
            signedEvent = new JSONObject(unsignedEvent.toString())
                    .put("id", eventId)
                    .put("sig", signature);
        } catch (final RuntimeException | JSONException e) {
            Log.w(TAG, "Skipping profile metadata publish: signing failed", e);
            return;
        }

        final String eventMessage = new JSONArray().put("EVENT").put(signedEvent).toString();
        int acceptedRelays = 0;
        for (final String relay : relays) {
            if (publishToRelay(relay, eventMessage, eventId)) {
                acceptedRelays++;
            }
        }
        Log.d(TAG, "Published profile metadata to "
                + acceptedRelays + "/" + relays.size() + " relays");
    }

    @NonNull
    private static String getOrCreateDeviceId(@NonNull final SharedPreferences preferences) {
        final String existing = preferences.getString(PREF_NOSTR_SYNC_DEVICE_ID, null);
        if (!TextUtils.isEmpty(existing)) {
            return existing;
        }

        final String created = UUID.randomUUID().toString();
        preferences.edit().putString(PREF_NOSTR_SYNC_DEVICE_ID, created).apply();
        return created;
    }

    @NonNull
    private static Set<String> getEnabledRelays(@NonNull final SharedPreferences preferences) {
        final Set<String> configuredRelays = new HashSet<>(getConfiguredRelays(preferences));
        final Set<String> stored = preferences.getStringSet(PREF_NOSTR_ENABLED_RELAYS, null);
        if (stored != null) {
            final Set<String> filtered = new HashSet<>(stored);
            filtered.retainAll(configuredRelays);
            return filtered;
        }
        final Set<String> defaults = new HashSet<>(DEFAULT_ENABLED_RELAYS);
        defaults.retainAll(configuredRelays);
        return defaults;
    }

    @NonNull
    private static List<String> getConfiguredRelays(@NonNull final SharedPreferences preferences) {
        final String raw = preferences.getString(PREF_NOSTR_RELAYS, null);
        if (TextUtils.isEmpty(raw)) {
            return DEFAULT_RELAYS;
        }

        final List<String> relays = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        try {
            final JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                final String relay = jsonArray.optString(i, "").trim();
                if (TextUtils.isEmpty(relay) || !seen.add(relay)) {
                    continue;
                }
                relays.add(relay);
            }
        } catch (final JSONException ignored) {
            return DEFAULT_RELAYS;
        }
        return relays;
    }

    @Nullable
    private static String resolveSignerPackage(@NonNull final Context context,
                                               @NonNull final SharedPreferences preferences) {
        final String storedPackage = preferences.getString(PREF_NOSTR_SIGNER_PACKAGE, null);
        if (!TextUtils.isEmpty(storedPackage)) {
            return storedPackage;
        }

        final Intent signerIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(NIP55_URI));
        signerIntent.putExtra(NIP55_TYPE, NIP55_TYPE_GET_PUBLIC_KEY);
        final List<ResolveInfo> candidates = context.getPackageManager()
                .queryIntentActivities(signerIntent, 0);
        if (candidates.isEmpty()) {
            return null;
        }

        String selectedPackage = null;
        for (final ResolveInfo candidate : candidates) {
            if (candidate.activityInfo == null
                    || TextUtils.isEmpty(candidate.activityInfo.packageName)) {
                continue;
            }
            final String packageName = candidate.activityInfo.packageName;
            if (AMBER_PACKAGE_NAME.equals(packageName)) {
                selectedPackage = packageName;
                break;
            }
            if (selectedPackage == null) {
                selectedPackage = packageName;
            }
        }

        if (!TextUtils.isEmpty(selectedPackage)) {
            preferences.edit().putString(PREF_NOSTR_SIGNER_PACKAGE, selectedPackage).apply();
        }
        return selectedPackage;
    }

    private static void updateRelayStatus(@NonNull final SharedPreferences preferences,
                                          final int connectedRelays,
                                          final int totalRelays) {
        preferences.edit()
                .putInt(PREF_NOSTR_LAST_CONNECTED_RELAYS, Math.max(0, connectedRelays))
                .putInt(PREF_NOSTR_LAST_TOTAL_RELAYS, Math.max(0, totalRelays))
                .apply();
    }

    @NonNull
    private static List<JSONObject> fetchSyncEvents(@NonNull final Set<String> relays,
                                                    @NonNull final String pubKeyHex) {
        final Map<String, JSONObject> dedupById = new LinkedHashMap<>();
        final JSONObject filter = new JSONObject();
        try {
            filter.put("authors", new JSONArray().put(pubKeyHex));
            filter.put("kinds", new JSONArray().put(KIND_APP_DATA));
            filter.put("limit", 1000);
        } catch (final JSONException e) {
            return new ArrayList<>();
        }

        for (final String relay : relays) {
            final List<JSONObject> eventsFromRelay = fetchFromRelay(relay, filter);
            for (final JSONObject event : eventsFromRelay) {
                final String id = event.optString("id", "");
                if (!TextUtils.isEmpty(id)) {
                    dedupById.put(id, event);
                }
            }
        }
        return new ArrayList<>(dedupById.values());
    }

    @NonNull
    private static List<JSONObject> fetchFromRelay(@NonNull final String relayUrl,
                                                   @NonNull final JSONObject filter) {
        final List<JSONObject> events = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch eoseLatch = new CountDownLatch(1);
        final String subscriptionId = "np-sync-" + System.nanoTime();
        final String reqMessage;

        try {
            reqMessage = new JSONArray()
                    .put("REQ")
                    .put(subscriptionId)
                    .put(filter)
                    .toString();
        } catch (final RuntimeException e) {
            return events;
        }

        final Request request = new Request.Builder().url(relayUrl).build();
        final WebSocket webSocket = WS_CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull final WebSocket webSocket,
                               @NonNull final okhttp3.Response response) {
                openLatch.countDown();
            }

            @Override
            public void onMessage(@NonNull final WebSocket webSocket,
                                  @NonNull final String text) {
                handleRelayMessage(text, subscriptionId, events, eoseLatch);
            }

            @Override
            public void onFailure(@NonNull final WebSocket webSocket,
                                  @NonNull final Throwable t,
                                  @Nullable final okhttp3.Response response) {
                openLatch.countDown();
                eoseLatch.countDown();
            }

            @Override
            public void onClosed(@NonNull final WebSocket webSocket, final int code,
                                 @NonNull final String reason) {
                eoseLatch.countDown();
            }
        });

        try {
            if (!openLatch.await(RELAY_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                webSocket.cancel();
                return events;
            }

            webSocket.send(reqMessage);
            eoseLatch.await(RELAY_EOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            webSocket.send(new JSONArray().put("CLOSE").put(subscriptionId).toString());
            webSocket.close(1000, "done");
        } catch (final Exception e) {
            webSocket.cancel();
        }
        return events;
    }

    private static void handleRelayMessage(@NonNull final String message,
                                           @NonNull final String subscriptionId,
                                           @NonNull final List<JSONObject> sink,
                                           @NonNull final CountDownLatch eoseLatch) {
        try {
            final JSONArray parsed = new JSONArray(message);
            if (parsed.length() < 2) {
                return;
            }
            final String type = parsed.optString(0, "");
            if ("EOSE".equals(type) && subscriptionId.equals(parsed.optString(1, ""))) {
                eoseLatch.countDown();
                return;
            }

            if (!"EVENT".equals(type) || parsed.length() < 3) {
                return;
            }
            if (!subscriptionId.equals(parsed.optString(1, ""))) {
                return;
            }
            final JSONObject event = parsed.optJSONObject(2);
            if (event != null) {
                sink.add(event);
            }
        } catch (final JSONException ignored) {
            // Ignore malformed relay frames.
        }
    }

    @NonNull
    private static Map<String, HistoryRecord> readHistoryFromEvents(
            @NonNull final List<JSONObject> events,
            @NonNull final Context context,
            @Nullable final String nsec,
            @Nullable final String signerPackage,
            @NonNull final String currentUserPubKeyHex) {
        final Map<String, HistoryRecord> merged = new HashMap<>();
        int decryptedPayloads = 0;
        for (final JSONObject event : events) {
            if (event.optInt("kind", -1) != KIND_APP_DATA) {
                continue;
            }
            final String dTag = getDTag(event);
            if (!dTag.startsWith(D_TAG_HISTORY_PREFIX)) {
                continue;
            }

            final JSONObject payload = decryptPayload(
                    event,
                    context,
                    nsec,
                    signerPackage,
                    currentUserPubKeyHex
            );
            if (payload == null || !CATEGORY_WATCH_HISTORY.equals(
                    payload.optString("category", "")
            )) {
                continue;
            }
            decryptedPayloads++;

            final JSONObject data = payload.optJSONObject("data");
            if (data == null) {
                continue;
            }
            final Iterator<String> keyIterator = data.keys();
            while (keyIterator.hasNext()) {
                final String key = keyIterator.next();
                final JSONObject item = data.optJSONObject(key);
                if (item == null) {
                    continue;
                }
                final HistoryRecord parsed = HistoryRecord.fromJson(item);
                if (parsed == null) {
                    continue;
                }
                mergedHistoryRecord(merged, key, parsed);
            }
        }
        Log.d(TAG, "Decoded history payloads=" + decryptedPayloads
                + " records=" + merged.size());
        return merged;
    }

    @NonNull
    private static Map<String, SubscriptionRecord> readSubscriptionsFromEvents(
            @NonNull final List<JSONObject> events,
            @NonNull final Context context,
            @Nullable final String nsec,
            @Nullable final String signerPackage,
            @NonNull final String currentUserPubKeyHex) {
        final Map<String, SubscriptionRecord> merged = new HashMap<>();
        int decryptedPayloads = 0;
        for (final JSONObject event : events) {
            if (event.optInt("kind", -1) != KIND_APP_DATA) {
                continue;
            }
            final String dTag = getDTag(event);
            if (!dTag.startsWith(D_TAG_SUBSCRIPTIONS_PREFIX)) {
                continue;
            }

            final JSONObject payload = decryptPayload(
                    event,
                    context,
                    nsec,
                    signerPackage,
                    currentUserPubKeyHex
            );
            if (payload == null || !CATEGORY_SUBSCRIPTIONS.equals(
                    payload.optString("category", "")
            )) {
                continue;
            }
            decryptedPayloads++;

            final JSONObject data = payload.optJSONObject("data");
            if (data == null) {
                continue;
            }
            final Iterator<String> keyIterator = data.keys();
            while (keyIterator.hasNext()) {
                final String key = keyIterator.next();
                final JSONObject item = data.optJSONObject(key);
                if (item == null) {
                    continue;
                }
                final SubscriptionRecord parsed = SubscriptionRecord.fromJson(item);
                if (parsed == null) {
                    continue;
                }
                mergedSubscriptionRecord(merged, key, parsed);
            }
        }
        Log.d(TAG, "Decoded subscriptions payloads=" + decryptedPayloads
                + " records=" + merged.size());
        return merged;
    }

    @Nullable
    private static JSONObject decryptPayload(@NonNull final JSONObject event,
                                             @NonNull final Context context,
                                             @Nullable final String nsec,
                                             @Nullable final String signerPackage,
                                             @NonNull final String currentUserPubKeyHex) {
        try {
            final String senderPubKey = event.optString("pubkey", null);
            final String content = event.optString("content", null);
            if (TextUtils.isEmpty(senderPubKey) || TextUtils.isEmpty(content)) {
                return null;
            }
            final String encryptedPayload = extractEncryptedContent(content);
            final String plain;
            if (!TextUtils.isEmpty(nsec)) {
                plain = NostrKeyUtils.decryptNip44(
                        nsec,
                        senderPubKey,
                        encryptedPayload
                );
            } else if (!TextUtils.isEmpty(signerPackage)) {
                plain = Nip55SignerClient.nip44Decrypt(
                        context,
                        signerPackage,
                        encryptedPayload,
                        senderPubKey,
                        currentUserPubKeyHex
                );
            } else {
                return null;
            }
            if (TextUtils.isEmpty(plain)) {
                return null;
            }
            return new JSONObject(plain);
        } catch (final RuntimeException | JSONException e) {
            return null;
        }
    }

    @NonNull
    private static String extractEncryptedContent(@NonNull final String eventContent)
            throws JSONException {
        final String trimmed = eventContent.trim();
        if (!trimmed.startsWith("{")) {
            // Backward compatibility for older payloads stored as raw NIP-44 base64 text.
            return eventContent;
        }

        final JSONObject wrapped = new JSONObject(trimmed);
        final Object dataField = wrapped.opt("data");
        if (dataField instanceof JSONObject) {
            final JSONObject dataObject = (JSONObject) dataField;
            final String dataCiphertext = dataObject.optString("ciphertext", null);
            if (!TextUtils.isEmpty(dataCiphertext)) {
                return dataCiphertext;
            }
            final String dataEncrypted = dataObject.optString("encrypted", null);
            if (!TextUtils.isEmpty(dataEncrypted)) {
                return dataEncrypted;
            }
        } else if (dataField instanceof String) {
            final String dataString = (String) dataField;
            if (!TextUtils.isEmpty(dataString)) {
                return dataString;
            }
        }

        final String ciphertext = wrapped.optString("ciphertext", null);
        if (!TextUtils.isEmpty(ciphertext)) {
            return ciphertext;
        }
        final String encrypted = wrapped.optString("encrypted", null);
        if (!TextUtils.isEmpty(encrypted)) {
            return encrypted;
        }
        throw new JSONException("Missing encrypted payload");
    }

    @NonNull
    private static String getDTag(@NonNull final JSONObject event) {
        final JSONArray tags = event.optJSONArray("tags");
        if (tags == null) {
            return "";
        }
        for (int i = 0; i < tags.length(); i++) {
            final JSONArray tag = tags.optJSONArray(i);
            if (tag == null || tag.length() < 2) {
                continue;
            }
            if ("d".equals(tag.optString(0, ""))) {
                return tag.optString(1, "");
            }
        }
        return "";
    }

    @NonNull
    private static Map<String, HistoryRecord> readLocalHistory(
            @NonNull final AppDatabase database) {
        final Map<String, HistoryRecord> output = new HashMap<>();
        final Map<Long, Long> progressByStreamId = new HashMap<>();
        for (final StreamStateEntity streamState : database.streamStateDAO().getAllBlocking()) {
            progressByStreamId.put(streamState.getStreamUid(), streamState.getProgressMillis());
        }

        final List<StreamHistoryEntry> historyEntries = database.streamHistoryDAO()
                .getHistorySortedByIdBlocking();
        for (final StreamHistoryEntry entry : historyEntries) {
            final StreamEntity stream = entry.getStreamEntity();
            final Long progressMillis = progressByStreamId.get(entry.getStreamId());
            final HistoryRecord record = HistoryRecord.fromEntry(
                    entry,
                    progressMillis == null ? -1 : progressMillis
            );
            output.put(composeKey(stream.getServiceId(), stream.getUrl()), record);
        }
        return output;
    }

    @NonNull
    private static Map<String, SubscriptionRecord> readLocalSubscriptions(
            @NonNull final AppDatabase database) {
        final Map<String, SubscriptionRecord> output = new HashMap<>();
        final Map<Long, String> keyBySubscriptionId = new HashMap<>();
        final List<SubscriptionEntity> subscriptions = database.subscriptionDAO()
                .getAllBlocking();
        for (final SubscriptionEntity entity : subscriptions) {
            final String url = entity.getUrl();
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            final int serviceId = entity.getServiceId();
            final SubscriptionRecord record = SubscriptionRecord.fromEntity(entity);
            final String key = composeKey(serviceId, url);
            output.put(key, record);
            keyBySubscriptionId.put(entity.getUid(), key);
        }

        final List<FeedGroupEntity> groups = database.feedGroupDAO()
                .getAllBlocking();
        for (final FeedGroupEntity group : groups) {
            final String groupName = group.getName();
            if (TextUtils.isEmpty(groupName)) {
                continue;
            }

            final int iconId = group.getIcon() == null
                    ? FeedGroupIcon.ALL.getId()
                    : group.getIcon().getId();
            final List<Long> subscriptionIds = database.feedGroupDAO()
                    .getSubscriptionIdsForBlocking(group.getUid());
            for (final Long subscriptionId : subscriptionIds) {
                if (subscriptionId == null) {
                    continue;
                }
                final String key = keyBySubscriptionId.get(subscriptionId);
                if (TextUtils.isEmpty(key)) {
                    continue;
                }
                final SubscriptionRecord record = output.get(key);
                if (record != null) {
                    record.mergeGroup(new GroupRecord(groupName, iconId));
                }
            }
        }
        return output;
    }

    @NonNull
    private static Map<String, HistoryRecord> mergeHistoryMaps(
            @NonNull final Map<String, HistoryRecord> remote,
            @NonNull final Map<String, HistoryRecord> local) {
        final Map<String, HistoryRecord> merged = new HashMap<>(remote);
        for (final Map.Entry<String, HistoryRecord> entry : local.entrySet()) {
            mergedHistoryRecord(merged, entry.getKey(), entry.getValue());
        }
        return merged;
    }

    @NonNull
    private static Map<String, SubscriptionRecord> mergeSubscriptionMaps(
            @NonNull final Map<String, SubscriptionRecord> remote,
            @NonNull final Map<String, SubscriptionRecord> local) {
        final Map<String, SubscriptionRecord> merged = new HashMap<>(remote);
        for (final Map.Entry<String, SubscriptionRecord> entry : local.entrySet()) {
            mergedSubscriptionRecord(merged, entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static void mergedHistoryRecord(@NonNull final Map<String, HistoryRecord> sink,
                                            @NonNull final String key,
                                            @NonNull final HistoryRecord incoming) {
        final HistoryRecord existing = sink.get(key);
        if (existing == null) {
            sink.put(key, incoming);
            return;
        }
        final boolean incomingIsNewer = incoming.accessTs > existing.accessTs;
        existing.repeatCount = Math.max(existing.repeatCount, incoming.repeatCount);
        if (incomingIsNewer) {
            existing.accessTs = incoming.accessTs;
            existing.title = nonEmpty(incoming.title, existing.title);
            existing.streamType = nonEmpty(incoming.streamType, existing.streamType);
            existing.duration = incoming.duration > 0 ? incoming.duration : existing.duration;
            existing.uploader = nonEmpty(incoming.uploader, existing.uploader);
            existing.uploaderUrl = nonEmpty(incoming.uploaderUrl, existing.uploaderUrl);
            existing.thumbnailUrl = nonEmpty(incoming.thumbnailUrl, existing.thumbnailUrl);
            existing.progressMillis = incoming.progressMillis >= 0
                    ? incoming.progressMillis
                    : existing.progressMillis;
        } else {
            existing.title = nonEmpty(existing.title, incoming.title);
            existing.streamType = nonEmpty(existing.streamType, incoming.streamType);
            existing.duration = existing.duration > 0 ? existing.duration : incoming.duration;
            existing.uploader = nonEmpty(existing.uploader, incoming.uploader);
            existing.uploaderUrl = nonEmpty(existing.uploaderUrl, incoming.uploaderUrl);
            existing.thumbnailUrl = nonEmpty(existing.thumbnailUrl, incoming.thumbnailUrl);
            if (incoming.accessTs == existing.accessTs) {
                existing.progressMillis = Math.max(
                        existing.progressMillis,
                        incoming.progressMillis
                );
            } else if (existing.progressMillis < 0 && incoming.progressMillis >= 0) {
                existing.progressMillis = incoming.progressMillis;
            }
        }
    }

    private static void mergedSubscriptionRecord(
            @NonNull final Map<String, SubscriptionRecord> sink,
            @NonNull final String key,
            @NonNull final SubscriptionRecord incoming) {
        final SubscriptionRecord existing = sink.get(key);
        if (existing == null) {
            sink.put(key, incoming);
            return;
        }
        existing.name = nonEmpty(existing.name, incoming.name);
        existing.avatarUrl = nonEmpty(existing.avatarUrl, incoming.avatarUrl);
        existing.description = nonEmpty(existing.description, incoming.description);
        if (existing.subscriberCount == null || existing.subscriberCount < 0) {
            existing.subscriberCount = incoming.subscriberCount;
        } else if (incoming.subscriberCount != null && incoming.subscriberCount > 0) {
            existing.subscriberCount = Math.max(existing.subscriberCount, incoming.subscriberCount);
        }
        existing.mergeGroupsFrom(incoming);
    }

    private static void applyHistoryToDatabase(@NonNull final AppDatabase database,
                                               @NonNull final Map<String, HistoryRecord> history) {
        database.runInTransaction(() -> {
            for (final HistoryRecord record : history.values()) {
                final StreamEntity streamEntity = record.toStreamEntity();
                final long streamUid = database.streamDAO().upsert(streamEntity);
                final StreamHistoryEntity latestEntry = database.streamHistoryDAO()
                        .getLatestEntry(streamUid);
                if (record.progressMillis >= 0) {
                    database.streamStateDAO().upsert(new StreamStateEntity(
                            streamUid,
                            record.progressMillis
                    ));
                }

                final OffsetDateTime accessDate = OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(record.accessTs),
                        ZoneOffset.UTC
                );
                if (latestEntry == null) {
                    database.streamHistoryDAO().insert(new StreamHistoryEntity(
                            streamUid,
                            accessDate,
                            Math.max(0, record.repeatCount)
                    ));
                    continue;
                }

                final OffsetDateTime mergedAccessDate =
                        latestEntry.getAccessDate().isAfter(accessDate)
                                ? latestEntry.getAccessDate()
                                : accessDate;
                final long mergedRepeatCount = Math.max(
                        latestEntry.getRepeatCount(),
                        record.repeatCount
                );

                if (latestEntry.getAccessDate().isEqual(mergedAccessDate)
                        && latestEntry.getRepeatCount() == mergedRepeatCount) {
                    continue;
                }

                database.streamHistoryDAO().delete(latestEntry);
                database.streamHistoryDAO().insert(new StreamHistoryEntity(
                        streamUid,
                        mergedAccessDate,
                        mergedRepeatCount
                ));
            }
        });
    }

    private static void applySubscriptionsToDatabase(@NonNull final AppDatabase database,
                                                     @NonNull final Map<String,
                                                             SubscriptionRecord> subscriptions) {
        database.runInTransaction(() -> {
            final Map<String, SubscriptionEntity> existing = new HashMap<>();
            final Map<String, Long> subscriptionIdByKey = new HashMap<>();
            final List<SubscriptionEntity> all = database.subscriptionDAO()
                    .getAllBlocking();
            for (final SubscriptionEntity entity : all) {
                if (!TextUtils.isEmpty(entity.getUrl())) {
                    final String key = composeKey(entity.getServiceId(), entity.getUrl());
                    existing.put(key, entity);
                    subscriptionIdByKey.put(key, entity.getUid());
                }
            }

            for (final Map.Entry<String, SubscriptionRecord> entry : subscriptions.entrySet()) {
                final SubscriptionRecord remote = entry.getValue();
                final SubscriptionEntity local = existing.get(entry.getKey());
                if (local == null) {
                    final SubscriptionEntity created = remote.toEntity();
                    final long insertedId = database.subscriptionDAO().insert(created);
                    if (insertedId > 0) {
                        created.setUid(insertedId);
                    }
                    existing.put(entry.getKey(), created);
                    subscriptionIdByKey.put(entry.getKey(), created.getUid());
                } else {
                    final boolean changed = remote.applyTo(local);
                    if (changed) {
                        database.subscriptionDAO().update(local);
                    }
                    subscriptionIdByKey.put(entry.getKey(), local.getUid());
                }
            }

            final Map<String, GroupRecord> mergedGroupsByName = new HashMap<>();
            final Map<String, Set<Long>> membershipByGroupName = new HashMap<>();
            for (final Map.Entry<String, SubscriptionRecord> entry : subscriptions.entrySet()) {
                final SubscriptionRecord record = entry.getValue();
                final Long subscriptionId = subscriptionIdByKey.get(entry.getKey());
                for (final GroupRecord group : record.groups.values()) {
                    if (TextUtils.isEmpty(group.name)) {
                        continue;
                    }
                    final GroupRecord existingGroup = mergedGroupsByName.get(group.name);
                    if (existingGroup == null) {
                        mergedGroupsByName.put(
                                group.name,
                                new GroupRecord(group.name, group.iconId)
                        );
                    } else if (existingGroup.iconId == FeedGroupIcon.ALL.getId()
                            && group.iconId != FeedGroupIcon.ALL.getId()) {
                        existingGroup.iconId = group.iconId;
                    }

                    if (subscriptionId != null && subscriptionId > 0) {
                        membershipByGroupName
                                .computeIfAbsent(group.name, ignored -> new HashSet<>())
                                .add(subscriptionId);
                    }
                }
            }

            if (mergedGroupsByName.isEmpty()) {
                return;
            }

            final Map<String, FeedGroupEntity> localGroupsByName = new HashMap<>();
            final List<FeedGroupEntity> localGroups = database.feedGroupDAO()
                    .getAllBlocking();
            for (final FeedGroupEntity group : localGroups) {
                if (!TextUtils.isEmpty(group.getName())) {
                    localGroupsByName.put(group.getName(), group);
                }
            }

            final Map<String, Long> groupIdsByName = new HashMap<>();
            for (final GroupRecord remoteGroup : mergedGroupsByName.values()) {
                if (TextUtils.isEmpty(remoteGroup.name)) {
                    continue;
                }

                final FeedGroupEntity localGroup = localGroupsByName.get(remoteGroup.name);
                if (localGroup == null) {
                    final FeedGroupEntity created = new FeedGroupEntity(
                            0,
                            remoteGroup.name,
                            resolveFeedGroupIcon(remoteGroup.iconId),
                            -1
                    );
                    final long groupId = database.feedGroupDAO().insert(created);
                    if (groupId > 0) {
                        groupIdsByName.put(remoteGroup.name, groupId);
                    }
                    continue;
                }

                groupIdsByName.put(remoteGroup.name, localGroup.getUid());
                final FeedGroupIcon desiredIcon = resolveFeedGroupIcon(remoteGroup.iconId);
                if (localGroup.getIcon() != desiredIcon) {
                    localGroup.setIcon(desiredIcon);
                    database.feedGroupDAO().update(localGroup);
                }
            }

            for (final Map.Entry<String, Set<Long>> entry : membershipByGroupName.entrySet()) {
                final Long groupId = groupIdsByName.get(entry.getKey());
                if (groupId == null || groupId <= 0) {
                    continue;
                }
                final List<Long> subscriptionIds = new ArrayList<>(entry.getValue());
                database.feedGroupDAO().updateSubscriptionsForGroup(groupId, subscriptionIds);
            }
        });
    }

    @NonNull
    private static JSONObject historyToJson(@NonNull final Map<String, HistoryRecord> history) {
        final List<Map.Entry<String, HistoryRecord>> entries = new ArrayList<>(history.entrySet());
        entries.sort((left, right) ->
                Long.compare(right.getValue().accessTs, left.getValue().accessTs));

        final JSONObject json = new JSONObject();
        int added = 0;
        boolean truncated = false;
        for (final Map.Entry<String, HistoryRecord> entry : entries) {
            try {
                json.put(entry.getKey(), entry.getValue().toJson());
                added++;
            } catch (final JSONException ignored) {
                // Skip malformed record.
            }

            if (added > MAX_HISTORY_RECORDS_PER_SNAPSHOT
                    || utf8Size(json.toString()) > MAX_CATEGORY_DATA_BYTES) {
                json.remove(entry.getKey());
                truncated = true;
                break;
            }
        }
        if (truncated) {
            Log.d(TAG, "Trimmed history snapshot from " + entries.size()
                    + " to " + json.length() + " entries");
        }
        return json;
    }

    @NonNull
    private static JSONObject subscriptionsToJson(
            @NonNull final Map<String, SubscriptionRecord> subscriptions) {
        final List<Map.Entry<String, SubscriptionRecord>> entries =
                new ArrayList<>(subscriptions.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        final JSONObject json = new JSONObject();
        int added = 0;
        boolean truncated = false;
        for (final Map.Entry<String, SubscriptionRecord> entry : entries) {
            try {
                json.put(entry.getKey(), entry.getValue().toJson());
                added++;
            } catch (final JSONException ignored) {
                // Skip malformed record.
            }

            if (added > MAX_SUBSCRIPTIONS_PER_SNAPSHOT
                    || utf8Size(json.toString()) > MAX_CATEGORY_DATA_BYTES) {
                json.remove(entry.getKey());
                truncated = true;
                break;
            }
        }
        if (truncated) {
            Log.d(TAG, "Trimmed subscription snapshot from " + entries.size()
                    + " to " + json.length() + " entries");
        }
        return json;
    }

    private static int utf8Size(@NonNull final String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int publishCategorySnapshot(@NonNull final Context context,
                                               @NonNull final Set<String> relays,
                                               @Nullable final String nsec,
                                               @Nullable final String signerPackage,
                                               @NonNull final String pubKeyHex,
                                               @NonNull final String category,
                                               @NonNull final String dTagValue,
                                               @NonNull final JSONObject data) {
        final JSONObject payload = new JSONObject();
        final long now = Instant.now().getEpochSecond();
        try {
            payload.put("v", 1);
            payload.put("category", category);
            payload.put("updated_at", now);
            payload.put("data", data);
        } catch (final JSONException e) {
            return 0;
        }

        final String encryptedContent;
        if (!TextUtils.isEmpty(nsec)) {
            try {
                encryptedContent = NostrKeyUtils.encryptNip44(nsec, pubKeyHex, payload.toString());
            } catch (final RuntimeException e) {
                return 0;
            }
        } else if (!TextUtils.isEmpty(signerPackage)) {
            encryptedContent = Nip55SignerClient.nip44Encrypt(
                    context,
                    signerPackage,
                    payload.toString(),
                    pubKeyHex,
                    pubKeyHex
            );
            if (TextUtils.isEmpty(encryptedContent)) {
                Log.w(TAG, "Skipping publish: signer failed NIP-44 encrypt");
                return 0;
            }
        } else {
            return 0;
        }
        final String eventContent;
        try {
            eventContent = new JSONObject()
                    .put("name", "newpipe-sync-" + category)
                    .put("data", new JSONObject()
                            .put("enc", "nip44")
                            .put("ciphertext", encryptedContent))
                    .toString();
        } catch (final JSONException e) {
            return 0;
        }

        final JSONArray tags = new JSONArray();
        tags.put(new JSONArray().put("d").put(dTagValue));
        tags.put(new JSONArray().put("p").put(pubKeyHex));
        tags.put(new JSONArray().put("client").put("newpipe-sync"));

        final JSONObject unsignedEvent = new JSONObject();
        try {
            unsignedEvent.put("pubkey", pubKeyHex);
            unsignedEvent.put("created_at", now);
            unsignedEvent.put("kind", KIND_APP_DATA);
            unsignedEvent.put("tags", tags);
            unsignedEvent.put("content", eventContent);
        } catch (final JSONException e) {
            return 0;
        }

        final String serialized = serializeEventForId(
                pubKeyHex,
                now,
                KIND_APP_DATA,
                tags,
                eventContent
        );
        final byte[] eventHash = sha256(serialized.getBytes(StandardCharsets.UTF_8));
        final String computedEventId = bytesToHex(eventHash);
        final JSONObject signedEvent;

        if (!TextUtils.isEmpty(nsec)) {
            final String signature;
            try {
                signature = NostrKeyUtils.signEventId(nsec, eventHash);
                signedEvent = new JSONObject(unsignedEvent.toString())
                        .put("id", computedEventId)
                        .put("sig", signature);
            } catch (final RuntimeException | JSONException e) {
                return 0;
            }
        } else if (!TextUtils.isEmpty(signerPackage)) {
            signedEvent = Nip55SignerClient.signEvent(
                    context,
                    signerPackage,
                    unsignedEvent,
                    pubKeyHex,
                    computedEventId
            );
            if (signedEvent == null) {
                Log.w(TAG, "Skipping publish: signer failed SIGN_EVENT");
                return 0;
            }
        } else {
            return 0;
        }

        final String eventId = nonEmpty(signedEvent.optString("id", null), computedEventId);
        final String eventMessage = new JSONArray().put("EVENT").put(signedEvent).toString();
        int acceptedRelays = 0;
        for (final String relay : relays) {
            if (publishToRelay(relay, eventMessage, eventId)) {
                acceptedRelays++;
            }
        }
        Log.d(TAG, "Published " + category + " snapshot entries=" + data.length()
                + " to " + acceptedRelays + "/" + relays.size() + " relays");
        return acceptedRelays;
    }

    private static boolean publishToRelay(@NonNull final String relayUrl,
                                          @NonNull final String eventMessage,
                                          @NonNull final String eventId) {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch okLatch = new CountDownLatch(1);
        final AtomicBoolean accepted = new AtomicBoolean(false);
        final AtomicBoolean okReceived = new AtomicBoolean(false);
        final Request request = new Request.Builder().url(relayUrl).build();
        final WebSocket webSocket = WS_CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull final WebSocket webSocket,
                               @NonNull final okhttp3.Response response) {
                openLatch.countDown();
            }

            @Override
            public void onMessage(@NonNull final WebSocket webSocket,
                                  @NonNull final String text) {
                try {
                    final JSONArray frame = new JSONArray(text);
                    if (frame.length() < 2 || !"OK".equals(frame.optString(0, ""))) {
                        return;
                    }
                    if (!eventId.equals(frame.optString(1, ""))) {
                        return;
                    }

                    okReceived.set(true);
                    accepted.set(frame.optBoolean(2, false));
                    if (!accepted.get()) {
                        Log.w(TAG, "Relay rejected event from " + relayUrl + ": "
                                + frame.optString(3, ""));
                    }
                    okLatch.countDown();
                } catch (final JSONException ignored) {
                    // Ignore malformed relay frames.
                }
            }

            @Override
            public void onFailure(@NonNull final WebSocket webSocket,
                                  @NonNull final Throwable t,
                                  @Nullable final okhttp3.Response response) {
                openLatch.countDown();
                okLatch.countDown();
            }

            @Override
            public void onClosed(@NonNull final WebSocket webSocket, final int code,
                                 @NonNull final String reason) {
                okLatch.countDown();
            }
        });

        try {
            if (!openLatch.await(RELAY_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                webSocket.cancel();
                return false;
            }

            if (!webSocket.send(eventMessage)) {
                webSocket.close(1000, "send failed");
                return false;
            }

            okLatch.await(RELAY_PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            webSocket.close(1000, "done");
            if (!okReceived.get()) {
                Log.w(TAG, "Relay did not acknowledge event: " + relayUrl);
            }
            return accepted.get();
        } catch (final Exception e) {
            webSocket.cancel();
            return false;
        }
    }

    @NonNull
    private static String serializeEventForId(@NonNull final String pubkey,
                                              final long createdAt,
                                              final int kind,
                                              @NonNull final JSONArray tags,
                                              @NonNull final String content) {
        final StringBuilder builder = new StringBuilder(content.length() + 128);
        builder.append("[0,");
        appendCanonicalJsonString(builder, pubkey);
        builder.append(',').append(createdAt).append(',').append(kind).append(',');
        appendCanonicalTags(builder, tags);
        builder.append(',');
        appendCanonicalJsonString(builder, content);
        builder.append(']');
        return builder.toString();
    }

    private static void appendCanonicalTags(@NonNull final StringBuilder sink,
                                            @NonNull final JSONArray tags) {
        sink.append('[');
        for (int i = 0; i < tags.length(); i++) {
            if (i > 0) {
                sink.append(',');
            }
            final JSONArray tag = tags.optJSONArray(i);
            if (tag == null) {
                sink.append("[]");
                continue;
            }

            sink.append('[');
            for (int j = 0; j < tag.length(); j++) {
                if (j > 0) {
                    sink.append(',');
                }
                appendCanonicalJsonString(sink, String.valueOf(tag.opt(j)));
            }
            sink.append(']');
        }
        sink.append(']');
    }

    private static void appendCanonicalJsonString(@NonNull final StringBuilder sink,
                                                  @Nullable final String value) {
        final String text = value == null ? "" : value;
        sink.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            switch (ch) {
                case '"':
                    sink.append("\\\"");
                    break;
                case '\\':
                    sink.append("\\\\");
                    break;
                case '\b':
                    sink.append("\\b");
                    break;
                case '\f':
                    sink.append("\\f");
                    break;
                case '\n':
                    sink.append("\\n");
                    break;
                case '\r':
                    sink.append("\\r");
                    break;
                case '\t':
                    sink.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sink.append(String.format(Locale.US, "\\u%04x", (int) ch));
                    } else {
                        sink.append(ch);
                    }
                    break;
            }
        }
        sink.append('"');
    }

    @NonNull
    private static byte[] sha256(@NonNull final byte[] input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @NonNull
    private static String bytesToHex(@NonNull final byte[] bytes) {
        final char[] alphabet = "0123456789abcdef".toCharArray();
        final char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            final int v = bytes[i] & 0xff;
            chars[i * 2] = alphabet[v >>> 4];
            chars[i * 2 + 1] = alphabet[v & 0x0f];
        }
        return new String(chars);
    }

    @NonNull
    private static String composeKey(final int serviceId, @NonNull final String url) {
        return serviceId + "|" + url;
    }

    @Nullable
    private static String nonEmpty(@Nullable final String preferred,
                                   @Nullable final String fallback) {
        return TextUtils.isEmpty(preferred) ? fallback : preferred;
    }

    @Nullable
    private static String trimToNull(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    private static FeedGroupIcon resolveFeedGroupIcon(final int iconId) {
        for (final FeedGroupIcon icon : FeedGroupIcon.values()) {
            if (icon.getId() == iconId) {
                return icon;
            }
        }
        return FeedGroupIcon.ALL;
    }

    private static final class HistoryRecord {
        final int serviceId;
        final String url;
        String title;
        String streamType;
        long duration;
        String uploader;
        String uploaderUrl;
        String thumbnailUrl;
        long progressMillis;
        long accessTs;
        long repeatCount;

        HistoryRecord(final int serviceId,
                      @NonNull final String url,
                      @Nullable final String title,
                      @Nullable final String streamType,
                      final long duration,
                      @Nullable final String uploader,
                      @Nullable final String uploaderUrl,
                      @Nullable final String thumbnailUrl,
                      final long progressMillis,
                      final long accessTs,
                      final long repeatCount) {
            this.serviceId = serviceId;
            this.url = url;
            this.title = title;
            this.streamType = streamType;
            this.duration = duration;
            this.uploader = uploader;
            this.uploaderUrl = uploaderUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.progressMillis = progressMillis;
            this.accessTs = accessTs;
            this.repeatCount = repeatCount;
        }

        @Nullable
        static HistoryRecord fromJson(@NonNull final JSONObject json) {
            final int serviceId = json.optInt("service_id", Integer.MIN_VALUE);
            final String url = json.optString("url", null);
            if (serviceId == Integer.MIN_VALUE || TextUtils.isEmpty(url)) {
                return null;
            }
            return new HistoryRecord(
                    serviceId,
                    url,
                    json.optString("title", null),
                    json.optString("stream_type", StreamType.VIDEO_STREAM.name()),
                    json.optLong("duration", -1),
                    json.optString("uploader", null),
                    json.optString("uploader_url", null),
                    json.optString("thumbnail_url", null),
                    Math.max(-1, json.optLong("progress_millis", -1)),
                    json.optLong("access_ts", 0),
                    Math.max(0, json.optLong("repeat_count", 0))
            );
        }

        @NonNull
        static HistoryRecord fromEntry(@NonNull final StreamHistoryEntry entry,
                                       final long progressMillis) {
            final StreamEntity stream = entry.getStreamEntity();
            final long accessTs = entry.getAccessDate().toEpochSecond();
            return new HistoryRecord(
                    stream.getServiceId(),
                    stream.getUrl(),
                    stream.getTitle(),
                    stream.getStreamType().name(),
                    stream.getDuration(),
                    stream.getUploader(),
                    stream.getUploaderUrl(),
                    stream.getThumbnailUrl(),
                    Math.max(-1, progressMillis),
                    accessTs,
                    entry.getRepeatCount()
            );
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("service_id", serviceId)
                    .put("url", url)
                    .put("title", title)
                    .put("stream_type", streamType)
                    .put("duration", duration)
                    .put("uploader", uploader)
                    .put("uploader_url", uploaderUrl)
                    .put("thumbnail_url", thumbnailUrl)
                    .put("progress_millis", progressMillis)
                    .put("access_ts", accessTs)
                    .put("repeat_count", repeatCount);
        }

        @NonNull
        StreamEntity toStreamEntity() {
            final StreamType parsedType;
            try {
                parsedType = StreamType.valueOf(
                        TextUtils.isEmpty(streamType)
                                ? StreamType.VIDEO_STREAM.name()
                                : streamType.toUpperCase(Locale.US)
                );
            } catch (final IllegalArgumentException e) {
                return new StreamEntity(
                        0,
                        serviceId,
                        url,
                        nonEmpty(title, url),
                        StreamType.VIDEO_STREAM,
                        duration,
                        nonEmpty(uploader, ""),
                        uploaderUrl,
                        thumbnailUrl,
                        null,
                        null,
                        null,
                        null
                );
            }

            return new StreamEntity(
                    0,
                    serviceId,
                    url,
                    nonEmpty(title, url),
                    parsedType,
                    duration,
                    nonEmpty(uploader, ""),
                    uploaderUrl,
                    thumbnailUrl,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private static final class SubscriptionRecord {
        final int serviceId;
        final String url;
        String name;
        String avatarUrl;
        Long subscriberCount;
        String description;
        final Map<String, GroupRecord> groups = new HashMap<>();

        SubscriptionRecord(final int serviceId,
                           @NonNull final String url,
                           @Nullable final String name,
                           @Nullable final String avatarUrl,
                           @Nullable final Long subscriberCount,
                           @Nullable final String description) {
            this.serviceId = serviceId;
            this.url = url;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.subscriberCount = subscriberCount;
            this.description = description;
        }

        @Nullable
        static SubscriptionRecord fromJson(@NonNull final JSONObject json) {
            final int serviceId = json.optInt("service_id", Integer.MIN_VALUE);
            final String url = json.optString("url", null);
            if (serviceId == Integer.MIN_VALUE || TextUtils.isEmpty(url)) {
                return null;
            }
            return new SubscriptionRecord(
                    serviceId,
                    url,
                    json.optString("name", null),
                    json.optString("avatar_url", null),
                    json.has("subscriber_count")
                            ? json.optLong("subscriber_count")
                            : null,
                    json.optString("description", null)
            ).applyGroups(json.optJSONArray("groups"));
        }

        @NonNull
        static SubscriptionRecord fromEntity(@NonNull final SubscriptionEntity entity) {
            return new SubscriptionRecord(
                    entity.getServiceId(),
                    entity.getUrl(),
                    entity.getName(),
                    entity.getAvatarUrl(),
                    entity.getSubscriberCount(),
                    entity.getDescription()
            );
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            final JSONObject json = new JSONObject()
                    .put("service_id", serviceId)
                    .put("url", url)
                    .put("name", name)
                    .put("avatar_url", avatarUrl)
                    .put("description", description);
            if (subscriberCount != null) {
                json.put("subscriber_count", subscriberCount);
            }
            if (!groups.isEmpty()) {
                final List<GroupRecord> sortedGroups = new ArrayList<>(groups.values());
                sortedGroups.sort((left, right) ->
                        left.name.compareToIgnoreCase(right.name));
                final JSONArray groupsArray = new JSONArray();
                for (final GroupRecord group : sortedGroups) {
                    groupsArray.put(group.toJson());
                }
                json.put("groups", groupsArray);
            }
            return json;
        }

        @NonNull
        SubscriptionEntity toEntity() {
            return new SubscriptionEntity(
                    0,
                    serviceId,
                    url,
                    name,
                    avatarUrl,
                    subscriberCount,
                    description,
                    NotificationMode.DISABLED
            );
        }

        boolean applyTo(@NonNull final SubscriptionEntity target) {
            boolean changed = false;
            if (!TextUtils.equals(target.getName(), name) && !TextUtils.isEmpty(name)) {
                target.setName(name);
                changed = true;
            }
            if (!TextUtils.equals(target.getAvatarUrl(), avatarUrl)
                    && !TextUtils.isEmpty(avatarUrl)) {
                target.setAvatarUrl(avatarUrl);
                changed = true;
            }
            if (!TextUtils.equals(target.getDescription(), description)
                    && !TextUtils.isEmpty(description)) {
                target.setDescription(description);
                changed = true;
            }
            if (subscriberCount != null
                    && (target.getSubscriberCount() == null
                    || target.getSubscriberCount() < subscriberCount)) {
                target.setSubscriberCount(subscriberCount);
                changed = true;
            }
            return changed;
        }

        @NonNull
        private SubscriptionRecord applyGroups(@Nullable final JSONArray groupsArray) {
            if (groupsArray == null) {
                return this;
            }
            for (int i = 0; i < groupsArray.length(); i++) {
                final JSONObject groupJson = groupsArray.optJSONObject(i);
                if (groupJson != null) {
                    mergeGroup(GroupRecord.fromJson(groupJson));
                } else {
                    final String groupName = groupsArray.optString(i, null);
                    if (!TextUtils.isEmpty(groupName)) {
                        mergeGroup(new GroupRecord(groupName, FeedGroupIcon.ALL.getId()));
                    }
                }
            }
            return this;
        }

        void mergeGroupsFrom(@NonNull final SubscriptionRecord incoming) {
            for (final GroupRecord group : incoming.groups.values()) {
                mergeGroup(group);
            }
        }

        void mergeGroup(@Nullable final GroupRecord group) {
            if (group == null || TextUtils.isEmpty(group.name)) {
                return;
            }

            final GroupRecord existing = groups.get(group.name);
            if (existing == null) {
                groups.put(group.name, new GroupRecord(group.name, group.iconId));
                return;
            }
            if (existing.iconId == FeedGroupIcon.ALL.getId()
                    && group.iconId != FeedGroupIcon.ALL.getId()) {
                existing.iconId = group.iconId;
            }
        }
    }

    private static final class GroupRecord {
        final String name;
        int iconId;

        GroupRecord(@NonNull final String name, final int iconId) {
            this.name = name;
            this.iconId = iconId;
        }

        @NonNull
        static GroupRecord fromJson(@NonNull final JSONObject json) {
            final String name = json.optString("name", "");
            final int iconId = json.optInt("icon_id", FeedGroupIcon.ALL.getId());
            return new GroupRecord(name, iconId);
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("name", name)
                    .put("icon_id", iconId);
        }
    }

    public static final class RelayStatus {
        public final int connectedRelays;
        public final int totalRelays;

        RelayStatus(final int connectedRelays, final int totalRelays) {
            this.connectedRelays = connectedRelays;
            this.totalRelays = totalRelays;
        }
    }
}
