package cc.photos.uploader.tasks;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.proto.NewMediaItemResult;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * Creates media items in the user's Google Photos library.
 *
 * <p>This task calls {@link PhotosLibraryClient#batchCreateMediaItems(String, List)} to create media items
 * in the user's library. <b>IMPORTANT:</b> This task should never be called in parallel for the
 * same user to avoid concurrency issues. Only call this task serially (one after another) for the
 * same user. It may be called in parallel for different users. Results are returned as a {@link
 * Map} that matches upload tokens (given as the input) to an instance of {@link
 * ItemCreationResult}, which contains a {@link MediaItem} if the item was successfully created.
 */
public class ItemCreationTask implements Callable<Map<String, ItemCreationTask.ItemCreationResult>> {
    private static final Logger LOG = LoggerFactory.getLogger(ItemCreationTask.class);

    /** Items to be created. */
    private final List<ByteUploadTask.ByteUploadResult> itemsToCreate;

    /** API client to use for media creation. */
    private final PhotosLibraryClient photosLibraryClient;

    /**
     * An {@link ItemCreationTask} calls {@link PhotosLibraryClient#batchCreateMediaItems(List)} with
     * a list of {@link NewMediaItem}s. Each {@link NewMediaItem} requires an upload token, file name
     * and description which is read from the {@link ByteUploadTask}. The description is set to
     * the current date and time.
     *
     * @param photosLibraryClient API client for media creation.
     * @param itemsToCreate The successful byte uploads to be created as media items.
     */
    public ItemCreationTask(PhotosLibraryClient photosLibraryClient, List<ByteUploadTask.ByteUploadResult> itemsToCreate) {
        this.photosLibraryClient = photosLibraryClient;
        this.itemsToCreate = itemsToCreate;
    }

    /**
     * Creates media items.
     *
     * <p>Calls mediaItems.batchCreate from the Library API. This creates media items for the upload
     * results. Returns a list of all Files that could not be created. If the entire API call failed,
     * all items are returned as failed.
     *
     * <p>Returns a {@link Map} that maps the upload token (as included in the {@link ByteUploadTask}) to a result.
     * This mapping is needed as the API call to {@link PhotosLibraryClient#batchCreateMediaItems(List)} may have
     * been partially successful, so the caller must verify each media item individually.
     *
     * @return Status for each item created.
     */
    public Map<String, ItemCreationResult> call() {
        LOG.info("Calling API to create items: " + itemsToCreate.size());

        if (itemsToCreate.isEmpty()) {
            // No items to create.
            throw new IllegalArgumentException("No items to create.");
        }

        // Keep track of the result for each media item creation. Some items could fail to be created
        // during a call, while others may be successful.
        Map<String, ItemCreationResult> results = new HashMap<>(itemsToCreate.size());

        // For each UploadResult, create a NewMediaItem with the following components:
        // - uploadToken obtained from the byte upload request
        // - filename that will be shown to the user in Google Photos
        // - description that will be shown to the user in Google Photos
        Map<Album,List<NewMediaItem>> albumItemMap = new HashMap<>();
        int itemCount = 0;
        for (ByteUploadTask.ByteUploadResult uploadResult : itemsToCreate) {
            NewMediaItem newMediaItem =
                    NewMediaItemFactory.createNewMediaItem(
                            uploadResult.uploadToken,
                            /* fileName= */ uploadResult.entryBeingUploaded.mediaPath().getName(),
                            /* description=*/ "Created at " + new Date());
            itemCount++;
            albumItemMap.computeIfAbsent(uploadResult.entryBeingUploaded.album(), k -> new ArrayList<>()).add(newMediaItem);
        }

        try {
            LOG.info("Calling batchCreate for new items: " + itemCount);

            // Call the API to create media items.
            int resultsCount = 0;
            Map<Album, BatchCreateMediaItemsResponse> responses = new HashMap<>();
            for (Album album : albumItemMap.keySet()) {
                LOG.info("> Creating " + albumItemMap.get(album).size() + " items for album: " + album.getTitle());
                BatchCreateMediaItemsResponse response = photosLibraryClient.batchCreateMediaItems(album.getId(), albumItemMap.get(album));
                resultsCount += response.getNewMediaItemResultsCount();
                responses.put(album, response);
            }

            // Confirm that all items where created successfully. Some items may have failed.
            for (Album album : responses.keySet()) {
                for (NewMediaItemResult itemsResponse : responses.get(album).getNewMediaItemResultsList()) {
                    Status status = itemsResponse.getStatus();
                    if (status.getCode() == Code.OK_VALUE) {
                        // The item was successfully created in the user's library.
                        MediaItem createdItem = itemsResponse.getMediaItem();
                        LOG.info("Item successfully created: " + createdItem.getFilename() + " in album: "+album.getTitle());
                        results.put(
                                itemsResponse.getUploadToken(),
                                ItemCreationResult.createSuccessResult(
                                        itemsResponse.getUploadToken(), status, itemsResponse.getMediaItem()));
                    } else {
                        // The item could not be created.
                        LOG.warn("Item not created. " + status.getMessage());

                        // Keep track of the failed item and its status.
                        results.put(
                                itemsResponse.getUploadToken(),
                                ItemCreationResult.createFailureResultWithStatus(
                                        itemsResponse.getUploadToken(), status));
                    }
                    // Confirm that a status was returned for all upload tokens, otherwise mark the missing ones
                    // as failed.
                    if (resultsCount != itemCount) {
                        for (ByteUploadTask.ByteUploadResult upload : itemsToCreate) {
                            if (!results.containsKey(upload.uploadToken)) {
                                LOG.warn("Item not created. No status returned for upload token: " + upload.uploadToken);
                                results.put(
                                        upload.uploadToken,
                                        ItemCreationResult.createFailureResultWithThrowable(
                                                upload.uploadToken, new Exception("Upload token was not returned.")));
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            // An error occurred while creating the media items and the entire request failed.
            // The client library would have already attempted to retry the request based on the retry
            // configuration. At this point, the request may not be automatically retryable and should be
            // handled by the application.
            // If this exception is of type APIException You can verify this by checking the status, its
            // code and whether it is retryable. See the documentation for Status for details.
            LOG.error("API error while calling createMediaItems. " + exception.getMessage(), exception);
            // Mark all items that were in this request as failed.
            for (ByteUploadTask.ByteUploadResult byteUploadResult : itemsToCreate) {
                results.put(
                        byteUploadResult.uploadToken,
                        ItemCreationResult.createFailureResultWithThrowable(
                                byteUploadResult.uploadToken, exception));
            }
        }

        return results;
    }

    /**
     * Result of an {@link ItemCreationTask}. Successful results include a {@link MediaItem} and an OK
     * {@link Status}. Failed results either include a {@link Throwable} or a {@link Status} with a
     * failed code.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class ItemCreationResult {
        /** Upload token this result is for. */
        public final String uploadToken;
        /** {@link MediaItem} if returned from the API call. */
        public final Optional<MediaItem> mediaItem;
        /** {@link Throwable} that describes a failure if it occurred. May be null if not set. */
        public final Optional<Throwable> error;
        /**
         * {@link Status} that describes success or failure if it was returned from the API call. May be
         * null if not set.
         */
        public final Optional<Status> status;

        /**
         * Result of a call to create an item, consists of an upload token and optionally an error,
         * status and the media item that was created.
         *
         * @param uploadToken Upload token for this result.
         * @param error Optional error as a {@link Throwable}.
         * @param status Optional status as returned from the API.
         * @param mediaItem Optional media item as returned from the API.
         */
        private ItemCreationResult(
                String uploadToken, Throwable error, Status status, MediaItem mediaItem) {
            this.uploadToken = uploadToken;
            this.mediaItem = Optional.ofNullable(mediaItem);
            this.error = Optional.ofNullable(error);
            this.status = Optional.ofNullable(status);
        }

        /**
         * Task was successful if there is a media item, a successful status and no error.
         *
         * @return True if the task was successful
         */
        public boolean isOk() {
            return mediaItem.isPresent()
                    && error.isEmpty()
                    && status.isPresent()
                    && status.get().getCode() == Code.OK_VALUE;
        }

        @Override
        public String toString() {
            return format(
                "ItemCreationResult{uploadToken=%s, mediaItem=%s, error=%s, status=%s}",
                uploadToken, mediaItem, error, status);
        }

        /**
         * Successful result of an item creation call to the API. Includes the status and the media item
         * as returned from the API.
         *
         * @param uploadToken Upload token for this result.
         * @param status Status as returned from the API.
         * @param mediaItem Media item as returned from the API.
         */
        public static ItemCreationResult createSuccessResult(
                String uploadToken, @Nonnull Status status, MediaItem mediaItem) {
            return new ItemCreationResult(uploadToken, null, status, mediaItem);
        }

        /**
         * Failed result of an item creation call to the API. Includes the {@link Throwable} that was
         * thrown during the API call.
         *
         * @param uploadToken Upload token for this result.
         * @param error {@link Throwable} that occurred during the call to the API.
         */
        public static ItemCreationResult createFailureResultWithThrowable(
                String uploadToken, Throwable error) {
            return new ItemCreationResult(uploadToken, error, null, null);
        }

        /**
         * Failed result of an item creation call to the API. Includes the {@link Status} field as
         * returned by the API.
         *
         * @param uploadToken Upload token for this result.
         * @param status Status as returned from the API.
         */
        public static ItemCreationResult createFailureResultWithStatus(
                String uploadToken, Status status) {
            return new ItemCreationResult(uploadToken, null, status, null);
        }
    }
}