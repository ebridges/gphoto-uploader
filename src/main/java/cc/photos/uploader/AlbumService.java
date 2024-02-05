package cc.photos.uploader;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.types.proto.Album;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class AlbumService {
    private static final Logger LOG = LoggerFactory.getLogger(AlbumService.class);
    public final Map<String, Album> ALBUM_CACHE = new HashMap<>();
    private final PhotosLibraryClient client;

    public AlbumService(PhotosLibraryClient client) {
        this.client = client;
    }

    public void initializeAlbumCache() {
        LOG.info("Initializing album cache");
        for (Album album : client.listAlbums().iterateAll()) {
            ALBUM_CACHE.put(album.getTitle(), album);
        }
    }

    public Album createAlbumIfAbsent(String albumName) {
        Album album = ALBUM_CACHE.get(albumName);
        if (album == null) {
            LOG.info("No album found with name: {}, creating.", albumName);
            album = client.createAlbum(albumName);
            ALBUM_CACHE.put(albumName, album);
        }
        return album;
    }

}
