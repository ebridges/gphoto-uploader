package cc.photos.uploader;

import ch.qos.logback.classic.Level;
import org.docopt.Docopt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toMap;

public class UploaderApp {
  private static final Logger LOG = LoggerFactory.getLogger(UploaderApp.class);
  private static final String USAGE = "/usage.txt";
  private static final String VERSION = "1.0";

  private static final String OPT_FILE = "--file";
  private static final String OPT_CREDENTIALS = "--credentials";
  private static final String OPT_VERBOSE = "--verbose";

  public static void main(String[] args) throws Exception {
    Map<String, String> opts = parseOpts(args);

    Uploader u = new Uploader();
    u.authorize(opts.get(OPT_CREDENTIALS));

    LOG.info("GPhoto Uploader Started to upload [{}]", opts.get(OPT_FILE));
    String[] imagesToUpload = opts.get(OPT_FILE).split("\\s*,\\s*");

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

  private static Map<String, String> parseOpts(String[] args) throws IOException {
    try(InputStream is = UploaderApp.class.getResourceAsStream(USAGE)) {
      Map<String, String> opts = new Docopt(is)
          .withVersion(VERSION)
          .withHelp(true)
          .parse(args)
          // by default, docopt returns as a Map<String,Object>
          // the following is to convert it to a Map<String,String>
          .entrySet()
          .stream()
          .collect(
              toMap(Map.Entry::getKey, e -> e.getValue().toString())
          );

      // handle verbose arg
      if(parseBoolean(opts.get(OPT_VERBOSE))) {
        setLogLevel(Level.DEBUG);
      }

      if(LOG.isDebugEnabled()) {
        LOG.debug(opts.toString());
      }

      return opts;
    }
  }

  private static void setLogLevel(@SuppressWarnings("SameParameterValue") Level level) {
    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.setLevel(level);
  }
}
