package cc.photos.uploader.util;

import static java.lang.System.*;

public class Constants {
    private static final String ENV_UPLOADER_STORED_CREDENTIALS_DIR = "UPLOADER_STORED_CREDENTIALS_DIR";
    private static final String DEFAULT_UPLOADER_STORED_CREDENTIALS_DIR = getenv("HOME") + "/.uploader-credentials";
    public static final String UPLOADER_STORED_CREDENTIALS_DIR = getenv().getOrDefault(ENV_UPLOADER_STORED_CREDENTIALS_DIR, DEFAULT_UPLOADER_STORED_CREDENTIALS_DIR);

    public static final String ENV_UPLOADER_PHOTO_LIST = "UPLOADER_PHOTO_LIST";
    private static final String DEFAULT_PHOTO_LIST_PATH = "photo-list.txt";

    public static final String UPLOADER_PHOTO_LIST = getenv().getOrDefault(ENV_UPLOADER_PHOTO_LIST, DEFAULT_PHOTO_LIST_PATH);

    public static final String ENV_UPLOADER_CREDENTIALS = "UPLOADER_CREDENTIALS";
    private static final String DEFAULT_CREDENTIALS_PATH = "credentials.json";

    public static final String UPLOADER_CREDENTIALS = getenv().getOrDefault(ENV_UPLOADER_CREDENTIALS, DEFAULT_CREDENTIALS_PATH);

    private static final String ENV_UPLOADER_VERBOSE = "UPLOADER_VERBOSE";
    public static final String UPLOADER_VERBOSE = getenv().getOrDefault(ENV_UPLOADER_VERBOSE, "false");
}
