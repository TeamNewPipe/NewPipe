package org.schabi.newpipe.database.playlist.dao;

import android.content.Context;
import android.support.annotation.NonNull;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.RemoteDatabaseClient;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemotePlaylistStreamDAO extends PlaylistStreamDAO {

    private static final String ENDPOINT = "/playliststreamjoin";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;

    public RemotePlaylistStreamDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
    }

    @Override
    public long insert(PlaylistStreamEntity playlistStreamEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(playlistStreamEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        playlistStreamEntity.setUid(id);
        return roomDb.playlistStreamDAO().insert(playlistStreamEntity);
    }

    @Override
    public List<Long> insertAll(PlaylistStreamEntity... playlistStreamEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistStreamEntity entity : playlistStreamEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<PlaylistStreamEntity> playlistStreamEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistStreamEntity entity : playlistStreamEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<PlaylistStreamEntity>> getAll() {
        return roomDb.playlistStreamDAO().getAll();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(PlaylistStreamEntity playlistStreamEntity) {
        client.put(ENDPOINT + "/" + playlistStreamEntity.getUid(), toJson(playlistStreamEntity));
        return roomDb.playlistStreamDAO().update(playlistStreamEntity);
    }

    @Override
    public void update(Collection<PlaylistStreamEntity> playlistStreamEntities) {
        for (PlaylistStreamEntity entity : playlistStreamEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<PlaylistStreamEntity>> listByService(int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBatch(long playlistId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Flowable<Integer> getMaximumIndexOf(long playlistId) {
        return roomDb.playlistStreamDAO().getMaximumIndexOf(playlistId);
    }

    @Override
    public Flowable<List<PlaylistStreamEntry>> getOrderedStreamsOf(long playlistId) {
        return roomDb.playlistStreamDAO().getOrderedStreamsOf(playlistId);
    }

    @Override
    public Flowable<List<PlaylistMetadataEntry>> getPlaylistMetadata() {
        return roomDb.playlistStreamDAO().getPlaylistMetadata();
    }

    @Override
    public void delete(PlaylistStreamEntity playlistStreamEntity) {
        client.delete(ENDPOINT + "/" + playlistStreamEntity.getUid());
        roomDb.playlistStreamDAO().delete(playlistStreamEntity);
    }

    @Override
    public int delete(Collection<PlaylistStreamEntity> playlistStreamEntities) {
        for (PlaylistStreamEntity entity : playlistStreamEntities) {
            delete(entity);
        }
        return 0;
    }

    public List<PlaylistStreamEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<PlaylistStreamEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private PlaylistStreamEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<PlaylistStreamEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<PlaylistStreamEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(PlaylistStreamEntity entity) {
        JsonBuilder json = JsonObject.builder();
        json.value("playlistId", entity.getPlaylistUid());
        json.value("streamId", entity.getStreamUid());
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private PlaylistStreamEntity fromJson(JsonObject object) {
        int playlistId = object.getInt("playlistId");
        int streamId = object.getInt("streamId");
        int joinIndex = object.getInt("joinIndex");
        int uid = object.getInt("uid");
        PlaylistStreamEntity entity = new PlaylistStreamEntity(playlistId, streamId, joinIndex);
        entity.setUid(uid);
        return entity;
    }
}
