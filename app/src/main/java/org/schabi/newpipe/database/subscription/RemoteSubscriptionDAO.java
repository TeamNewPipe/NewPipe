package org.schabi.newpipe.database.subscription;

import android.content.Context;
import android.support.annotation.NonNull;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.auth.EncryptionUtils;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.RemoteDatabaseClient;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemoteSubscriptionDAO extends SubscriptionDAO {

    private static final String ENDPOINT = "/subscriptions";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemoteSubscriptionDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils", e);
        }
    }

    @Override
    public long insert(SubscriptionEntity subscriptionEntity) {
        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(subscriptionEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        subscriptionEntity.setUid(id);
        return roomDb.subscriptionDAO().insert(subscriptionEntity);
    }

    @Override
    public List<Long> insertAll(SubscriptionEntity... subscriptionEntities) {
        List<Long> result = new ArrayList<>();
        for (SubscriptionEntity entity : subscriptionEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<SubscriptionEntity> subscriptionEntities) {
        List<Long> result = new ArrayList<>();
        for (SubscriptionEntity entity : subscriptionEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<SubscriptionEntity>> getAll() {
        return roomDb.subscriptionDAO().getAll();
    }

    public List<SubscriptionEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(SubscriptionEntity subscriptionEntity) {
        client.put(ENDPOINT + "/" + subscriptionEntity.getUid(), toJson(subscriptionEntity));
        return roomDb.subscriptionDAO().update(subscriptionEntity);
    }

    @Override
    public void update(Collection<SubscriptionEntity> subscriptionEntities) {
        for (SubscriptionEntity entity : subscriptionEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<SubscriptionEntity>> listByService(int serviceId) {
        return roomDb.subscriptionDAO().listByService(serviceId);
    }

    @Override
    public Flowable<List<SubscriptionEntity>> getSubscription(int serviceId, String url) {
        return roomDb.subscriptionDAO().getSubscription(serviceId, url);
    }

    @Override
    Long getSubscriptionIdInternal(int serviceId, String url) {
        return roomDb.subscriptionDAO().getSubscriptionIdInternal(serviceId, url);
    }

    @Override
    Long insertInternal(SubscriptionEntity entities) {
        Long uid = getSubscriptionIdInternal(entities.getServiceId(), entities.getUrl());
        if (null == uid) {
            return insert(entities);
        }
        return -1L;
    }

    @Override
    public void delete(SubscriptionEntity subscriptionEntity) {
        client.delete(ENDPOINT + "/" + subscriptionEntity.getUid());
        roomDb.subscriptionDAO().delete(subscriptionEntity);
    }

    @Override
    public int delete(Collection<SubscriptionEntity> subscriptionEntities) {
        for (SubscriptionEntity entity : subscriptionEntities) {
            delete(entity);
        }
        return 0;
    }

    @Override
    public void destroyAndRefill(Collection<SubscriptionEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private SubscriptionEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<SubscriptionEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<SubscriptionEntity> subscriptions = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                subscriptions.add(fromJson(entity));
            }
        }
        return subscriptions;
    }

    private String toJson(SubscriptionEntity entity) {
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("serviceId", eU.encrypt(entity.getServiceId()));
            json.value("name", eU.encrypt(entity.getName()));
            json.value("url", eU.encrypt(entity.getUrl()));
            json.value("avatarUrl", eU.encrypt(entity.getAvatarUrl()));
            json.value("description", eU.encrypt(entity.getDescription()));
            json.value("subscriberCount", eU.encrypt(entity.getSubscriberCount()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private SubscriptionEntity fromJson(JsonObject object) {
        try {
            int serviceId = Long.valueOf(eU.decryptAsLong(object.getString("serviceId"))).intValue();
            String name = eU.decrypt(object.getString("name"));
            String url = eU.decrypt(object.getString("url"));
            String avatarUrl = eU.decrypt(object.getString("avatarUrl"));
            String description = eU.decrypt(object.getString("description"));
            Long subscriberCount = eU.decryptAsLong(object.getString("subscriberCount"));
            int uid = object.getInt("uid");
            SubscriptionEntity entity = new SubscriptionEntity();
            entity.setData(name, avatarUrl, description, subscriberCount);
            entity.setServiceId(serviceId);
            entity.setUrl(url);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        }
    }
}
