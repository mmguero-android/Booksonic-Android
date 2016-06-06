/*
	This file is part of Subsonic.

	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.

	Copyright 2015 (C) Scott Jackson
*/
package github.popeen.dsub.util.compat;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.media.MediaRouter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import github.popeen.dsub.R;
import github.popeen.dsub.activity.SubsonicActivity;
import github.popeen.dsub.activity.SubsonicFragmentActivity;
import github.popeen.dsub.domain.Bookmark;
import github.popeen.dsub.domain.MusicDirectory;
import github.popeen.dsub.domain.MusicDirectory.Entry;
import github.popeen.dsub.domain.Playlist;
import github.popeen.dsub.domain.SearchCritera;
import github.popeen.dsub.domain.SearchResult;
import github.popeen.dsub.service.DownloadFile;
import github.popeen.dsub.service.DownloadService;
import github.popeen.dsub.service.MusicService;
import github.popeen.dsub.util.Constants;
import github.popeen.dsub.util.ImageLoader;
import github.popeen.dsub.util.SilentServiceTask;
import github.popeen.dsub.util.Util;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RemoteControlClientLP extends RemoteControlClientBase {
	private static final String TAG = RemoteControlClientLP.class.getSimpleName();
	private static final String CUSTOM_ACTION_THUMBS_UP = "github.popeen.dsub.THUMBS_UP";
	private static final String CUSTOM_ACTION_THUMBS_DOWN = "github.popeen.dsub.THUMBS_DOWN";
	private static final String CUSTOM_ACTION_STAR = "github.popeen.dsub.STAR";
	// Copied from MediaControlConstants so I did not have to include the entire Wear SDK just for these constant
	private static final String SHOW_ON_WEAR = "android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR";
	private static final String WEAR_RESERVE_SKIP_TO_NEXT = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT";
	private static final String WEAR_RESERVE_SKIP_TO_PREVIOUS = "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS";
	private static final String WEAR_BACKGROUND_THEME = "android.support.wearable.media.extra.BACKGROUND_COLOR_FROM_THEME";
	// These constants don't seem to exist anywhere in the SDK.  Grabbed from Google's sample media player app
	private static final String AUTO_RESERVE_SKIP_TO_NEXT = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT";
	private static final String AUTO_RESERVE_SKIP_TO_PREVIOUS = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS";

	protected MediaSession mediaSession;
	protected DownloadService downloadService;
	protected ImageLoader imageLoader;
	protected List<DownloadFile> currentQueue;
	protected int previousState;

	@Override
	public void register(Context context, ComponentName mediaButtonReceiverComponent) {
		downloadService = (DownloadService) context;
		mediaSession = new MediaSession(downloadService, "DSub MediaSession");

		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(mediaButtonReceiverComponent);
		PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, mediaButtonIntent, 0);
		mediaSession.setMediaButtonReceiver(mediaPendingIntent);

		Intent activityIntent = new Intent(context, SubsonicFragmentActivity.class);
		activityIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD, true);
		activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent activityPendingIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);
		mediaSession.setSessionActivity(activityPendingIntent);

		mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
		mediaSession.setCallback(new EventCallback());

		AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder();
		audioAttributesBuilder.setUsage(AudioAttributes.USAGE_MEDIA)
			.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
		mediaSession.setPlaybackToLocal(audioAttributesBuilder.build());
		mediaSession.setActive(true);

		Bundle sessionExtras = new Bundle();
		sessionExtras.putBoolean(WEAR_BACKGROUND_THEME, true);
		sessionExtras.putBoolean(WEAR_RESERVE_SKIP_TO_PREVIOUS, true);
		sessionExtras.putBoolean(WEAR_RESERVE_SKIP_TO_NEXT, true);
		sessionExtras.putBoolean(AUTO_RESERVE_SKIP_TO_PREVIOUS, true);
		sessionExtras.putBoolean(AUTO_RESERVE_SKIP_TO_NEXT, true);
		mediaSession.setExtras(sessionExtras);

		imageLoader = SubsonicActivity.getStaticImageLoader(context);
	}

	@Override
	public void unregister(Context context) {
		mediaSession.release();
	}

	@Override
	public void setPlaybackState(int state) {
		PlaybackState.Builder builder = new PlaybackState.Builder();

		int newState = PlaybackState.STATE_NONE;
		switch(state) {
			case RemoteControlClient.PLAYSTATE_PLAYING:
				newState = PlaybackState.STATE_PLAYING;
				break;
			case RemoteControlClient.PLAYSTATE_STOPPED:
				newState = PlaybackState.STATE_STOPPED;
				break;
			case RemoteControlClient.PLAYSTATE_PAUSED:
				newState = PlaybackState.STATE_PAUSED;
				break;
			case RemoteControlClient.PLAYSTATE_BUFFERING:
				newState = PlaybackState.STATE_BUFFERING;
				break;
		}

		long position = -1;
		if(state == RemoteControlClient.PLAYSTATE_PLAYING || state == RemoteControlClient.PLAYSTATE_PAUSED) {
			position = downloadService.getPlayerPosition();
		}
		builder.setState(newState, position, 1.0f);
		builder.setActions(getPlaybackActions());

		DownloadFile downloadFile = downloadService.getCurrentPlaying();
		if(downloadFile != null) {
			MusicDirectory.Entry entry = downloadFile.getSong();
			addCustomActions(entry, builder);
			builder.setActiveQueueItemId(entry.getId().hashCode());
		}

		PlaybackState playbackState = builder.build();
		mediaSession.setPlaybackState(playbackState);
		previousState = state;
	}

	@Override
	public void updateMetadata(Context context, MusicDirectory.Entry currentSong) {
		setMetadata(currentSong, null);

		if(currentSong != null && imageLoader != null) {
			imageLoader.loadImage(context, this, currentSong);
		}
	}

	@Override
	public void metadataChanged(MusicDirectory.Entry currentSong) {
		setPlaybackState(previousState);
	}

	public void setMetadata(MusicDirectory.Entry currentSong, Bitmap bitmap) {
		MediaMetadata.Builder builder = new MediaMetadata.Builder();
		builder.putString(MediaMetadata.METADATA_KEY_ARTIST, (currentSong == null) ? null : currentSong.getArtist())
				.putString(MediaMetadata.METADATA_KEY_ALBUM, (currentSong == null) ? null : currentSong.getAlbum())
				.putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, (currentSong == null) ? null : currentSong.getArtist())
				.putString(MediaMetadata.METADATA_KEY_TITLE, (currentSong) == null ? null : currentSong.getTitle())
				.putString(MediaMetadata.METADATA_KEY_GENRE, (currentSong) == null ? null : currentSong.getGenre())
				.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (currentSong == null) ?
						0 : ((currentSong.getTrack() == null) ? 0 : currentSong.getTrack()))
				.putLong(MediaMetadata.METADATA_KEY_DURATION, (currentSong == null) ?
						0 : ((currentSong.getDuration() == null) ? 0 : (currentSong.getDuration() * 1000)));

		if(bitmap != null) {
			builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
		}

		mediaSession.setMetadata(builder.build());
	}

	@Override

	public void updateAlbumArt(Entry currentSong, Bitmap bitmap) {
		setMetadata(currentSong, bitmap);
	}

	@Override
	public void registerRoute(MediaRouter router) {
		router.setMediaSession(mediaSession);
	}

	@Override
	public void unregisterRoute(MediaRouter router) {
		router.setMediaSession(null);
	}

	@Override
	public void updatePlaylist(List<DownloadFile> playlist) {
		List<MediaSession.QueueItem> queue = new ArrayList<>();

		for(DownloadFile file: playlist) {
			Entry entry = file.getSong();

			MediaDescription description = new MediaDescription.Builder()
					.setMediaId(entry.getId())
					.setTitle(entry.getTitle())
					.setSubtitle(entry.getAlbumDisplay())
					.build();
			MediaSession.QueueItem item = new MediaSession.QueueItem(description, entry.getId().hashCode());
			queue.add(item);
		}

		mediaSession.setQueue(queue);
		currentQueue = playlist;
	}

	public MediaSession getMediaSession() {
		return mediaSession;
	}

	protected long getPlaybackActions() {
		long actions = PlaybackState.ACTION_PLAY |
				PlaybackState.ACTION_PAUSE |
				PlaybackState.ACTION_SEEK_TO |
				PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;

		int currentIndex = downloadService.getCurrentPlayingIndex();
		int size = downloadService.size();
		if(currentIndex > 0) {
			actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
		}
		if(currentIndex < size - 1) {
			actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
		}

		return actions;
	}
	protected void addCustomActions(Entry currentSong, PlaybackState.Builder builder) {
		Bundle showOnWearExtras = new Bundle();
		showOnWearExtras.putBoolean(SHOW_ON_WEAR, true);

		int rating = currentSong.getRating();
		PlaybackState.CustomAction thumbsUp = new PlaybackState.CustomAction.Builder(CUSTOM_ACTION_THUMBS_UP,
					downloadService.getString(R.string.download_thumbs_up),
					rating == 5 ? R.drawable.ic_action_rating_good_selected : R.drawable.ic_action_rating_good)
				.setExtras(showOnWearExtras).build();

		PlaybackState.CustomAction thumbsDown = new PlaybackState.CustomAction.Builder(CUSTOM_ACTION_THUMBS_DOWN,
					downloadService.getString(R.string.download_thumbs_down),
					rating == 1 ? R.drawable.ic_action_rating_bad_selected : R.drawable.ic_action_rating_bad)
				.setExtras(showOnWearExtras).build();

		PlaybackState.CustomAction star = new PlaybackState.CustomAction.Builder(CUSTOM_ACTION_STAR,
					downloadService.getString(R.string.common_star),
					currentSong.isStarred() ? R.drawable.ic_toggle_star : R.drawable.ic_toggle_star_outline)
				.setExtras(showOnWearExtras).build();

		builder.addCustomAction(thumbsDown).addCustomAction(star).addCustomAction(thumbsUp);
	}

	private void searchPlaylist(final String name) {
		new SilentServiceTask<Void>(downloadService) {
			@Override
			protected Void doInBackground(MusicService musicService) throws Throwable {
				List<Playlist> playlists = musicService.getPlaylists(false, downloadService, null);
				for(Playlist playlist: playlists) {
					if(playlist.getName().equals(name)) {
						getPlaylist(playlist);
						return null;
					}
				}

				noResults();
				return null;
			}

			private void getPlaylist(Playlist playlist) throws Exception {
				MusicDirectory musicDirectory = musicService.getPlaylist(false, playlist.getId(), playlist.getName(), downloadService, null);
				playSongs(musicDirectory.getChildren());
			}
		}.execute();
	}
	private void searchCriteria(final SearchCritera searchCritera) {
		new SilentServiceTask<Void>(downloadService) {
			@Override
			protected Void doInBackground(MusicService musicService) throws Throwable {
				SearchResult results = musicService.search(searchCritera, downloadService, null);

				if(results.hasArtists()) {
					playFromParent(new Entry(results.getArtists().get(0)));
				} else if(results.hasAlbums()) {
					playFromParent(results.getAlbums().get(0));
				} else if(results.hasSongs()) {
					playSong(results.getSongs().get(0));
				} else {
					noResults();
				}

				return null;
			}
			
			private void playFromParent(Entry parent) throws Exception {
				List<Entry> songs = new ArrayList<>();
				getSongsRecursively(parent, songs);
				playSongs(songs);
			}
			private void getSongsRecursively(Entry parent, List<Entry> songs) throws Exception {
				MusicDirectory musicDirectory;
				if(Util.isTagBrowsing(downloadService) && !Util.isOffline(downloadService)) {
					musicDirectory = musicService.getAlbum(parent.getId(), parent.getTitle(), false, downloadService, this);
				} else {
					musicDirectory = musicService.getMusicDirectory(parent.getId(), parent.getTitle(), false, downloadService, this);
				}

				for (Entry dir : musicDirectory.getChildren(true, false)) {
					if (dir.getRating() == 1) {
						continue;
					}

					getSongsRecursively(dir, songs);
				}

				for (Entry song : musicDirectory.getChildren(false, true)) {
					if (!song.isVideo() && song.getRating() != 1) {
						songs.add(song);
					}
				}
			}
		}.execute();
	}

	private void playPlaylist(final Playlist playlist, final boolean shuffle, final boolean append) {
		new SilentServiceTask<Void>(downloadService) {
			@Override
			protected Void doInBackground(MusicService musicService) throws Throwable {
				MusicDirectory musicDirectory = musicService.getPlaylist(false, playlist.getId(), playlist.getName(), downloadService, null);
				playSongs(musicDirectory.getChildren(), shuffle, append);

				return null;
			}
		}.execute();
	}
	private void playMusicDirectory(Entry dir, boolean shuffle, boolean append, boolean playFromBookmark) {
		playMusicDirectory(dir.getId(), shuffle, append, playFromBookmark);
	}
	private void playMusicDirectory(final String dirId, final boolean shuffle, final boolean append, final boolean playFromBookmark) {
		new SilentServiceTask<Void>(downloadService) {
			@Override
			protected Void doInBackground(MusicService musicService) throws Throwable {
				MusicDirectory musicDirectory;
				if(Util.isTagBrowsing(downloadService) && !Util.isOffline(downloadService)) {
					musicDirectory = musicService.getAlbum(dirId, "dir", false, downloadService, null);
				} else {
					musicDirectory = musicService.getMusicDirectory(dirId, "dir", false, downloadService, null);
				}

				List<Entry> playEntries = new ArrayList<>();
				List<Entry> allEntries = musicDirectory.getChildren(false, true);
				for(Entry song: allEntries) {
					if (!song.isVideo() && song.getRating() != 1) {
						playEntries.add(song);
					}
				}
				playSongs(playEntries, shuffle, append, playFromBookmark);

				return null;
			}
		}.execute();
	}

	private void playSong(Entry entry) {

	}
	private void playSong(Entry entry, boolean resumeFromBookmark) {
		List<Entry> entries = new ArrayList<>();
		entries.add(entry);
		playSongs(entries, false, false, resumeFromBookmark);
	}
	private void playSongs(List<Entry> entries) {
		playSongs(entries, false, false);
	}
	private void playSongs(List<Entry> entries, boolean shuffle, boolean append) {
		playSongs(entries, shuffle, append, false);
	}
	private void playSongs(List<Entry> entries, boolean shuffle, boolean append, boolean resumeFromBookmark) {
		if(!append) {
			downloadService.clear();
		}

		int startIndex = 0;
		int startPosition = 0;
		if(resumeFromBookmark) {
			int bookmarkIndex = 0;
			for(Entry entry: entries) {
				if(entry.getBookmark() != null) {
					Bookmark bookmark = entry.getBookmark();
					startIndex = bookmarkIndex;
					startPosition = bookmark.getPosition();
					break;
				}
				bookmarkIndex++;
			}
		}

		downloadService.download(entries, false, !append, false, shuffle, startIndex, startPosition);
	}

	private void noResults() {

	}

	private class EventCallback extends MediaSession.Callback {
		@Override
		public void onPlay() {
			downloadService.start();
		}

		@Override
		public void onStop() {
			downloadService.pause();
		}

		@Override
		public void onPause() {
			downloadService.pause();
		}

		@Override
		public void onSeekTo(long position) {
			downloadService.seekTo((int) position);
		}

		@Override
		public void onSkipToNext() {
			downloadService.next();
		}
		@Override
		public void onSkipToPrevious() {
			downloadService.previous();
		}

		@Override
		public void onSkipToQueueItem(long queueId) {
			if(currentQueue != null) {
				for(DownloadFile file: currentQueue) {
					if(file.getSong().getId().hashCode() == queueId) {
						downloadService.play(file);
						return;
					}
				}
			}
		}

		@Override
		public void onPlayFromSearch (String query, Bundle extras) {
			// User just asked to playing something
			if("".equals(query)) {
				downloadService.clear();
				downloadService.setShufflePlayEnabled(true);
			} else {
				String mediaFocus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS);


				// Play a specific playlist
				if (MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE.equals(mediaFocus)) {
					String playlist = extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST);
					searchPlaylist(playlist);
				}
				// Play a specific genre
				else if (MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE.equals(mediaFocus)) {
					String genre = extras.getString(MediaStore.EXTRA_MEDIA_GENRE);

					SharedPreferences.Editor editor = Util.getPreferences(downloadService).edit();
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, null);
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, null);
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, genre);
					editor.commit();

					downloadService.clear();
					downloadService.setShufflePlayEnabled(true);
				}
				else {
					int artists = 10;
					int albums = 10;
					int songs = 10;

					// Play a specific artist
					if (MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE.equals(mediaFocus)) {
						query = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST);
						albums = 0;
						songs = 0;
					}
					// Play a specific album
					else if (MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE.equals(mediaFocus)) {
						query = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM);
						artists = 0;
						songs = 0 ;
					}
					// Play a specific song
					else if (MediaStore.Audio.Media.ENTRY_CONTENT_TYPE.equals(mediaFocus)) {
						query = extras.getString(MediaStore.EXTRA_MEDIA_TITLE);
						artists = 0;
						albums = 0;
					}

					SearchCritera criteria = new SearchCritera(query, artists, albums, songs);
					searchCriteria(criteria);
				}
			}
		}

		@Override
		public void onPlayFromMediaId (String mediaId, Bundle extras) {
			if(extras == null) {
				return;
			}

			boolean shuffle = extras.getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false);
			boolean playLast = extras.getBoolean(Constants.INTENT_EXTRA_PLAY_LAST, false);
			Entry entry = (Entry) extras.getSerializable(Constants.INTENT_EXTRA_ENTRY);

			String playlistId = extras.getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID, null);
			if(playlistId != null) {
				Playlist playlist = new Playlist(playlistId, null);
				playPlaylist(playlist, shuffle, playLast);
			}
			String musicDirectoryId = extras.getString(Constants.INTENT_EXTRA_NAME_ID);
			if(musicDirectoryId != null) {
				Entry dir = new Entry(musicDirectoryId);
				playMusicDirectory(dir, shuffle, playLast, true);
			}

			String podcastId = extras.getString(Constants.INTENT_EXTRA_NAME_PODCAST_ID, null);
			if(podcastId != null) {
				playSong(entry, true);
			}

			// Currently only happens when playing bookmarks so we should be looking up parent
			String childId = extras.getString(Constants.INTENT_EXTRA_NAME_CHILD_ID, null);
			if(childId != null) {
				if(Util.isTagBrowsing(downloadService) && !Util.isOffline(downloadService)) {
					playMusicDirectory(entry.getAlbumId(), shuffle, playLast, true);
				} else {
					playMusicDirectory(entry.getParent(), shuffle, playLast, true);
				}
			}
		}

		@Override
		public void onCustomAction(String action, Bundle extras) {
			if(CUSTOM_ACTION_THUMBS_UP.equals(action)) {
				downloadService.toggleRating(5);
			} else if(CUSTOM_ACTION_THUMBS_DOWN.equals(action)) {
				downloadService.toggleRating(1);
			} else if(CUSTOM_ACTION_STAR.equals(action)) {
				downloadService.toggleStarred();
			}
		}
	}
}