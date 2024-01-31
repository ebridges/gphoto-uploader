package cc.photos.uploader;

import cc.photos.uploader.util.Counter;
import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import org.docopt.Docopt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toMap;

public class UploaderApp {
  private static final Logger LOG = LoggerFactory.getLogger(UploaderApp.class);
  private static final String USAGE = "/usage.txt";
  private static final String VERSION = "1.6";
  private static final String OPT_FILE = "--photo-list";
  private static final String OPT_CREDENTIALS = "--credentials";
  private static final String OPT_VERBOSE = "--verbose";

  @SuppressWarnings("UnstableApiUsage")
  public static void main(String[] args) throws Exception {
    Stopwatch timer = Stopwatch.createStarted();
    Counter counter = new Counter();
    Map<String, String> opts = parseOpts(args);
    LOG.info("GPhoto Uploader Started to upload [{}]", opts.get(OPT_FILE));

    Uploader u = new Uploader();
    u.authorize(opts.get(OPT_CREDENTIALS));

    try (Stream<String> pathsToUpload = Files.lines(Paths.get(opts.get(OPT_FILE)))) {
      pathsToUpload.map(Paths::get).forEach( (image) -> {
        u.upload(image);
        counter.incr();
      });
    }

    timer.stop();
    LOG.info("Upload of "+counter+" files completed. ["+timer+"]");
  }

  private static Map<String, String> parseOpts(String[] args) throws IOException {
    if(LOG.isDebugEnabled()) {
      LOG.debug("Args: {}", Arrays.toString(args));
    }

    try(InputStream is = UploaderApp.class.getResourceAsStream(USAGE)) {
      if (is == null) {
        throw new IOException("Could not find usage file: " + USAGE);
      }
      Map<String, String> opts = new Docopt(is)
          .withVersion(VERSION)
          .withHelp(true)
          .parse(args)
          // by default, docopt returns as a Map<String,Object>
          // the following is to convert it to a Map<String,String>
          .entrySet()
          .stream()
          .collect(
              toMap(Map.Entry::getKey, e -> e.getValue() != null ? e.getValue().toString() : "")
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
