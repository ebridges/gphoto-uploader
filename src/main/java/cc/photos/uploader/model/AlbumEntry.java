package cc.photos.uploader.model;

import com.google.photos.types.proto.Album;

import java.io.File;

public record AlbumEntry(Album album, File mediaPath) {
}
