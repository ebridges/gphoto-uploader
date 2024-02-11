[![Maven Package](https://github.com/ebridges/gphoto-uploader/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/ebridges/gphoto-uploader/actions/workflows/maven-publish.yml)

# gphoto-uploader

Uploads images using the Google Photos API

## Usage

```
GPhoto Uploader

Usage:
    gphoto-uploader
    gphoto-uploader (-h | --help)
    gphoto-uploader (-v | --version)

Environment Variable Configuration

The following environment variables can be configured for the application:

1. UPLOADER_STORED_CREDENTIALS_DIR: This variable is used to specify the directory where the uploader's credentials are stored. 
   Default: `HOME/.uploader-credentials`
2. UPLOADER_PHOTO_LIST: This variable is used to specify the path to the list of photos to be uploaded.
   Default: `photo-list.txt`
3. UPLOADER_CREDENTIALS: This variable is used to specify the path to the uploader's credentials file.
   Default: `credentials.json`
4. UPLOADER_VERBOSE: This variable is used to control the verbosity of the uploader. Set it to 'true' for verbose output.
   Default: `false`
```

## Releasing

Run the script `release.sh` with a version number of the form `vX.Y.Z` where X, Y, & Z are integers from 0-99. If the results look good after running, push to remote using the commands: `git push && git push :origin "v${VERSION}" && git push --tags`.

When successfully pushed to the remote, it will trigger a Github action that will create a Github release and publish a standalone executable JAR file to the Github packages repository.

## Running

Requires OAuth2 Client credentials to be stored in a local file.  Be sure to choose an "OAuth Client ID" of type
"Other" or "Installed".  This application, on first run, will redirect to a webserver running on localhost port `8000` that
will receive the redirect and the authorization code. The authorization code will be used to generate the access token and 
grant authorization to the application.

## Known Issues

### Conflicting Filenames when expanding Jar

**Issue**

```
$ jar xf gphoto-uploader.jar
java.io.IOException: META-INF/license : could not create directory
	at jdk.jartool/sun.tools.jar.Main.extractFile(Main.java:1451)
	at jdk.jartool/sun.tools.jar.Main.extract(Main.java:1385)
	at jdk.jartool/sun.tools.jar.Main.run(Main.java:390)
	at jdk.jartool/sun.tools.jar.Main.main(Main.java:1702)
```

**Workaround**

Remove all "license" files & directories in `META-INF`:

```
for i in `jar tf gphoto-uploader.jar | grep META-INF | grep -i license`; do zip -d gphoto-uploader.jar $i ; done
```

### Merge Strategy of maven-assembly-plugin

**Issue**

```
java.lang.IllegalStateException: Could not find policy 'pick_first'. Make sure its implementation is either registered to LoadBalancerRegistry or included in META-INF/services/io.grpc.LoadBalancerProvider from your jar files.
```

**Workaround**

Add implementation for 'LoadBalancerProvider`:

```
$ mkdir temp-directory
$ mv target/gphoto-uploader-2.1.0-jar-with-dependencies.jar temp-directory
$ cd temp-directory && jar xf gphoto-uploader-2.1.0-jar-with-dependencies.jar
$ rm META-INF/services/io.grpc.LoadBalancerProvider
$ echo io.grpc.internal.PickFirstLoadBalancerProvider > META-INF/services/io.grpc.LoadBalancerProvider
$ rm gphoto-uploader-2.1.0-jar-with-dependencies.jar
$ jar --create --manifest=META-INF/MANIFEST.MF --file ../target/gphoto-uploader-2.1.0-jar-with-dependencies.jar --verbose *
$ cd .. && /bin/rm -rf temp-directory
```
