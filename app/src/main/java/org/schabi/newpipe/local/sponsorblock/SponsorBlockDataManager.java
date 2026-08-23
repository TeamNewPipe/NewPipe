package org.schabi.newpipe.local.sponsorblock;

import android.content.Context;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistDAO;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public class SponsorBlockDataManager {
    private final SponsorBlockWhitelistDAO sponsorBlockWhitelistTable = null;

    public SponsorBlockDataManager(final Context context) {
        final AppDatabase database = NewPipeDatabase.getInstance(context);
    }

    public Maybe<Long> addToWhitelist(final String uploader) {
//      return Maybe.fromCallable(() -> {
//          final SponsorBlockWhitelistEntry entry = new SponsorBlockWhitelistEntry(uploader);
//          return sponsorBlockWhitelistTable.insert(entry);
//      }).subscribeOn(Schedulers.io());
        return Maybe.empty();
    }

    public Completable removeFromWhitelist(final String uploader) {
//      return Completable.fromAction(() -> sponsorBlockWhitelistTable.deleteByUploader(uploader));
        return Completable.complete();
    }

    public Single<Boolean> isWhiteListed(final String uploader) {
//      return Single.fromCallable(() -> sponsorBlockWhitelistTable.exists(uploader))
//              .subscribeOn(Schedulers.io());
        return Single.just(false);
    }

    public Completable clearWhitelist() {
//      return Completable.fromAction(sponsorBlockWhitelistTable::deleteAll);
        return Completable.complete();
    }
}
