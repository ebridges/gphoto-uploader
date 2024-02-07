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

Tag the `master` branch with a tag of the form `vX.Y.Z` where X, Y, & Z are integers from 0-99.  This will create a Github release and publish a standalone executable JAR file to the Github packages repository.

## Running

Requires OAuth2 Client credentials to be stored in a local file.  Be sure to choose an "OAuth Client ID" of type
"Other" or "Installed".  This application, on first run, will redirect to a webserver running on localhost port `8000` that
will receive the redirect and the authorization code. The authorization code will be used to generate the access token and 
grant authorization to the application.