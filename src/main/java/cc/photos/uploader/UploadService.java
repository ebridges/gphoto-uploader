package cc.photos.uploader;

import cc.photos.uploader.model.AlbumEntry;
import cc.photos.uploader.tasks.ByteUploadTask;
import cc.photos.uploader.tasks.ItemCreationTask;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.types.proto.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.String.format;

public class UploadService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);

    /**
     * Number of parallel threads to use when uploading image bytes.
     */
    public static final int NUM_BYTE_UPLOAD_THREAD_DEFAULT = 6;

    /**
     * Number of media items to include in a call to `mediaItems.batchCreate`. This must be not be
     * greater than 50. See
     * https://developers.google.com/photos/library/guides/upload-media#creating-media-item
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    public static final int NUM_BATCH_SIZE_DEFAULT = 50;

    private final PhotosLibraryClient client;
    @SuppressWarnings("FieldCanBeLocal")
    private final int numByteUploadThreads;
    private final int numCreationBatchSize;
    private final ArrayList<ByteUploadTask.ByteUploadResult> creationQueue;
    private final ExecutorService uploadExecutor;
    private final CompletionService<ByteUploadTask.ByteUploadResult> uploadService;
    private final HashMap<String, ByteUploadTask.ByteUploadResult> successfulUploads;
    private final HashMap<AlbumEntry, ByteUploadTask.ByteUploadResult> failedUploads;
    private final Map<String, ItemCreationTask.ItemCreationResult> itemCreationResults;

    private int totalNumOfExpectedUploads;

    public UploadService(PhotosLibraryClient client) {
        this.client = client;
        this.numByteUploadThreads = NUM_BYTE_UPLOAD_THREAD_DEFAULT;
        this.numCreationBatchSize = NUM_BATCH_SIZE_DEFAULT;
        this.creationQueue = new ArrayList<>(numCreationBatchSize);
        this.uploadExecutor = Executors.newFixedThreadPool(this.numByteUploadThreads);
        this.uploadService = new ExecutorCompletionService<>(uploadExecutor);
        this.successfulUploads = new HashMap<>();
        this.failedUploads = new HashMap<>();
        this.itemCreationResults = new HashMap<>();
        this.totalNumOfExpectedUploads = 0;
    }

    public void addEntriesToAlbums(List<AlbumEntry> albumEntries) throws InterruptedException, ExecutionException {
        albumEntries.forEach(albumWithEntry -> {
            this.totalNumOfExpectedUploads += 1;
            scheduleUploadFileBytes(albumWithEntry);
        });
        LOG.info("All byte uploads tasks have been scheduled.");

        for (int finishedResults = 0; finishedResults < this.totalNumOfExpectedUploads; finishedResults++) {
            // Wait until a task is completed and get its result.
            Future<ByteUploadTask.ByteUploadResult> futureResult = uploadService.take();
            ByteUploadTask.ByteUploadResult uploadResult = futureResult.get();

            if (uploadResult.isOk()) {
                // The bytes were successfully uploaded and an upload token is available.
                successfulUploads.put(uploadResult.uploadToken, uploadResult);

                // Add it to the queue for the next call to create media items.
                creationQueue.add(uploadResult);

            } else {
                // The byte upload failed, collect its result and deal with the error later.
                failedUploads.put(uploadResult.entryBeingUploaded, uploadResult);
            }

            // If enough tasks have completed so that a batch is full (or this was the final upload),
            // submit the upload tokens to create media items.
            if (creationQueue.size() >= numCreationBatchSize
                    || successfulUploads.size() + failedUploads.size() >= this.totalNumOfExpectedUploads) {

                LOG.info("Starting batch creation call.");

                createMediaItems();
            }
        }
        LOG.info("All uploads have been processed.");
    }

    /**
     * Use an {@link ItemCreationTask} to call the Library API to creat media items from the internal
     * queue.
     */
    private void createMediaItems() {
        if (creationQueue.isEmpty()) {
            // No items in the queue to be created. All byte uploads may have failed.
            LOG.warn("No items to create.");
            return;
        }
        // Empty the queue and get the items to be created.
        List<ByteUploadTask.ByteUploadResult> itemsToCreate = new ArrayList<>(creationQueue);
        creationQueue.clear();

        // Make the API call to `mediaItems.batchCreate`. This method returns the status of each item.
        // Important: Note how this call is made sequentially, blocking further execution until it completes.
        // Execution of this task is not scheduled in a separate thread, instead it is done here on the main
        // thread blocking further execution until it completes.
        Map<String, ItemCreationTask.ItemCreationResult> creationResult =
                new ItemCreationTask(client, itemsToCreate).call();

        // Store all results for later processing.
        itemCreationResults.putAll(creationResult);
    }

    /**
     * Use a {@link ByteUploadTask} to call the Library API to upload media items. All API calls are
     * scheduled here using the internal completion service: {@link #uploadService}.
     *
     * @param albumWithEntry Files to upload.
     * @see CompletionService#submit(Callable)
     */
    private void scheduleUploadFileBytes(AlbumEntry albumWithEntry) {
        // Queue all files for upload. The ByteUploadTask uploads the bytes of the file to the Library API.
        // Note that the ExecutorService handles execution of threads, here they are queued up for processing.
        // These tasks are executed in parallel, based on the thread pool configured above.
        // The Library API supports parallel byte uploads for the same user.
        LOG.info("-> Scheduling byte upload for: " + albumWithEntry);
        // Initialise a new upload tasks and schedule it for execution.
        ByteUploadTask task = new ByteUploadTask(client, albumWithEntry);
        uploadService.submit(task);
    }

    /**
     * Prints the status of the Uploader, including all failed and successful uploads and media item
     * creations.
     */
    public void printState() {
        // Print the failed byte uploads
        LOG.info("The following " + failedUploads.size() + " files could not be uploaded:");
        for (ByteUploadTask.ByteUploadResult uploadResult : failedUploads.values()) {
            // Print the error that lead to this failure.
            // If it was an ApiException there may be some additional details that could be examined
            // before retrying it as needed. Here it is just printed out.
            printError(uploadResult.entryBeingUploaded.mediaPath().getAbsolutePath(), uploadResult.error.toString());
        }

        // Print the successful media item creations and extract ones that failed.
        LOG.info("The following items were successfully created:");
        // Check the status of each item creation, keep track of all failed creations for retry.
        List<ItemCreationTask.ItemCreationResult> failedCreations = new LinkedList<>();
        for (Map.Entry<String, ItemCreationTask.ItemCreationResult> entry :
                itemCreationResults.entrySet()) {
            ItemCreationTask.ItemCreationResult value = entry.getValue();
            if (value.isOk()) {
                // The item was successfully created. Print out its details.
                //noinspection OptionalGetWithoutIsPresent
                MediaItem item = value.mediaItem.get();
                printOk(entry.getKey(), item.getProductUrl());
            } else {
                // The item could not be created. Keep track of it.
                failedCreations.add(value);
            }
        }

        // Print the failed media item creations. If possible, print some additional details if the API
        // returned an error.
        LOG.info("The following " + failedCreations.size() + " files could not be created:");
        for (ItemCreationTask.ItemCreationResult result : failedCreations) {
            // The file was successfully uploaded in the first step. Look up its File definition by the
            // upload token
            File file = successfulUploads.get(result.uploadToken).entryBeingUploaded.mediaPath();

            if (result.status.isPresent()) {
                // The API returned a status that contains some information about the error.
                // Here it is just printed out.
                printError(file.getAbsolutePath(), result.status.get().toString());
            } else {
                // Print details about the Throwable that caused this error. If it was an ApiException there
                // may be some additional details that could be examined before retrying it as needed.
                // Here it is just printed out.
                printError(file.getAbsolutePath(), result.error.toString());
            }
        }
    }

    /**
     * Print a success message prefixed by the file name.
     *
     * @param fileName Name of this file is used as a prefix for the message.
     * @param message Message to print out.
     */
    private static void printOk(String fileName, String message) {
        print("OK", fileName, message);
    }

    /**
     * Print an error message prefixed by the file name.
     *
     * @param fileName Name of this file is used as a prefix for the message.
     * @param message Message to print out.
     */
    private static void printError(String fileName, String message) {
        print("ERR", fileName, message);
    }

    /**
     * Print a message prefixed by a string.
     *
     * @param status Status of the message.
     * @param prefix Prefix printed before the message.
     * @param message Message to print out.
     */
    private static void print(String status, String prefix, String message) {
        LOG.info(format("\t%s: %s: %s", status, prefix, message));
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void close() throws Exception {
        if (uploadExecutor != null) {
            uploadExecutor.shutdown();
        }
    }
}
