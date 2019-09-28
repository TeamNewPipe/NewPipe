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
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;

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

public class RemoteSearchHistoryDAO extends SearchHistoryDAO {

    private static final String ENDPOINT = "/searchhistory";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final RemoteDatabaseClient client;
    private final AppDatabase roomDb;
    private final EncryptionUtils eU;

    public RemoteSearchHistoryDAO(AppDatabase roomDb, Context context) {
        this.roomDb = roomDb;
        this.client = RemoteDatabaseClient.getInstance(context);
        try {
            this.eU = EncryptionUtils.getInstance(context);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("unable to get encryption utils", e);
        }
    }

    @Override
    public long insert(SearchHistoryEntry searchHistoryEntity) {
        long id = Single.fromCallable(() -> client.post(ENDPOINT, toJson(searchHistoryEntity)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap(s -> Single.just(parseObject(s))).blockingGet().getUid();
        searchHistoryEntity.setUid(id);
        return roomDb.searchHistoryDAO().insert(searchHistoryEntity);
    }

    @Override
    public List<Long> insertAll(SearchHistoryEntry... searchHistoryEntities) {
        List<Long> result = new ArrayList<>();
        for (SearchHistoryEntry entity : searchHistoryEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public List<Long> insertAll(Collection<SearchHistoryEntry> searchHistoryEntities) {
        List<Long> result = new ArrayList<>();
        for (SearchHistoryEntry entity : searchHistoryEntities) {
            result.add(insert(entity));
        }
        return result;
    }

    @Override
    public Flowable<List<SearchHistoryEntry>> getAll() {
        return roomDb.searchHistoryDAO().getAll();
    }

    @Override
    public Flowable<List<SearchHistoryEntry>> getUniqueEntries(int limit) {
        return roomDb.searchHistoryDAO().getUniqueEntries(limit);
    }

    @Nullable
    @Override
    public SearchHistoryEntry getLatestEntry() {
        return roomDb.searchHistoryDAO().getLatestEntry();
    }

    @Override
    public int deleteAll() {
        getAll().blockingForEach(t -> delete(t));
        return 0;
    }

    @Override
    public int deleteAllWhereQuery(String query) {
        return 0;
    }

    @Override
    public int update(SearchHistoryEntry searchHistoryEntity) {
        client.put(ENDPOINT + "/" + searchHistoryEntity.getUid(), toJson(searchHistoryEntity));
        return roomDb.searchHistoryDAO().update(searchHistoryEntity);
    }

    @Override
    public void update(Collection<SearchHistoryEntry> searchHistoryEntities) {
        for (SearchHistoryEntry entity : searchHistoryEntities) {
            update(entity);
        }
    }

    @Override
    public Flowable<List<SearchHistoryEntry>> listByService(int serviceId) {
        return roomDb.searchHistoryDAO().listByService(serviceId);
    }

    @Override
    public Flowable<List<SearchHistoryEntry>> getSimilarEntries(String query, int limit) {
        return roomDb.searchHistoryDAO().getSimilarEntries(query, limit);
    }

    @Override
    public void delete(SearchHistoryEntry searchHistoryEntity) {
        client.delete(ENDPOINT + "/" + searchHistoryEntity.getUid());
        roomDb.searchHistoryDAO().delete(searchHistoryEntity);
    }

    @Override
    public int delete(Collection<SearchHistoryEntry> searchHistoryEntities) {
        for (SearchHistoryEntry entity : searchHistoryEntities) {
            delete(entity);
        }
        return 0;
    }

    public List<SearchHistoryEntry> fetchAll() throws JsonParserException {
        String response = client.get(ENDPOINT);
        return parseList(response);
    }

    @Override
    public void destroyAndRefill(Collection<SearchHistoryEntry> entities) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    private SearchHistoryEntry parseObject(String s) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(s);
        return fromJson(jsonObject);
    }

    @NonNull
    private List<SearchHistoryEntry> parseList(String s) throws JsonParserException {
        JsonArray list = JsonParser.array().from(s);
        List<SearchHistoryEntry> resultList = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof JsonObject) {
                JsonObject entity = (JsonObject) object;
                resultList.add(fromJson(entity));
            }
        }
        return resultList;
    }

    private String toJson(SearchHistoryEntry entity) {
        JsonBuilder json = JsonObject.builder();
        try {
            json.value("serviceId", eU.encrypt(entity.getServiceId()));
            json.value("search", eU.encrypt(entity.getSearch()));
            String creationDate = new SimpleDateFormat(DATE_FORMAT, Locale.US).format(entity.getCreationDate());
            json.value("creationDate", eU.encrypt(creationDate));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption error", e);
        }
        String ret = JsonWriter.string(json.done());
        return ret;
    }

    private SearchHistoryEntry fromJson(JsonObject object) {
        try {
            int serviceId = Long.valueOf(eU.decryptAsLong(object.getString("serviceId"))).intValue();
            DateFormat df = new SimpleDateFormat(DATE_FORMAT, Locale.US);
            Date creationDate = df.parse(eU.decrypt(object.getString("creationDate")));
            String search = eU.decrypt(object.getString("search"));
            int uid = object.getInt("uid");
            SearchHistoryEntry entity = new SearchHistoryEntry(creationDate, serviceId, search);
            entity.setUid(uid);
            return entity;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption error", e);
        } catch (ParseException e) {
            throw new RuntimeException("error parsing date", e);
        }
    }
}
