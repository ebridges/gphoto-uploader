package cc.photos.uploader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class UploaderApp {
  private static final Logger LOG = LoggerFactory.getLogger(UploaderApp.class);

  public static void main(String[] args) throws Exception {
    LOG.info("GPhoto Uploader Started to upload [{}]", args[0]);
    String[] imagesToUpload = args[0].split("\\s*,\\s*");
    Uploader u = new Uploader();
    u.authorize("/client-secret.json");

    for(String toUpload : imagesToUpload) {
      Path albumPath = UploaderApp.getAlbumName(toUpload);
      String albumId = u.resolveAlbumId(albumPath);
      if (albumId != null) {
        u.upload(albumId, toUpload);
      } else {
        LOG.error("unable to create album {}", albumPath);
      }
    }
  }

  private static Path getAlbumName(String mediaFile) {
    Path mediaPath = Paths.get(mediaFile);
    return mediaPath.getParent();
  }
}
