package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.download.DownloadedStreamEntity
import org.schabi.newpipe.database.download.DownloadedStreamStatus
import org.schabi.newpipe.database.download.DownloadedStreamsDao
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.streams.io.StoredFileHelper

object DownloadedStreamsRepository {

    data class DownloadAssociation(
        val streamUid: Long,
        val entityId: Long
    )

    private fun database(context: Context): AppDatabase {
        return NewPipeDatabase.getInstance(context)
    }

    private fun downloadedDao(context: Context): DownloadedStreamsDao {
        return database(context).downloadedStreamsDao()
    }

    fun observeByStreamUid(context: Context, streamUid: Long): Flowable<List<DownloadedStreamEntity>> {
        return downloadedDao(context)
            .observeByStreamUid(streamUid)
            .subscribeOn(Schedulers.io())
    }

    fun getByStreamUid(context: Context, streamUid: Long): Maybe<DownloadedStreamEntity> {
        return downloadedDao(context)
            .getByStreamUid(streamUid)
            .subscribeOn(Schedulers.io())
    }

    fun ensureStreamEntry(context: Context, info: StreamInfo): Single<Long> {
        return Single.fromCallable {
            database(context).streamDAO().upsert(StreamEntity(info))
        }.subscribeOn(Schedulers.io())
    }

    fun upsertForEnqueued(
        context: Context,
        info: StreamInfo,
        storage: StoredFileHelper,
        displayName: String?,
        mime: String?,
        qualityLabel: String?,
        durationMs: Long?,
        sizeBytes: Long?
    ): Single<DownloadAssociation> {
        return Single.fromCallable {
            val db = database(context)
            db.runInTransaction<DownloadAssociation> {
                val streamDao = db.streamDAO()
                val dao = db.downloadedStreamsDao()
                val streamId = streamDao.upsert(StreamEntity(info))
                val now = System.currentTimeMillis()
                val fileUri = storage.uriString()
                val entity = dao.findEntityByStreamUid(streamId)
                val resolvedDisplayName = displayName ?: storage.getName()
                val resolvedMime = mime ?: storage.getType()

                if (entity == null) {
                    val newEntity = DownloadedStreamEntity(
                        streamUid = streamId,
                        serviceId = info.serviceId,
                        url = info.url,
                        fileUri = fileUri,
                        parentUri = storage.parentUriString(),
                        displayName = resolvedDisplayName,
                        mime = resolvedMime,
                        sizeBytes = sizeBytes,
                        qualityLabel = qualityLabel,
                        durationMs = durationMs,
                        status = DownloadedStreamStatus.IN_PROGRESS,
                        addedAt = now,
                        lastCheckedAt = null,
                        missingSince = null
                    )
                    val insertedId = dao.insert(newEntity)
                    val resolvedId = if (insertedId == -1L) {
                        dao.findEntityByStreamUid(streamId)?.id
                            ?: throw IllegalStateException("Failed to resolve downloaded stream entry")
                    } else {
                        insertedId
                    }
                    newEntity.id = resolvedId
                    DownloadAssociation(streamId, resolvedId)
                } else {
                    entity.serviceId = info.serviceId
                    entity.url = info.url
                    entity.fileUri = fileUri
                    val parentUri = storage.parentUriString()
                    if (parentUri != null) {
                        entity.parentUri = parentUri
                    }
                    entity.displayName = resolvedDisplayName
                    entity.mime = resolvedMime
                    entity.sizeBytes = sizeBytes
                    entity.qualityLabel = qualityLabel
                    entity.durationMs = durationMs
                    entity.status = DownloadedStreamStatus.IN_PROGRESS
                    entity.lastCheckedAt = null
                    entity.missingSince = null
                    if (entity.addedAt <= 0) {
                        entity.addedAt = now
                    }
                    dao.update(entity)
                    DownloadAssociation(streamId, entity.id)
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun markFinished(
        context: Context,
        association: DownloadAssociation,
        serviceId: Int,
        url: String,
        storage: StoredFileHelper,
        mime: String?,
        qualityLabel: String?,
        durationMs: Long?,
        sizeBytes: Long?
    ): Completable {
        return Completable.fromAction {
            val dao = downloadedDao(context)
            val now = System.currentTimeMillis()
            val entity = dao.findEntityById(association.entityId)
                ?: dao.findEntityByStreamUid(association.streamUid)
                ?: DownloadedStreamEntity(
                    streamUid = association.streamUid,
                    serviceId = serviceId,
                    url = url,
                    fileUri = storage.uriString(),
                    parentUri = storage.parentUriString(),
                    displayName = storage.getName(),
                    mime = mime ?: storage.getType(),
                    sizeBytes = sizeBytes,
                    qualityLabel = qualityLabel,
                    durationMs = durationMs,
                    status = DownloadedStreamStatus.IN_PROGRESS,
                    addedAt = now
                )
            entity.serviceId = serviceId
            entity.url = url
            entity.fileUri = storage.uriString()
            storage.parentUriString()?.let { entity.parentUri = it }
            entity.displayName = storage.getName()
            val resolvedMime = mime ?: storage.getType() ?: entity.mime
            entity.mime = resolvedMime
            entity.sizeBytes = sizeBytes ?: storage.safeLength() ?: entity.sizeBytes
            if (qualityLabel != null) {
                entity.qualityLabel = qualityLabel
            }
            if (durationMs != null) {
                entity.durationMs = durationMs
            }
            entity.status = DownloadedStreamStatus.AVAILABLE
            entity.lastCheckedAt = now
            entity.missingSince = null
            if (entity.addedAt <= 0) {
                entity.addedAt = now
            }

            if (entity.id == 0L) {
                val newId = dao.insert(entity)
                entity.id = newId
            } else {
                dao.update(entity)
            }
        }.subscribeOn(Schedulers.io())
    }

    fun updateStatus(
        context: Context,
        entityId: Long,
        status: DownloadedStreamStatus,
        lastCheckedAt: Long? = System.currentTimeMillis(),
        missingSince: Long? = null
    ): Completable {
        return Completable.fromAction {
            downloadedDao(context).updateStatus(entityId, status, lastCheckedAt, missingSince)
        }.subscribeOn(Schedulers.io())
    }

    fun updateFileUri(context: Context, entityId: Long, uri: Uri): Completable {
        return Completable.fromAction {
            downloadedDao(context).updateFileUri(entityId, uri.toString())
        }.subscribeOn(Schedulers.io())
    }

    fun relink(context: Context, entity: DownloadedStreamEntity, uri: Uri): Completable {
        return Single.fromCallable {
            StoredFileHelper(context, uri, entity.mime ?: StoredFileHelper.DEFAULT_MIME)
        }.flatMapCompletable { helper ->
            val association = DownloadAssociation(entity.streamUid, entity.id)
            markFinished(
                context,
                association,
                entity.serviceId,
                entity.url,
                helper,
                helper.type,
                entity.qualityLabel,
                entity.durationMs,
                helper.safeLength()
            )
        }.subscribeOn(Schedulers.io())
    }

    fun deleteByStreamUid(context: Context, streamUid: Long): Completable {
        return Completable.fromAction {
            downloadedDao(context).deleteByStreamUid(streamUid)
        }.subscribeOn(Schedulers.io())
    }

    private fun StoredFileHelper.uriString(): String = getUri().toString()

    private fun StoredFileHelper.safeLength(): Long? {
        return runCatching { length() }.getOrNull()
    }

    private fun StoredFileHelper.parentUriString(): String? {
        return runCatching { getParentUri() }.getOrNull()?.toString()
    }
}
