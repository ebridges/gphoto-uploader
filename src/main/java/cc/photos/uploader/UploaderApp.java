package cc.photos.uploader;

import cc.photos.uploader.factory.PhotosLibraryClientFactory;
import cc.photos.uploader.model.AlbumEntry;
import cc.photos.uploader.util.Constants;
import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.types.proto.Album;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toList;

public class UploaderApp {
    private static final Logger LOG = LoggerFactory.getLogger(UploaderApp.class);

    private static final List<String> REQUIRED_SCOPES = List.of(
            "https://www.googleapis.com/auth/photoslibrary",
            "https://www.googleapis.com/auth/photoslibrary.sharing",
            "https://www.googleapis.com/auth/photoslibrary.readonly",
            "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata",
            "https://www.googleapis.com/auth/photoslibrary.appendonly",
            "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata"
    );

    private static final String USAGE = "/usage.txt";
    private static final String VERSION = "/version.txt";

    public static void main(String[] args) {
        handleArgsIfNeeded(args);

        Stopwatch timer = Stopwatch.createStarted();
        Map<String, String> opts = parseOpts();
        LOG.info("GPhoto Uploader Started to upload [{}]", opts.get(Constants.ENV_UPLOADER_PHOTO_LIST));

        String credentialsPath = opts.get(Constants.ENV_UPLOADER_CREDENTIALS);
        try (PhotosLibraryClient client = PhotosLibraryClientFactory.createClient(credentialsPath, REQUIRED_SCOPES)) {
            AlbumService albumService = new AlbumService(client);
            albumService.initializeAlbumCache();
            try (UploadService uploadService = new UploadService(client)) {
                File filePaths = new File(opts.get(Constants.ENV_UPLOADER_PHOTO_LIST));
                List<AlbumEntry> albumEntries = parseAlbumsToUpload(albumService, filePaths);
                uploadService.addEntriesToAlbums(albumEntries);
                uploadService.printState();
            }
        } catch (Exception e) {
            LOG.error("Error uploading photos", e);
        }
        timer.stop();
        LOG.info("Photo Uploader Finished in {}", timer);
    }

    /*
    Given a file with a list of filepaths of this format: "/yearName/albumName/filename.jpg" parse the albumName from
    the filepath, and initialize an instance of `AlbumEntry` returning the result as a Stream of `AlbumEntry`.
     */
    private static List<AlbumEntry> parseAlbumsToUpload(AlbumService albumService, File filePaths) throws IOException {
        try (Stream<String> lines = Files.lines(filePaths.toPath())) {
            return lines.map(line -> {
                Path path = Paths.get(line);
                String albumName = path.getName(path.getNameCount() - 2).toString();
                Album album = albumService.createAlbumIfAbsent(albumName);
                return new AlbumEntry(album, path.toFile());
            }).collect(toList());
        }
    }

    private static Map<String, String> parseOpts() {
        Map<String, String> opts = new HashMap<>();

        opts.put(Constants.ENV_UPLOADER_CREDENTIALS, Constants.UPLOADER_CREDENTIALS);
        opts.put(Constants.ENV_UPLOADER_PHOTO_LIST, Constants.UPLOADER_PHOTO_LIST);
        boolean verbose = parseBoolean(Constants.UPLOADER_VERBOSE);

        // handle verbose arg
        if (verbose) {
            setLogLevel(Level.DEBUG);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(opts.toString());
        }

        return opts;
    }

    private static void setLogLevel(@SuppressWarnings("SameParameterValue") Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    private static void handleArgsIfNeeded(String[] args) {
        if (args.length > 0) {
            if ("-h".equals(args[0]) || "--help".equals(args[0])) {
                printHelp();
                System.exit(0);
            }
            if ("-v".equals(args[0]) || "--version".equals(args[0])) {
                printVersion();
                System.exit(0);
            }
        }
    }

    private static void printHelp() {
        renderFileToStdout(USAGE);
    }

    private static void printVersion() {
        renderFileToStdout(VERSION);
    }

    private static void renderFileToStdout(String filename) {
        try(InputStream is = UploaderApp.class.getResourceAsStream(filename)) {
            if (is != null) {
                new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .forEach(System.out::println);
            } else {
                System.err.printf("%s is not found.\n", filename);
            }
        } catch (IOException e) {
            System.err.printf("Error loading %s.\n", filename);
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }
}
