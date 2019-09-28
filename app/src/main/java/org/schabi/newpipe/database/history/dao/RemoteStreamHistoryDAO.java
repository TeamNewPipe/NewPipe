package org.schabi.newpipe.database.history.dao;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.auth.EncryptionUtils;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.RemoteDatabaseClient;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemoteStreamHistoryDAO extends StreamHistoryDAO {

    private static final String ENDPOINT = "/streamhistory";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemoteStreamHistoryDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils", e);
        }
    }

    @Override
    public long insert(StreamHistoryEntity streamHistoryEntity) {

        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(streamHistoryEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        streamHistoryEntity.setUid(id);
        return roomDb.streamHistoryDAO().insert(streamHistoryEntity);
    }

    @Override
    public List<Long> insertAll(StreamHistoryEntity... streamHistoryEntities) {
        List<Long> result = new ArrayList<>();
        for (StreamHistoryEntity entity : streamHistoryEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<StreamHistoryEntity> streamHistoryEntities) {
        List<Long> result = new ArrayList<>();
        for (StreamHistoryEntity entity : streamHistoryEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<StreamHistoryEntity>> getAll() {
        return roomDb.streamHistoryDAO().getAll();
    }

    @Nullable
    @Override
    public StreamHistoryEntity getLatestEntry() {
        return roomDb.streamHistoryDAO().getLatestEntry();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int update(StreamHistoryEntity streamHistoryEntity) {
        client.put(ENDPOINT + "/" + streamHistoryEntity.getUid(), toJson(streamHistoryEntity));
        return roomDb.streamHistoryDAO().update(streamHistoryEntity);
    }

    @Override
    public void update(Collection<StreamHistoryEntity> streamHistoryEntities) {
        for (StreamHistoryEntity entity : streamHistoryEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<StreamHistoryEntity>> listByService(int serviceId) {
        return roomDb.streamHistoryDAO().listByService(serviceId);
    }

    @Override
    public Flowable<List<StreamHistoryEntry>> getHistory() {
        return roomDb.streamHistoryDAO().getHistory();
    }

    @Nullable
    @Override
    public StreamHistoryEntity getLatestEntry(long streamId) {
        return roomDb.streamHistoryDAO().getLatestEntry(streamId);
    }

    @Override
    public int deleteStreamHistory(long streamId) {
        return 0;
    }

    @Override
    public Flowable<List<StreamStatisticsEntry>> getStatistics() {
        return roomDb.streamHistoryDAO().getStatistics();
    }

    @Override
    public void delete(StreamHistoryEntity streamHistoryEntity) {
        client.delete(ENDPOINT + "/" + streamHistoryEntity.getUid());
        roomDb.streamHistoryDAO().delete(streamHistoryEntity);
    }

    @Override
    public int delete(Collection<StreamHistoryEntity> streamHistoryEntities) {
        for (StreamHistoryEntity entity : streamHistoryEntities) {
            delete(entity);
        }
        return 0;
    }

    public List<StreamHistoryEntity> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<StreamHistoryEntity> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private StreamHistoryEntity parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<StreamHistoryEntity> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<StreamHistoryEntity> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(StreamHistoryEntity entity) {
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("streamId", entity.getStreamUid());
            String accessDate = new SimpleDateFormat(DATE_FORMAT, Locale.US).format(entity.getAccessDate());
            json.value("accessDate", eU.encrypt(accessDate));
            json.value("repeatCount", eU.encrypt(entity.getRepeatCount()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private StreamHistoryEntity fromJson(JsonObject object) {
        try {
            int streamId = object.getInt("streamId");
            DateFormat df = new SimpleDateFormat(DATE_FORMAT, Locale.US);
            Date accessDate = df.parse(eU.decrypt(object.getString("accessDate")));
            long repeatCount = eU.decryptAsLong(object.getString("repeatCount"));
            int uid = object.getInt("uid");
            StreamHistoryEntity entity = new StreamHistoryEntity(streamId, accessDate, repeatCount);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        } catch (ParseException e){
            throw new RuntimeException("error parsing date", e);
        }
    }
}
