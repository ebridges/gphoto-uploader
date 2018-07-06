# gphoto-uploader

Uploads images to the new Google Photos API

## Usage

```
GPhoto Uploader.

Usage:
    gphoto-uploader upload --credentials=FILE [--file=FILE] [--verbose]
    gphoto-uploader (-h | --help)
    gphoto-uploader --version

Options:
    --file=FILE           CSV list of image or video files to upload. Default: read line-by-line from stdin.
    --credentials=FILE    Path to client-secrets.json file.
    -h --help             Show this screen.
    --version             Show version.
    --verbose             Enable verbose logging.
```

## Releasing

`./gradlew clean build release`
