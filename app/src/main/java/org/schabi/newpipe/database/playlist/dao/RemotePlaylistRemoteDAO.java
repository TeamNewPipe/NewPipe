package org.schabi.newpipe.database.playlist.dao;

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
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemotePlaylistRemoteDAO extends PlaylistRemoteDAO {

    private static final String ENDPOINT = "/remoteplaylists";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemotePlaylistRemoteDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils", e);
        }
    }

    @Override
    public long insert(PlaylistRemoteEntity playlistRemoteEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(playlistRemoteEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        playlistRemoteEntity.setUid(id);
        return roomDb.playlistRemoteDAO().insert(playlistRemoteEntity);
    }

    @Override
    public List<Long> insertAll(PlaylistRemoteEntity... playlistRemoteEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistRemoteEntity entity : playlistRemoteEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<PlaylistRemoteEntity> playlistRemoteEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistRemoteEntity entity : playlistRemoteEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<PlaylistRemoteEntity>> getAll() {
        return roomDb.playlistRemoteDAO().getAll();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(PlaylistRemoteEntity playlistRemoteEntity) {
        client.put(ENDPOINT + "/" + playlistRemoteEntity.getUid(), toJson(playlistRemoteEntity));
        return roomDb.playlistRemoteDAO().update(playlistRemoteEntity);
    }

    @Override
    public void update(Collection<PlaylistRemoteEntity> playlistRemoteEntities) {
        for (PlaylistRemoteEntity entity : playlistRemoteEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<PlaylistRemoteEntity>> listByService(int serviceId) {
        return roomDb.playlistRemoteDAO().listByService(serviceId);
    }

    @Override
    public Flowable<List<PlaylistRemoteEntity>> getPlaylist(long serviceId, String url) {
        return roomDb.playlistRemoteDAO().getPlaylist(serviceId, url);
    }

    @Override
    Long getPlaylistIdInternal(long serviceId, String url) {
        return roomDb.playlistRemoteDAO().getPlaylistIdInternal(serviceId, url);
    }

    @Override
    public int deletePlaylist(long playlistId) {
        client.delete(ENDPOINT + "/" + playlistId);
        return roomDb.playlistRemoteDAO().deletePlaylist(playlistId);
    }

    @Override
    public void delete(PlaylistRemoteEntity playlistRemoteEntity) {
        client.delete(ENDPOINT + "/" + playlistRemoteEntity.getUid());
        roomDb.playlistRemoteDAO().delete(playlistRemoteEntity);
    }

    @Override
    public int delete(Collection<PlaylistRemoteEntity> playlistRemoteEntities) {
        for (PlaylistRemoteEntity entity : playlistRemoteEntities) {
            delete(entity);
        }
        return 0;
    }

    public List<PlaylistRemoteEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<PlaylistRemoteEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private PlaylistRemoteEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<PlaylistRemoteEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<PlaylistRemoteEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(PlaylistRemoteEntity entity) {
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("serviceId", eU.encrypt(entity.getServiceId()));
            json.value("name", eU.encrypt(entity.getName()));
            json.value("url", eU.encrypt(entity.getUrl()));
            json.value("thumbnailUrl", eU.encrypt(entity.getThumbnailUrl()));
            json.value("uploader", eU.encrypt(entity.getUploader()));
            json.value("streamCount", eU.encrypt(entity.getStreamCount()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private PlaylistRemoteEntity fromJson(JsonObject object) {
        try {
            int serviceId = Long.valueOf(eU.decryptAsLong(object.getString("serviceId"))).intValue();
            String name = eU.decrypt(object.getString("name"));
            String url = eU.decrypt(object.getString("url"));
            String thumbnailUrl = eU.decrypt(object.getString("thumbnailUrl"));
            String uploader = eU.decrypt(object.getString("uploader"));
            Long streamCount = eU.decryptAsLong(object.getString("streamCount"));
            int uid = object.getInt("uid");
            PlaylistRemoteEntity entity = new PlaylistRemoteEntity(serviceId, name
                    , url, thumbnailUrl, uploader, streamCount);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        }
    }
}
