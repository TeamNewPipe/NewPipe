package org.schabi.newpipe.database.stream.dao;

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
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemoteStreamDAO extends StreamDAO {

    private static final String ENDPOINT = "/streams";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemoteStreamDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException| IOException e) {
            throw new IllegalStateException("unable to get encryption utils",e);
        }
    }

    @Override
    public long insert(StreamEntity streamEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(streamEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        streamEntity.setUid(id);
        return roomDb.streamDAO().insert(streamEntity);
    }

    @Override
    public List<Long> insertAll(StreamEntity... streamEntities) {
        List<Long> result = new ArrayList<>();
        for(StreamEntity entity: streamEntities){
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<StreamEntity> streamEntities) {
        List<Long> result = new ArrayList<>();
        for(StreamEntity entity: streamEntities){
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<StreamEntity>> getAll() {
        return roomDb.streamDAO().getAll();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(StreamEntity streamEntity) {
        client.put(ENDPOINT + "/" + streamEntity.getUid(), toJson(streamEntity));
        return roomDb.streamDAO().update(streamEntity);
    }

    @Override
    public void update(Collection<StreamEntity> streamEntities) {
        for(StreamEntity entity: streamEntities){
            update(entity);
        }
    }

    @Override
    public Flowable<List<StreamEntity>> listByService(int serviceId) {
        return roomDb.streamDAO().listByService(serviceId);
    }

    @Override
    public Flowable<List<StreamEntity>> getStream(long serviceId, String url) {
        return roomDb.streamDAO().getStream(serviceId, url);
    }

    @Override
    void silentInsertAllInternal(List<StreamEntity> streams) {
        for(StreamEntity entity: streams){
            Long id = getStreamIdInternal(entity.getServiceId(), entity.getUrl());
            if(id == null){
                insert(entity);
            }
        }
    }

    @Override
    Long getStreamIdInternal(long serviceId, String url) {
        return roomDb.streamDAO().getStreamIdInternal(serviceId, url);
    }

    @Override
    public int deleteOrphans() {
        return 0;
    }

    @Override
    public void delete(StreamEntity streamEntity) {
        client.delete(ENDPOINT + "/" + streamEntity.getUid());
        roomDb.streamDAO().delete(streamEntity);
    }

    @Override
    public int delete(Collection<StreamEntity> streamEntities) {
        for(StreamEntity entity: streamEntities){
            delete(entity);
        }
        return 0;
    }

    public List<StreamEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<StreamEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private StreamEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<StreamEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<StreamEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(StreamEntity entity){
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("serviceId", eU.encrypt(entity.getServiceId()));
            json.value("title", eU.encrypt(entity.getTitle()));
            json.value("url", eU.encrypt(entity.getUrl()));
            json.value("streamType", eU.encrypt(entity.getStreamType().name()));
            json.value("thumbnailUrl", eU.encrypt(entity.getThumbnailUrl()));
            json.value("uploader", eU.encrypt(entity.getUploader()));
            json.value("duration", eU.encrypt(entity.getDuration()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private StreamEntity fromJson(JsonObject object){
        try {
            int serviceId = Long.valueOf(eU.decryptAsLong(object.getString("serviceId"))).intValue();
            String title = eU.decrypt(object.getString("title"));
            String url = eU.decrypt(object.getString("url"));
            String thumbnailUrl = eU.decrypt(object.getString("thumbnailUrl"));
            StreamType streamType = StreamType.valueOf(eU.decrypt(object.getString("streamType")));
            String uploader = eU.decrypt(object.getString("uploader"));
            Long duration = eU.decryptAsLong(object.getString("duration"));
            int uid = object.getInt("uid");
            StreamEntity entity = new StreamEntity(serviceId, title, url, streamType, thumbnailUrl, uploader, duration);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        }
    }
}
