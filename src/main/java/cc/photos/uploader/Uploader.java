package cc.photos.uploader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.currentTimeMillis;

public class Uploader {
  private static final Logger LOG = LoggerFactory.getLogger(Uploader.class);
  private Map<String,String> ALBUM_CACHE = new HashMap<>();

  private GPhotoUploadService uploadService = new GPhotoUploadService();

  public void authorize(String secretsFile) throws IOException {
    if(LOG.isInfoEnabled()) {
      LOG.info("authorizing client with credentials: {}", secretsFile);
    }
    this.uploadService.authorize(secretsFile);
  }

  /* assume album name looks like this: `2017/2017-01-01`; we only want the final portion */
  public String resolveAlbumId(Path albumPath) throws IOException {
    LOG.info("Getting album ID for [{}]", albumPath);
    String albumName =  albumPath.getFileName().toString();
    String albumId;
    synchronized (this) {
      albumId = lookupAlbumId(albumName);
      if (albumId == null) {
        LOG.info("no album found with name: {}, creating.", albumName);
        albumId = uploadService.createAlbum(albumName);
        ALBUM_CACHE.put(albumName, albumId);
      }
      assert albumId != null;
    }
    LOG.info("Found album ID {} for [{}]", albumId, albumPath);
    return albumId;
  }

  public void upload(String albumId, String mediaPath) throws IOException {
    String uploadId = uploadService.uploadBytes(mediaPath);
    LOG.info("uploadId: {}", uploadId);
    String response = uploadService.addMediaItem(albumId, uploadId, mediaPath);
    LOG.info("Upload completed: {}", response);
  }

  private String lookupAlbumId(String albumName) throws IOException {
    LOG.info("loooking up ID for album {}", albumName);
    long start = currentTimeMillis();
    if(ALBUM_CACHE.isEmpty()) {
      ALBUM_CACHE = uploadService.listAlbums();
    }
    long end = currentTimeMillis();
    LOG.info("listAlbums took {}ms", (end-start));
    return ALBUM_CACHE.get(albumName);
  }
}
