CREATE TABLE artists (
    artist_id VARCHAR(22) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(255)
);

CREATE TABLE albums (
    album_id VARCHAR(22) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    cover_art VARCHAR(255)
);

CREATE TABLE album_artists (
    album_id VARCHAR(22) REFERENCES albums(album_id) ON DELETE CASCADE,
    artist_id VARCHAR(22) REFERENCES artists(artist_id) ON DELETE CASCADE,
    PRIMARY KEY (album_id, artist_id)
);

CREATE TABLE tracks (
    track_id VARCHAR(22) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    album_id VARCHAR(22) REFERENCES albums(album_id) ON DELETE SET NULL
);

CREATE TABLE users (
    discord_user_id SERIAL PRIMARY KEY,
    spotify_user_id VARCHAR(255) UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
);

CREATE TABLE track_streamings (
    streaming_id SERIAL PRIMARY KEY,
    discord_user_id INT REFERENCES users(discord_user_id) ON DELETE CASCADE,
    track_id VARCHAR(22) REFERENCES tracks(track_id) ON DELETE CASCADE,
    streamed_at TIMESTAMP NOT NULL,
    duration_ms INT NOT NULL
);