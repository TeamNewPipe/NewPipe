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
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemotePlaylistDAO extends PlaylistDAO {

    private static final String ENDPOINT = "/playlists";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemotePlaylistDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils", e);
        }
    }

    @Override
    public long insert(PlaylistEntity playlistEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(playlistEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        playlistEntity.setUid(id);
        return roomDb.playlistDAO().insert(playlistEntity);

    }

    @Override
    public List<Long> insertAll(PlaylistEntity... playlistEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistEntity entity : playlistEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<PlaylistEntity> playlistEntities) {
        List<Long> result = new ArrayList<>();
        for (PlaylistEntity entity : playlistEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<PlaylistEntity>> getAll() {
        return roomDb.playlistDAO().getAll();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(PlaylistEntity playlistEntity) {
        client.put(ENDPOINT + "/" + playlistEntity.getUid(), toJson(playlistEntity));
        return roomDb.playlistDAO().update(playlistEntity);
    }

    @Override
    public void update(Collection<PlaylistEntity> playlistEntities) {
        for (PlaylistEntity entity : playlistEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<PlaylistEntity>> listByService(int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(PlaylistEntity playlistEntity) {
        client.delete(ENDPOINT + "/" + playlistEntity.getUid());
        roomDb.playlistDAO().delete(playlistEntity);
    }

    @Override
    public int delete(Collection<PlaylistEntity> playlistEntities) {
        for (PlaylistEntity entity : playlistEntities) {
            delete(entity);
        }
        return 0;
    }

    @Override
    public Flowable<List<PlaylistEntity>> getPlaylist(final long playlistId) {

        return roomDb.playlistDAO().getPlaylist(playlistId);
    }

    @Override
    public int deletePlaylist(final long playlistId) {
        client.delete(ENDPOINT + "/" + playlistId);
        return roomDb.playlistDAO().deletePlaylist(playlistId);
    }

    public List<PlaylistEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<PlaylistEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private PlaylistEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<PlaylistEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<PlaylistEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(PlaylistEntity entity) {
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("name", eU.encrypt(entity.getName()));
            json.value("thumbnailUrl", eU.encrypt(entity.getThumbnailUrl()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private PlaylistEntity fromJson(JsonObject object) {
        try {
            String name = eU.decrypt(object.getString("name"));
            String thumbnailUrl = eU.decrypt(object.getString("thumbnailUrl"));
            int uid = object.getInt("uid");
            PlaylistEntity entity = new PlaylistEntity(name, thumbnailUrl);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        }
    }
}
