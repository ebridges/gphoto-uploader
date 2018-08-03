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

## Running

Requires OAuth2 Client credentials to be stored in a local file.  Be sure to choose an "OAuth Client ID" of type
"Other" or "Installed".  This application, on first run, will redirect to a webserver running on localhost port `8000` that
will receive the redirect and the authorization code.  The code must be manually provided back to the CLI.

You will need to run a webserver on localhost port `8000` in order to receive this redirect when using this tool.

For example:

```
$ python3 -m http.server
```
