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
import org.schabi.newpipe.database.stream.model.StreamStateEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemoteStreamStateDAO extends StreamStateDAO {

    private static final String ENDPOINT = "/streamstates";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemoteStreamStateDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils",e);
        }
    }

    @Override
    public long insert(StreamStateEntity streamStateEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(streamStateEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        streamStateEntity.setUid(id);
        return roomDb.streamStateDAO().insert(streamStateEntity);
    }

    @Override
    public List<Long> insertAll(StreamStateEntity... streamStateEntities) {
        List<Long> result = new ArrayList<>();
        for(StreamStateEntity entity: streamStateEntities){
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<StreamStateEntity> streamStateEntities) {
        List<Long> result = new ArrayList<>();
        for(StreamStateEntity entity: streamStateEntities){
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<StreamStateEntity>> getAll() {
        return roomDb.streamStateDAO().getAll();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(StreamStateEntity streamStateEntity) {
        client.put(ENDPOINT + "/" + streamStateEntity.getUid(), toJson(streamStateEntity));
        return roomDb.streamStateDAO().update(streamStateEntity);
    }

    @Override
    public void update(Collection<StreamStateEntity> streamStateEntities) {
        for(StreamStateEntity entity: streamStateEntities){
            update(entity);
        }
    }

    @Override
    public Flowable<List<StreamStateEntity>> listByService(int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Flowable<List<StreamStateEntity>> getState(long streamId) {
        return roomDb.streamStateDAO().getState(streamId);
    }

    @Override
    public int deleteState(long streamId) {
        client.delete(ENDPOINT + "/" + streamId);
        return roomDb.streamStateDAO().deleteState(streamId);
    }

    @Override
    void silentInsertInternal(StreamStateEntity streamState) {

        List<StreamStateEntity> state = getState(streamState.getStreamUid()).blockingFirst();
        if(state.isEmpty()){
            streamState.setUid(insert(streamState));
        }else{
            streamState.setUid(state.get(0).getUid());
        }
    }

    @Override
    public void delete(StreamStateEntity streamStateEntity) {
        deleteState(streamStateEntity.getUid());
    }

    @Override
    public int delete(Collection<StreamStateEntity> streamStateEntities) {
        for(StreamStateEntity entity: streamStateEntities){
            delete(entity);
        }
        return 0;
    }

    public List<StreamStateEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<StreamStateEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private StreamStateEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<StreamStateEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<StreamStateEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(StreamStateEntity entity){
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("streamId", entity.getStreamUid());
            json.value("progressTime", eU.encrypt(entity.getProgressTime()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private StreamStateEntity fromJson(JsonObject object){
        try {
            long streamUid = object.getInt("streamId");
            long progressTime = eU.decryptAsLong(object.getString("progressTime"));
            long uid = object.getInt("uid");
            StreamStateEntity entity = new StreamStateEntity(streamUid, progressTime);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        }
    }
}
