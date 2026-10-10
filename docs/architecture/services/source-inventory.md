# Inventario dei sorgenti per file

Stato: inventario baseline del 2026-10-10 (`cef023bc`), con destinazioni Hive
aggiornate da ADR-0017. I conteggi storici non includono i nuovi file core;
le altre destinazioni restano proposte. Le righe delle code riportano il
percorso attuale dopo il port, mantenendo il census originale come riferimento.

Elenco completo dei 424 file Java principali restituiti da CodeGraph aggiornato.
I percorsi sono quelli attuali; nessun file è stato spostato. Destinazioni e
azioni sono una proposta di ownership per famiglia, non una verifica funzionale
di ogni metodo. `Da qualificare` richiede un consumatore o una decisione prima
di condividere il tipo. `Comune DTO + split` condivide la forma finale, non
i metodi di calcolo/persistenza oggi presenti nello stesso file.

Le famiglie e gli accoppiamenti verificati sono in [inventory.md](inventory.md);
i criteri dei contratti in [shared-contracts.md](shared-contracts.md).
Il dettaglio degli import è una scansione testuale, non una call graph;
riferimenti qualificati, campi generici e classi annidate richiedono review
mirata con CodeGraph prima del port. Test e materiale storico sono descritti
nell'inventario per area, non sono destinati al comune per default.

## App.java — 1 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [App.java](../../../src/main/java/com/safjnest/App.java) | Da separare | Tre bootstrap e lifecycle indipendenti |

## commands — 131 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [commands/audio/Connect.java](../../../src/main/java/com/safjnest/commands/audio/Connect.java) | Bbot | Comando del dominio bot |
| [commands/audio/Disconnect.java](../../../src/main/java/com/safjnest/commands/audio/Disconnect.java) | Bbot | Comando del dominio bot |
| [commands/audio/Stop.java](../../../src/main/java/com/safjnest/commands/audio/Stop.java) | Bbot | Comando del dominio bot |
| [commands/audio/TTS.java](../../../src/main/java/com/safjnest/commands/audio/TTS.java) | Bbot | Comando del dominio bot |
| [commands/audio/greet/Greet.java](../../../src/main/java/com/safjnest/commands/audio/greet/Greet.java) | Bbot | Comando del dominio bot |
| [commands/audio/greet/GreetSet.java](../../../src/main/java/com/safjnest/commands/audio/greet/GreetSet.java) | Bbot | Comando del dominio bot |
| [commands/audio/greet/GreetUnset.java](../../../src/main/java/com/safjnest/commands/audio/greet/GreetUnset.java) | Bbot | Comando del dominio bot |
| [commands/audio/greet/GreetView.java](../../../src/main/java/com/safjnest/commands/audio/greet/GreetView.java) | Bbot | Comando del dominio bot |
| [commands/audio/list/List.java](../../../src/main/java/com/safjnest/commands/audio/list/List.java) | Bbot | Comando del dominio bot |
| [commands/audio/list/ListGuild.java](../../../src/main/java/com/safjnest/commands/audio/list/ListGuild.java) | Bbot | Comando del dominio bot |
| [commands/audio/list/ListUser.java](../../../src/main/java/com/safjnest/commands/audio/list/ListUser.java) | Bbot | Comando del dominio bot |
| [commands/audio/play/Play.java](../../../src/main/java/com/safjnest/commands/audio/play/Play.java) | Bbot | Comando del dominio bot |
| [commands/audio/play/PlaySound.java](../../../src/main/java/com/safjnest/commands/audio/play/PlaySound.java) | Bbot | Comando del dominio bot |
| [commands/audio/play/PlayYoutube.java](../../../src/main/java/com/safjnest/commands/audio/play/PlayYoutube.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/Playlist.java](../../../src/main/java/com/safjnest/commands/audio/playlist/Playlist.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistAddQueue.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistAddQueue.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistAddSong.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistAddSong.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistCreate.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistCreate.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistDelete.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistDelete.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistList.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistList.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistOrder.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistOrder.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistPlay.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistPlay.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistRemoveSong.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistRemoveSong.java) | Bbot | Comando del dominio bot |
| [commands/audio/playlist/PlaylistView.java](../../../src/main/java/com/safjnest/commands/audio/playlist/PlaylistView.java) | Bbot | Comando del dominio bot |
| [commands/audio/search/Search.java](../../../src/main/java/com/safjnest/commands/audio/search/Search.java) | Bbot | Comando del dominio bot |
| [commands/audio/search/SearchSound.java](../../../src/main/java/com/safjnest/commands/audio/search/SearchSound.java) | Bbot | Comando del dominio bot |
| [commands/audio/search/SearchYoutube.java](../../../src/main/java/com/safjnest/commands/audio/search/SearchYoutube.java) | Bbot | Comando del dominio bot |
| [commands/audio/sound/Sound.java](../../../src/main/java/com/safjnest/commands/audio/sound/Sound.java) | Bbot | Comando del dominio bot |
| [commands/audio/sound/SoundCustomize.java](../../../src/main/java/com/safjnest/commands/audio/sound/SoundCustomize.java) | Bbot | Comando del dominio bot |
| [commands/audio/sound/SoundDelete.java](../../../src/main/java/com/safjnest/commands/audio/sound/SoundDelete.java) | Bbot | Comando del dominio bot |
| [commands/audio/sound/SoundDownload.java](../../../src/main/java/com/safjnest/commands/audio/sound/SoundDownload.java) | Bbot | Comando del dominio bot |
| [commands/audio/sound/SoundUpload.java](../../../src/main/java/com/safjnest/commands/audio/sound/SoundUpload.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/Soundboard.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/Soundboard.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardAdd.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardAdd.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardCreate.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardCreate.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardDelete.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardDelete.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardPlay.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardPlay.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardRemove.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardRemove.java) | Bbot | Comando del dominio bot |
| [commands/audio/soundboard/SoundboardThumbnail.java](../../../src/main/java/com/safjnest/commands/audio/soundboard/SoundboardThumbnail.java) | Bbot | Comando del dominio bot |
| [commands/guild/ChannelInfo.java](../../../src/main/java/com/safjnest/commands/guild/ChannelInfo.java) | Bbot | Comando del dominio bot |
| [commands/guild/Clear.java](../../../src/main/java/com/safjnest/commands/guild/Clear.java) | Bbot | Comando del dominio bot |
| [commands/guild/EmojiInfo.java](../../../src/main/java/com/safjnest/commands/guild/EmojiInfo.java) | Bbot | Comando del dominio bot |
| [commands/guild/Leaderboard.java](../../../src/main/java/com/safjnest/commands/guild/Leaderboard.java) | Bbot | Comando del dominio bot |
| [commands/guild/MemberInfo.java](../../../src/main/java/com/safjnest/commands/guild/MemberInfo.java) | Bbot | Comando del dominio bot |
| [commands/guild/ServerInfo.java](../../../src/main/java/com/safjnest/commands/guild/ServerInfo.java) | Bbot | Comando del dominio bot |
| [commands/guild/Warn.java](../../../src/main/java/com/safjnest/commands/guild/Warn.java) | Bbot | Comando del dominio bot |
| [commands/lol/Augment.java](../../../src/main/java/com/safjnest/commands/lol/Augment.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Champion.java](../../../src/main/java/com/safjnest/commands/lol/Champion.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Champions.java](../../../src/main/java/com/safjnest/commands/lol/Champions.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Item.java](../../../src/main/java/com/safjnest/commands/lol/Item.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Livegame.java](../../../src/main/java/com/safjnest/commands/lol/Livegame.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Opgg.java](../../../src/main/java/com/safjnest/commands/lol/Opgg.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/Region.java](../../../src/main/java/com/safjnest/commands/lol/Region.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/UltimateBravery.java](../../../src/main/java/com/safjnest/commands/lol/UltimateBravery.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/Summoner.java](../../../src/main/java/com/safjnest/commands/lol/summoner/Summoner.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerChampion.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerChampion.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerLink.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerLink.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerOverview.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerOverview.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerProfile.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerProfile.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerTrack.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerTrack.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/lol/summoner/SummonerUnlink.java](../../../src/main/java/com/safjnest/commands/lol/summoner/SummonerUnlink.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/math/Calculator.java](../../../src/main/java/com/safjnest/commands/math/Calculator.java) | Bbot | Comando del dominio bot |
| [commands/math/Dice.java](../../../src/main/java/com/safjnest/commands/math/Dice.java) | Bbot | Comando del dominio bot |
| [commands/math/Prime.java](../../../src/main/java/com/safjnest/commands/math/Prime.java) | Bbot | Comando del dominio bot |
| [commands/members/Ban.java](../../../src/main/java/com/safjnest/commands/members/Ban.java) | Bbot | Comando del dominio bot |
| [commands/members/Blacklist.java](../../../src/main/java/com/safjnest/commands/members/Blacklist.java) | Bbot | Comando del dominio bot |
| [commands/members/Image.java](../../../src/main/java/com/safjnest/commands/members/Image.java) | Bbot | Comando del dominio bot |
| [commands/members/Kick.java](../../../src/main/java/com/safjnest/commands/members/Kick.java) | Bbot | Comando del dominio bot |
| [commands/members/Mute.java](../../../src/main/java/com/safjnest/commands/members/Mute.java) | Bbot | Comando del dominio bot |
| [commands/members/Nickname.java](../../../src/main/java/com/safjnest/commands/members/Nickname.java) | Bbot | Comando del dominio bot |
| [commands/members/Permissions.java](../../../src/main/java/com/safjnest/commands/members/Permissions.java) | Bbot | Comando del dominio bot |
| [commands/members/UnMute.java](../../../src/main/java/com/safjnest/commands/members/UnMute.java) | Bbot | Comando del dominio bot |
| [commands/members/Unban.java](../../../src/main/java/com/safjnest/commands/members/Unban.java) | Bbot | Comando del dominio bot |
| [commands/members/move/Move.java](../../../src/main/java/com/safjnest/commands/members/move/Move.java) | Bbot | Comando del dominio bot |
| [commands/members/move/MoveChannel.java](../../../src/main/java/com/safjnest/commands/members/move/MoveChannel.java) | Bbot | Comando del dominio bot |
| [commands/members/move/MoveUser.java](../../../src/main/java/com/safjnest/commands/members/move/MoveUser.java) | Bbot | Comando del dominio bot |
| [commands/misc/APOD.java](../../../src/main/java/com/safjnest/commands/misc/APOD.java) | Bbot | Comando del dominio bot |
| [commands/misc/Alias.java](../../../src/main/java/com/safjnest/commands/misc/Alias.java) | Bbot | Comando del dominio bot |
| [commands/misc/Bug.java](../../../src/main/java/com/safjnest/commands/misc/Bug.java) | Bbot | Comando del dominio bot |
| [commands/misc/Help.java](../../../src/main/java/com/safjnest/commands/misc/Help.java) | Bbot | Comando del dominio bot |
| [commands/misc/InviteBot.java](../../../src/main/java/com/safjnest/commands/misc/InviteBot.java) | Bbot | Comando del dominio bot |
| [commands/misc/Msg.java](../../../src/main/java/com/safjnest/commands/misc/Msg.java) | Bbot | Comando del dominio bot |
| [commands/misc/Ping.java](../../../src/main/java/com/safjnest/commands/misc/Ping.java) | Bbot | Comando del dominio bot |
| [commands/misc/QRCode.java](../../../src/main/java/com/safjnest/commands/misc/QRCode.java) | Bbot | Comando del dominio bot |
| [commands/misc/SpecialChar.java](../../../src/main/java/com/safjnest/commands/misc/SpecialChar.java) | Bbot | Comando del dominio bot |
| [commands/misc/Weather.java](../../../src/main/java/com/safjnest/commands/misc/Weather.java) | Bbot | Comando del dominio bot |
| [commands/misc/omegle/Omegle.java](../../../src/main/java/com/safjnest/commands/misc/omegle/Omegle.java) | Bbot | Comando del dominio bot |
| [commands/misc/omegle/OmegleConnect.java](../../../src/main/java/com/safjnest/commands/misc/omegle/OmegleConnect.java) | Bbot | Comando del dominio bot |
| [commands/misc/omegle/OmegleDisconnect.java](../../../src/main/java/com/safjnest/commands/misc/omegle/OmegleDisconnect.java) | Bbot | Comando del dominio bot |
| [commands/misc/omegle/OmegleStop.java](../../../src/main/java/com/safjnest/commands/misc/omegle/OmegleStop.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/Spotify.java](../../../src/main/java/com/safjnest/commands/misc/spotify/Spotify.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/SpotifyAlbums.java](../../../src/main/java/com/safjnest/commands/misc/spotify/SpotifyAlbums.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/SpotifyArtists.java](../../../src/main/java/com/safjnest/commands/misc/spotify/SpotifyArtists.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/SpotifyLink.java](../../../src/main/java/com/safjnest/commands/misc/spotify/SpotifyLink.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/SpotifyTracks.java](../../../src/main/java/com/safjnest/commands/misc/spotify/SpotifyTracks.java) | Bbot | Comando del dominio bot |
| [commands/misc/spotify/SpotifyUpload.java](../../../src/main/java/com/safjnest/commands/misc/spotify/SpotifyUpload.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/Twitch.java](../../../src/main/java/com/safjnest/commands/misc/twitch/Twitch.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/TwitchLink.java](../../../src/main/java/com/safjnest/commands/misc/twitch/TwitchLink.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/TwitchMenu.java](../../../src/main/java/com/safjnest/commands/misc/twitch/TwitchMenu.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/TwitchMessage.java](../../../src/main/java/com/safjnest/commands/misc/twitch/TwitchMessage.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/TwitchUnlink.java](../../../src/main/java/com/safjnest/commands/misc/twitch/TwitchUnlink.java) | Bbot | Comando del dominio bot |
| [commands/misc/twitch/TwitchUser.java](../../../src/main/java/com/safjnest/commands/misc/twitch/TwitchUser.java) | Bbot | Comando del dominio bot |
| [commands/owner/ChampionStats.java](../../../src/main/java/com/safjnest/commands/owner/ChampionStats.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Chat.java](../../../src/main/java/com/safjnest/commands/owner/Chat.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Jelly.java](../../../src/main/java/com/safjnest/commands/owner/Jelly.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/ListGuild.java](../../../src/main/java/com/safjnest/commands/owner/ListGuild.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/PrintCache.java](../../../src/main/java/com/safjnest/commands/owner/PrintCache.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Query.java](../../../src/main/java/com/safjnest/commands/owner/Query.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Ram.java](../../../src/main/java/com/safjnest/commands/owner/Ram.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/RandomMove.java](../../../src/main/java/com/safjnest/commands/owner/RandomMove.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Restart.java](../../../src/main/java/com/safjnest/commands/owner/Restart.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Shutdown.java](../../../src/main/java/com/safjnest/commands/owner/Shutdown.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/Test.java](../../../src/main/java/com/safjnest/commands/owner/Test.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/TrackerStatus.java](../../../src/main/java/com/safjnest/commands/owner/TrackerStatus.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/owner/VandalizeServer.java](../../../src/main/java/com/safjnest/commands/owner/VandalizeServer.java) | Bbot | Adapter comando; delegare LoL/maintenance al proprietario |
| [commands/queue/JumpTo.java](../../../src/main/java/com/safjnest/commands/queue/JumpTo.java) | Bbot | Comando del dominio bot |
| [commands/queue/Pause.java](../../../src/main/java/com/safjnest/commands/queue/Pause.java) | Bbot | Comando del dominio bot |
| [commands/queue/PlayYoutubeForce.java](../../../src/main/java/com/safjnest/commands/queue/PlayYoutubeForce.java) | Bbot | Comando del dominio bot |
| [commands/queue/Player.java](../../../src/main/java/com/safjnest/commands/queue/Player.java) | Bbot | Comando del dominio bot |
| [commands/queue/Previous.java](../../../src/main/java/com/safjnest/commands/queue/Previous.java) | Bbot | Comando del dominio bot |
| [commands/queue/Queue.java](../../../src/main/java/com/safjnest/commands/queue/Queue.java) | Bbot | Comando del dominio bot |
| [commands/queue/Resume.java](../../../src/main/java/com/safjnest/commands/queue/Resume.java) | Bbot | Comando del dominio bot |
| [commands/queue/Skip.java](../../../src/main/java/com/safjnest/commands/queue/Skip.java) | Bbot | Comando del dominio bot |
| [commands/settings/AutomatedAction.java](../../../src/main/java/com/safjnest/commands/settings/AutomatedAction.java) | Bbot | Comando del dominio bot |
| [commands/settings/Boost.java](../../../src/main/java/com/safjnest/commands/settings/Boost.java) | Bbot | Comando del dominio bot |
| [commands/settings/Leave.java](../../../src/main/java/com/safjnest/commands/settings/Leave.java) | Bbot | Comando del dominio bot |
| [commands/settings/LevelUp.java](../../../src/main/java/com/safjnest/commands/settings/LevelUp.java) | Bbot | Comando del dominio bot |
| [commands/settings/Prefix.java](../../../src/main/java/com/safjnest/commands/settings/Prefix.java) | Bbot | Comando del dominio bot |
| [commands/settings/Reward.java](../../../src/main/java/com/safjnest/commands/settings/Reward.java) | Bbot | Comando del dominio bot |
| [commands/settings/Voice.java](../../../src/main/java/com/safjnest/commands/settings/Voice.java) | Bbot | Comando del dominio bot |
| [commands/settings/Welcome.java](../../../src/main/java/com/safjnest/commands/settings/Welcome.java) | Bbot | Comando del dominio bot |

## core — 39 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [core/Bot.java](../../../src/main/java/com/safjnest/core/Bot.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/Chronos.java](../../../src/main/java/com/safjnest/core/Chronos.java) | Da qualificare | Scheduling periodico: istanze locali; riuso solo della parte pura |
| [core/audio/AudioForwarder.java](../../../src/main/java/com/safjnest/core/audio/AudioForwarder.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/ByteArraySeekableInputStream.java](../../../src/main/java/com/safjnest/core/audio/ByteArraySeekableInputStream.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/GuildMusicManager.java](../../../src/main/java/com/safjnest/core/audio/GuildMusicManager.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/PlayerManager.java](../../../src/main/java/com/safjnest/core/audio/PlayerManager.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/QueueHandler.java](../../../src/main/java/com/safjnest/core/audio/QueueHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/QueueMessage.java](../../../src/main/java/com/safjnest/core/audio/QueueMessage.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/ResultHandler.java](../../../src/main/java/com/safjnest/core/audio/ResultHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/SafjAudioPlaylist.java](../../../src/main/java/com/safjnest/core/audio/SafjAudioPlaylist.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/SoundAnalytics.java](../../../src/main/java/com/safjnest/core/audio/SoundAnalytics.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/SoundEmbed.java](../../../src/main/java/com/safjnest/core/audio/SoundEmbed.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/TrackData.java](../../../src/main/java/com/safjnest/core/audio/TrackData.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/TrackScheduler.java](../../../src/main/java/com/safjnest/core/audio/TrackScheduler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/tts/TTSHandler.java](../../../src/main/java/com/safjnest/core/audio/tts/TTSHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/tts/TTSVoices.java](../../../src/main/java/com/safjnest/core/audio/tts/TTSVoices.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/types/AudioType.java](../../../src/main/java/com/safjnest/core/audio/types/AudioType.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/types/EmbedType.java](../../../src/main/java/com/safjnest/core/audio/types/EmbedType.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/types/PlayTiming.java](../../../src/main/java/com/safjnest/core/audio/types/PlayTiming.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/audio/types/ReplyType.java](../../../src/main/java/com/safjnest/core/audio/types/ReplyType.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/AbstractCache.java](../../../src/main/java/com/safjnest/core/cache/AbstractCache.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/CacheAdapter.java](../../../src/main/java/com/safjnest/core/cache/CacheAdapter.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/managers/GenericCache.java](../../../src/main/java/com/safjnest/core/cache/managers/GenericCache.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/managers/GuildCache.java](../../../src/main/java/com/safjnest/core/cache/managers/GuildCache.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/managers/SoundCache.java](../../../src/main/java/com/safjnest/core/cache/managers/SoundCache.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/cache/managers/UserCache.java](../../../src/main/java/com/safjnest/core/cache/managers/UserCache.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/chat/ChatHandler.java](../../../src/main/java/com/safjnest/core/chat/ChatHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/chat/OmegleChannel.java](../../../src/main/java/com/safjnest/core/chat/OmegleChannel.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/CommandEventHandler.java](../../../src/main/java/com/safjnest/core/events/CommandEventHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventAutoCompleteInteractionHandler.java](../../../src/main/java/com/safjnest/core/events/EventAutoCompleteInteractionHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventButtonHandler.java](../../../src/main/java/com/safjnest/core/events/EventButtonHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventComponentsHandler.java](../../../src/main/java/com/safjnest/core/events/EventComponentsHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventHandler.java](../../../src/main/java/com/safjnest/core/events/EventHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventHandlerBeebot.java](../../../src/main/java/com/safjnest/core/events/EventHandlerBeebot.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventModalInteractionHandler.java](../../../src/main/java/com/safjnest/core/events/EventModalInteractionHandler.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/EventUtils.java](../../../src/main/java/com/safjnest/core/events/EventUtils.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/Functions.java](../../../src/main/java/com/safjnest/core/events/Functions.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/interfaces/BeebotListenerAdapter.java](../../../src/main/java/com/safjnest/core/events/interfaces/BeebotListenerAdapter.java) | Bbot | Discord/cache/audio/eventi; non core job comune |
| [core/events/types/WarningEvent.java](../../../src/main/java/com/safjnest/core/events/types/WarningEvent.java) | Bbot | Discord/cache/audio/eventi; non core job comune |

## lol — 146 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [lol/LeagueHandler.java](../../../src/main/java/com/safjnest/lol/LeagueHandler.java) | Da separare | Bot/Riot/statici/Mongo mescolati; escluso dal comune |
| [lol/champion/BuildSignature.java](../../../src/main/java/com/safjnest/lol/champion/BuildSignature.java) | Spring | Provider/record/firma di calcolo build o statistiche |
| [lol/champion/ChampionBuildData.java](../../../src/main/java/com/safjnest/lol/champion/ChampionBuildData.java) | Spring | Provider/record/firma di calcolo build o statistiche |
| [lol/champion/ChampionBuildProvider.java](../../../src/main/java/com/safjnest/lol/champion/ChampionBuildProvider.java) | Spring | Provider/record/firma di calcolo build o statistiche |
| [lol/champion/ChampionStatsData.java](../../../src/main/java/com/safjnest/lol/champion/ChampionStatsData.java) | Spring | Provider/record/firma di calcolo build o statistiche |
| [lol/champion/ChampionStatsProvider.java](../../../src/main/java/com/safjnest/lol/champion/ChampionStatsProvider.java) | Spring | Provider/record/firma di calcolo build o statistiche |
| [lol/champion/RuneSignature.java](../../../src/main/java/com/safjnest/lol/champion/RuneSignature.java) | Comune DTO + split | Forma rune esposta da Build/LiveGame; parsing/key nel produttore |
| [lol/message/LeagueEventHandler.java](../../../src/main/java/com/safjnest/lol/message/LeagueEventHandler.java) | Bbot | Presentazione/adapter Discord invariato |
| [lol/message/LeagueMessage.java](../../../src/main/java/com/safjnest/lol/message/LeagueMessage.java) | Bbot | Presentazione/adapter Discord invariato |
| [lol/message/LeagueMessageParameter.java](../../../src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java) | Bbot | Presentazione/adapter Discord invariato |
| [lol/message/LeagueMessageType.java](../../../src/main/java/com/safjnest/lol/message/LeagueMessageType.java) | Bbot | Presentazione/adapter Discord invariato |
| [lol/model/ActivityFilter.java](../../../src/main/java/com/safjnest/lol/model/ActivityFilter.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/ApiResult.java](../../../src/main/java/com/safjnest/lol/model/ApiResult.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/Augment.java](../../../src/main/java/com/safjnest/lol/model/Augment.java) | Da qualificare | Dati statici/bot: contratto solo se scambiato tra servizi |
| [lol/model/Build.java](../../../src/main/java/com/safjnest/lol/model/Build.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/ChampionIndexable.java](../../../src/main/java/com/safjnest/lol/model/ChampionIndexable.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/ChampionStatistics.java](../../../src/main/java/com/safjnest/lol/model/ChampionStatistics.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/ChampionTierList.java](../../../src/main/java/com/safjnest/lol/model/ChampionTierList.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/ChampionTierSource.java](../../../src/main/java/com/safjnest/lol/model/ChampionTierSource.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/ChampionView.java](../../../src/main/java/com/safjnest/lol/model/ChampionView.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/Filter.java](../../../src/main/java/com/safjnest/lol/model/Filter.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/PlayerChampionStats.java](../../../src/main/java/com/safjnest/lol/model/PlayerChampionStats.java) | Bbot | Aggregato usato per presentazione bot; qualificare calcolo remoto |
| [lol/model/ProfileIndexable.java](../../../src/main/java/com/safjnest/lol/model/ProfileIndexable.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/ResponseMetadata.java](../../../src/main/java/com/safjnest/lol/model/ResponseMetadata.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/competitive/CompetitiveEntry.java](../../../src/main/java/com/safjnest/lol/model/competitive/CompetitiveEntry.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/leaderboard/LeaderboardDistribution.java](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardDistribution.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/leaderboard/LeaderboardPage.java](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardPage.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/LiveGame.java](../../../src/main/java/com/safjnest/lol/model/match/LiveGame.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/match/Match.java](../../../src/main/java/com/safjnest/lol/model/match/Match.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/match/MatchOrder.java](../../../src/main/java/com/safjnest/lol/model/match/MatchOrder.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/MatchPage.java](../../../src/main/java/com/safjnest/lol/model/match/MatchPage.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/MatchResult.java](../../../src/main/java/com/safjnest/lol/model/match/MatchResult.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/Participant.java](../../../src/main/java/com/safjnest/lol/model/match/Participant.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/RankHistory.java](../../../src/main/java/com/safjnest/lol/model/match/RankHistory.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/RankHistoryMatch.java](../../../src/main/java/com/safjnest/lol/model/match/RankHistoryMatch.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/match/RankHistoryMetadata.java](../../../src/main/java/com/safjnest/lol/model/match/RankHistoryMetadata.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/RankHistoryQuery.java](../../../src/main/java/com/safjnest/lol/model/match/RankHistoryQuery.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/RankHistoryView.java](../../../src/main/java/com/safjnest/lol/model/match/RankHistoryView.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/match/RankProgress.java](../../../src/main/java/com/safjnest/lol/model/match/RankProgress.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/record/ProfileRecord.java](../../../src/main/java/com/safjnest/lol/model/record/ProfileRecord.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/record/ProfileRecordPage.java](../../../src/main/java/com/safjnest/lol/model/record/ProfileRecordPage.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/record/RecordMetric.java](../../../src/main/java/com/safjnest/lol/model/record/RecordMetric.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/record/RecordOrder.java](../../../src/main/java/com/safjnest/lol/model/record/RecordOrder.java) | Da qualificare | Parametro interno: comune solo se richiesto dal confine |
| [lol/model/record/RecordOwner.java](../../../src/main/java/com/safjnest/lol/model/record/RecordOwner.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/record/RecordPage.java](../../../src/main/java/com/safjnest/lol/model/record/RecordPage.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/record/RecordsOverview.java](../../../src/main/java/com/safjnest/lol/model/record/RecordsOverview.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/rune/PageRunes.java](../../../src/main/java/com/safjnest/lol/model/rune/PageRunes.java) | Da qualificare | Dati statici/bot: contratto solo se scambiato tra servizi |
| [lol/model/rune/Rune.java](../../../src/main/java/com/safjnest/lol/model/rune/Rune.java) | Da qualificare | Dati statici/bot: contratto solo se scambiato tra servizi |
| [lol/model/statistics/CanonicalQueue.java](../../../src/main/java/com/safjnest/lol/model/statistics/CanonicalQueue.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/statistics/ChampionStatsDocument.java](../../../src/main/java/com/safjnest/lol/model/statistics/ChampionStatsDocument.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/ProfileActivity.java](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileActivity.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/statistics/ProfileMatchupLeaf.java](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileMatchupLeaf.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/statistics/ProfileMatchups.java](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileMatchups.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/statistics/ProfileStatistics.java](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/statistics/ProfileStatisticsRow.java](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileStatisticsRow.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/ChampionLeafStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/ChampionLeafStats.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/ChampionNode.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/ChampionNode.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/ChampionStatsScope.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/ChampionStatsScope.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/LeafStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/LeafStats.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/statistics/shared/MatchupStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/MatchupStats.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/ProfileLeafStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/ProfileLeafStats.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/statistics/shared/TrendStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/TrendStats.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/statistics/shared/WinLossStats.java](../../../src/main/java/com/safjnest/lol/model/statistics/shared/WinLossStats.java) | Spring | Storage/intermedio/calcolo; non DTO finale comune |
| [lol/model/status/BotStatus.java](../../../src/main/java/com/safjnest/lol/model/status/BotStatus.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [com/hive/status/JobProgress.java](../../../src/main/java/com/hive/status/JobProgress.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [com/hive/status/JobStatus.java](../../../src/main/java/com/hive/status/JobStatus.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [lol/model/status/JvmMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/JvmMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/LeagueMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/LeagueMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoCollectionPerformance.java](../../../src/main/java/com/safjnest/lol/model/status/MongoCollectionPerformance.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoHottestCollection.java](../../../src/main/java/com/safjnest/lol/model/status/MongoHottestCollection.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoMemoryMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/MongoMemoryMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/MongoMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoOperationRates.java](../../../src/main/java/com/safjnest/lol/model/status/MongoOperationRates.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoOperationSample.java](../../../src/main/java/com/safjnest/lol/model/status/MongoOperationSample.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoOperationsMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/MongoOperationsMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoPerformanceMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/MongoPerformanceMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/status/MongoSlowOperation.java](../../../src/main/java/com/safjnest/lol/model/status/MongoSlowOperation.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [com/hive/status/QueueStatus.java](../../../src/main/java/com/hive/status/QueueStatus.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [lol/model/status/RedisMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/RedisMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [com/hive/status/RunStatus.java](../../../src/main/java/com/hive/status/RunStatus.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [com/hive/status/SchedulerStatus.java](../../../src/main/java/com/hive/status/SchedulerStatus.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [lol/model/status/SystemMetrics.java](../../../src/main/java/com/safjnest/lol/model/status/SystemMetrics.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [com/hive/status/WorkerStatus.java](../../../src/main/java/com/hive/status/WorkerStatus.java) | Hive implementato | Snapshot generico; proiezioni applicative locali, JSON invariato |
| [lol/model/summoner/Mastery.java](../../../src/main/java/com/safjnest/lol/model/summoner/Mastery.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/summoner/Rank.java](../../../src/main/java/com/safjnest/lol/model/summoner/Rank.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/summoner/Summoner.java](../../../src/main/java/com/safjnest/lol/model/summoner/Summoner.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/summoner/SummonerLeaderboard.java](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerLeaderboard.java) | Comune DTO candidato | Dati o parametro del contratto; verificare chiusura dei tipi |
| [lol/model/summoner/SummonerOverview.java](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerOverview.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/model/summoner/SummonerView.java](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java) | Comune DTO + split | Forma finale comune; isolare metodi operativi/SDK/lookup |
| [lol/queue/QueueHandler.java](../../../src/main/java/com/safjnest/lol/queue/QueueHandler.java) | Applicazione | Composizione e facade; delega a Hive, bootstrap esplicito |
| [com/hive/Registry.java](../../../src/main/java/com/hive/Registry.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/Router.java](../../../src/main/java/com/hive/Router.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/job/Job.java](../../../src/main/java/com/hive/job/Job.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/job/JobPriority.java](../../../src/main/java/com/hive/job/JobPriority.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/job/JobState.java](../../../src/main/java/com/hive/job/JobState.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/scheduler/AbstractScheduler.java](../../../src/main/java/com/hive/scheduler/AbstractScheduler.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [lol/scheduler/ChampionMatrixRequest.java](../../../src/main/java/com/safjnest/lol/scheduler/ChampionMatrixRequest.java) | Spring | Adapter/policy compute o Sync; non comune |
| [lol/scheduler/ComputeScheduler.java](../../../src/main/java/com/safjnest/lol/scheduler/ComputeScheduler.java) | Spring | Adapter/policy compute o Sync; non comune |
| [lol/scheduler/DatabaseWorkerType.java](../../../src/main/java/com/safjnest/lol/scheduler/DatabaseWorkerType.java) | Spring | Adapter/policy compute o Sync; non comune |
| [r4j/scheduler/RiotScheduler.java](../../../src/main/java/com/safjnest/r4j/scheduler/RiotScheduler.java) | R4J | Adapter routing e naming Riot sopra il core comune |
| [lol/scheduler/SyncScheduler.java](../../../src/main/java/com/safjnest/lol/scheduler/SyncScheduler.java) | Spring | Adapter/policy compute o Sync; non comune |
| [com/hive/worker/JobQueue.java](../../../src/main/java/com/hive/worker/JobQueue.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/worker/JobWorker.java](../../../src/main/java/com/hive/worker/JobWorker.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [com/hive/worker/WorkerState.java](../../../src/main/java/com/hive/worker/WorkerState.java) | Hive implementato | Meccanismo generico JDK-only per istanza (ADR-0017) |
| [lol/service/CacheInvalidationService.java](../../../src/main/java/com/safjnest/lol/service/CacheInvalidationService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ChampionAnalyzer.java](../../../src/main/java/com/safjnest/lol/service/ChampionAnalyzer.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ChampionBuildEngine.java](../../../src/main/java/com/safjnest/lol/service/ChampionBuildEngine.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ChampionService.java](../../../src/main/java/com/safjnest/lol/service/ChampionService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ChampionTierAnalyzer.java](../../../src/main/java/com/safjnest/lol/service/ChampionTierAnalyzer.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/CompetitiveService.java](../../../src/main/java/com/safjnest/lol/service/CompetitiveService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/LeaderboardService.java](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/MasteryService.java](../../../src/main/java/com/safjnest/lol/service/MasteryService.java) | Spring + split R4J | Writer/read-through dominio in Spring; acquisizione SDK in R4J |
| [lol/service/MatchService.java](../../../src/main/java/com/safjnest/lol/service/MatchService.java) | Spring + split R4J | Writer/read-through dominio in Spring; acquisizione SDK in R4J |
| [lol/service/MatchupRankingAnalyzer.java](../../../src/main/java/com/safjnest/lol/service/MatchupRankingAnalyzer.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ProfileAnalyzer.java](../../../src/main/java/com/safjnest/lol/service/ProfileAnalyzer.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ProfileRecordAnalyzer.java](../../../src/main/java/com/safjnest/lol/service/ProfileRecordAnalyzer.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ProfileRecordService.java](../../../src/main/java/com/safjnest/lol/service/ProfileRecordService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/ProfileService.java](../../../src/main/java/com/safjnest/lol/service/ProfileService.java) | Spring | Dominio/calcolo/persistenza/proiezione o invalidazione LoL |
| [lol/service/RankService.java](../../../src/main/java/com/safjnest/lol/service/RankService.java) | Spring + split R4J | Writer/read-through dominio in Spring; acquisizione SDK in R4J |
| [lol/service/StaticDataService.java](../../../src/main/java/com/safjnest/lol/service/StaticDataService.java) | R4J + client | Acquisizione/cache statica in R4J; consumatori tramite contratto |
| [lol/service/SummonerService.java](../../../src/main/java/com/safjnest/lol/service/SummonerService.java) | Spring + split R4J | Writer/read-through dominio in Spring; acquisizione SDK in R4J |
| [lol/tracker/Tracker.java](../../../src/main/java/com/safjnest/lol/tracker/Tracker.java) | Spring | Tracking/completamento dati; fetch mediante client R4J |
| [lol/tracker/TrackerScheduler.java](../../../src/main/java/com/safjnest/lol/tracker/TrackerScheduler.java) | Spring | Tracking/completamento dati; fetch mediante client R4J |
| [lol/utils/AugmentUtils.java](../../../src/main/java/com/safjnest/lol/utils/AugmentUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/BuildUtils.java](../../../src/main/java/com/safjnest/lol/utils/BuildUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/ChampionBuildTimelineUtils.java](../../../src/main/java/com/safjnest/lol/utils/ChampionBuildTimelineUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/ChampionUtils.java](../../../src/main/java/com/safjnest/lol/utils/ChampionUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/FilterCodec.java](../../../src/main/java/com/safjnest/lol/utils/FilterCodec.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/GameQueueTypeUtils.java](../../../src/main/java/com/safjnest/lol/utils/GameQueueTypeUtils.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/ItemUtils.java](../../../src/main/java/com/safjnest/lol/utils/ItemUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/KdaUtils.java](../../../src/main/java/com/safjnest/lol/utils/KdaUtils.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/LaneTypeUtils.java](../../../src/main/java/com/safjnest/lol/utils/LaneTypeUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/LeagueConstants.java](../../../src/main/java/com/safjnest/lol/utils/LeagueConstants.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/LeagueMessageUtils.java](../../../src/main/java/com/safjnest/lol/utils/LeagueMessageUtils.java) | Bbot + split | Rendering bot; dipendenze dati da remotizzare |
| [lol/utils/LeagueShardUtils.java](../../../src/main/java/com/safjnest/lol/utils/LeagueShardUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/MatchMemoryUtils.java](../../../src/main/java/com/safjnest/lol/utils/MatchMemoryUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/MatchUtils.java](../../../src/main/java/com/safjnest/lol/utils/MatchUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/MatchupTimelineUtils.java](../../../src/main/java/com/safjnest/lol/utils/MatchupTimelineUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/NumberUtils.java](../../../src/main/java/com/safjnest/lol/utils/NumberUtils.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/OPTUtils.java](../../../src/main/java/com/safjnest/lol/utils/OPTUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/ParticipantBuildCodec.java](../../../src/main/java/com/safjnest/lol/utils/ParticipantBuildCodec.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/PatchUtils.java](../../../src/main/java/com/safjnest/lol/utils/PatchUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/RankProgressUtils.java](../../../src/main/java/com/safjnest/lol/utils/RankProgressUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |
| [lol/utils/SeasonUtils.java](../../../src/main/java/com/safjnest/lol/utils/SeasonUtils.java) | Da qualificare | Riuso delle sole parti pure richieste; nessun utils globale |
| [lol/utils/TierDivisionUtils.java](../../../src/main/java/com/safjnest/lol/utils/TierDivisionUtils.java) | Da separare | Semantica/calcolo, SDK/statici e presentazione mescolati |
| [lol/utils/TierMathUtils.java](../../../src/main/java/com/safjnest/lol/utils/TierMathUtils.java) | Spring | Calcolo/parser/normalizzazione/gestione memoria interni |

## model — 35 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [model/AliasData.java](../../../src/main/java/com/safjnest/model/AliasData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/BotSettings/AWSSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/AWSSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/AppConfig.java](../../../src/main/java/com/safjnest/model/BotSettings/AppConfig.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/BotSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/BotSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/DatabaseSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/DatabaseSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/JsonSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/JsonSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/LavalinkSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/LavalinkSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/OpenAISettings.java](../../../src/main/java/com/safjnest/model/BotSettings/OpenAISettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/PoTokenSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/PoTokenSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/RiotSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/RiotSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/Settings.java](../../../src/main/java/com/safjnest/model/BotSettings/Settings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/SpotifyApiSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/SpotifyApiSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/SpotifySettings.java](../../../src/main/java/com/safjnest/model/BotSettings/SpotifySettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/BotSettings/TwitchSettings.java](../../../src/main/java/com/safjnest/model/BotSettings/TwitchSettings.java) | Locale per servizio | Dividere configurazione per consumatore; nessun secret nel comune |
| [model/UserData.java](../../../src/main/java/com/safjnest/model/UserData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/customemoji/CustomEmoji.java](../../../src/main/java/com/safjnest/model/customemoji/CustomEmoji.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/customemoji/CustomEmojiHandler.java](../../../src/main/java/com/safjnest/model/customemoji/CustomEmojiHandler.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/AutomatedAction.java](../../../src/main/java/com/safjnest/model/guild/AutomatedAction.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/BlacklistData.java](../../../src/main/java/com/safjnest/model/guild/BlacklistData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/ChannelData.java](../../../src/main/java/com/safjnest/model/guild/ChannelData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/GuildData.java](../../../src/main/java/com/safjnest/model/guild/GuildData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/MemberData.java](../../../src/main/java/com/safjnest/model/guild/MemberData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/AlertData.java](../../../src/main/java/com/safjnest/model/guild/alert/AlertData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/AlertKey.java](../../../src/main/java/com/safjnest/model/guild/alert/AlertKey.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/AlertSendType.java](../../../src/main/java/com/safjnest/model/guild/alert/AlertSendType.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/AlertType.java](../../../src/main/java/com/safjnest/model/guild/alert/AlertType.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/RewardData.java](../../../src/main/java/com/safjnest/model/guild/alert/RewardData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/guild/alert/TwitchData.java](../../../src/main/java/com/safjnest/model/guild/alert/TwitchData.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/sound/Sound.java](../../../src/main/java/com/safjnest/model/sound/Sound.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/sound/Tag.java](../../../src/main/java/com/safjnest/model/sound/Tag.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/spotify/SpotifyAlbum.java](../../../src/main/java/com/safjnest/model/spotify/SpotifyAlbum.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/spotify/SpotifyArtist.java](../../../src/main/java/com/safjnest/model/spotify/SpotifyArtist.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/spotify/SpotifyTrack.java](../../../src/main/java/com/safjnest/model/spotify/SpotifyTrack.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/spotify/SpotifyTrackStreaming.java](../../../src/main/java/com/safjnest/model/spotify/SpotifyTrackStreaming.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |
| [model/util/ColorDeserializer.java](../../../src/main/java/com/safjnest/model/util/ColorDeserializer.java) | Bbot | Guild/user/audio/Spotify e presentazione del bot |

## nosql — 6 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [nosql/AbstractEntity.java](../../../src/main/java/com/safjnest/nosql/AbstractEntity.java) | Spring | Persistenza Mongo/monitoraggio o entity executor |
| [nosql/MongoCommandMonitor.java](../../../src/main/java/com/safjnest/nosql/MongoCommandMonitor.java) | Spring | Persistenza Mongo/monitoraggio o entity executor |
| [nosql/MongoDB.java](../../../src/main/java/com/safjnest/nosql/MongoDB.java) | Spring | Persistenza Mongo/monitoraggio o entity executor |
| [nosql/MongoMigration.java](../../../src/main/java/com/safjnest/nosql/MongoMigration.java) | Tooling migration | MariaDB → Mongo; fuori dal runtime comune |
| [nosql/MongoServerStatusSnapshot.java](../../../src/main/java/com/safjnest/nosql/MongoServerStatusSnapshot.java) | Spring | Persistenza Mongo/monitoraggio o entity executor |
| [nosql/NoSqlEntityExecutor.java](../../../src/main/java/com/safjnest/nosql/NoSqlEntityExecutor.java) | Spring | Persistenza Mongo/monitoraggio o entity executor |

## org.voicerss.tts — 10 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [org/voicerss/tts/Audio/AudioCodec.java](../../../src/main/java/org/voicerss/tts/Audio/AudioCodec.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Audio/AudioFormat.java](../../../src/main/java/org/voicerss/tts/Audio/AudioFormat.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Languages.java](../../../src/main/java/org/voicerss/tts/Languages.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Speech/SpeechDataEvent.java](../../../src/main/java/org/voicerss/tts/Speech/SpeechDataEvent.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Speech/SpeechDataEventListener.java](../../../src/main/java/org/voicerss/tts/Speech/SpeechDataEventListener.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Speech/SpeechErrorEvent.java](../../../src/main/java/org/voicerss/tts/Speech/SpeechErrorEvent.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Speech/SpeechErrorEventListener.java](../../../src/main/java/org/voicerss/tts/Speech/SpeechErrorEventListener.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Voice/VoiceParameters.java](../../../src/main/java/org/voicerss/tts/Voice/VoiceParameters.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Voice/VoiceProvider.java](../../../src/main/java/org/voicerss/tts/Voice/VoiceProvider.java) | Bbot | TTS/audio; non comune |
| [org/voicerss/tts/Voice/Voices.java](../../../src/main/java/org/voicerss/tts/Voice/Voices.java) | Bbot | TTS/audio; non comune |

## redis — 3 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [redis/RedisClient.java](../../../src/main/java/com/safjnest/redis/RedisClient.java) | Riuso tecnico candidato | Adapter/metric parser; pool/config/lifecycle locali |
| [redis/RedisKey.java](../../../src/main/java/com/safjnest/redis/RedisKey.java) | Da separare | Cataloghi/TTL/env locali R4J e Spring; rimuovere App |
| [redis/RedisMemoryParser.java](../../../src/main/java/com/safjnest/redis/RedisMemoryParser.java) | Riuso tecnico candidato | Adapter/metric parser; pool/config/lifecycle locali |

## spring — 13 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [spring/SpringServer.java](../../../src/main/java/com/safjnest/spring/SpringServer.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/config/LolApiConfig.java](../../../src/main/java/com/safjnest/spring/config/LolApiConfig.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/AiTrainingController.java](../../../src/main/java/com/safjnest/spring/controller/AiTrainingController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/ChampionController.java](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/LeaderboardController.java](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/LolApiExceptionHandler.java](../../../src/main/java/com/safjnest/spring/controller/LolApiExceptionHandler.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/LolApiParameters.java](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/LolApiResponses.java](../../../src/main/java/com/safjnest/spring/controller/LolApiResponses.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/LolController.java](../../../src/main/java/com/safjnest/spring/controller/LolController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/ProfileIndexableController.java](../../../src/main/java/com/safjnest/spring/controller/ProfileIndexableController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/RecordsController.java](../../../src/main/java/com/safjnest/spring/controller/RecordsController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/controller/StatusController.java](../../../src/main/java/com/safjnest/spring/controller/StatusController.java) | Spring | Server/controller/configurazione/parser/mapping HTTP |
| [spring/dto/LolApiError.java](../../../src/main/java/com/safjnest/spring/dto/LolApiError.java) | Comune DTO candidato | Schema errore senza Spring; richiede sincronizzazione ADR |

## sql — 8 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [sql/AbstractDB.java](../../../src/main/java/com/safjnest/sql/AbstractDB.java) | Bbot + tooling | Connessioni SQL domini bot e migration; config da isolare |
| [sql/DatabaseHandler.java](../../../src/main/java/com/safjnest/sql/DatabaseHandler.java) | Bbot + tooling | Connessioni SQL domini bot e migration; config da isolare |
| [sql/QueryRecord.java](../../../src/main/java/com/safjnest/sql/QueryRecord.java) | Da qualificare | Helper query già misto SQL/Mongo; non risposta pubblica |
| [sql/QueryRecordParser.java](../../../src/main/java/com/safjnest/sql/QueryRecordParser.java) | Da qualificare | Helper query già misto SQL/Mongo; non risposta pubblica |
| [sql/database/BotDB.java](../../../src/main/java/com/safjnest/sql/database/BotDB.java) | Bbot | Persistenza relazionale del rispettivo dominio |
| [sql/database/LeagueDB.java](../../../src/main/java/com/safjnest/sql/database/LeagueDB.java) | Tooling migration | SQL storico LoL; nessun runtime fallback |
| [sql/database/SpotifyDB.java](../../../src/main/java/com/safjnest/sql/database/SpotifyDB.java) | Bbot | Persistenza relazionale del rispettivo dominio |
| [sql/database/WebsiteDB.java](../../../src/main/java/com/safjnest/sql/database/WebsiteDB.java) | Bbot | Persistenza relazionale del rispettivo dominio |

## status — 6 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [status/LeagueMetricsStore.java](../../../src/main/java/com/safjnest/status/LeagueMetricsStore.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |
| [status/MongoMetricsSampler.java](../../../src/main/java/com/safjnest/status/MongoMetricsSampler.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |
| [status/SampledMetrics.java](../../../src/main/java/com/safjnest/status/SampledMetrics.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |
| [status/StatusRates.java](../../../src/main/java/com/safjnest/status/StatusRates.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |
| [status/StatusService.java](../../../src/main/java/com/safjnest/status/StatusService.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |
| [status/SystemMetricsSampler.java](../../../src/main/java/com/safjnest/status/SystemMetricsSampler.java) | Locale + Spring | Sampling locale e aggregazione esplicita; non DTO/core job |

## utils — 26 file

| File attuale | Destinazione proposta | Azione/confine |
|---|---|---|
| [utils/Accumulator.java](../../../src/main/java/com/safjnest/utils/Accumulator.java) | Da qualificare | Helper puro candidato solo con consumatore condiviso |
| [utils/AlertMessage.java](../../../src/main/java/com/safjnest/utils/AlertMessage.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/AutomatedActionTimer.java](../../../src/main/java/com/safjnest/utils/AutomatedActionTimer.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/BotCommand.java](../../../src/main/java/com/safjnest/utils/BotCommand.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/CommandsLoader.java](../../../src/main/java/com/safjnest/utils/CommandsLoader.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/DateHandler.java](../../../src/main/java/com/safjnest/utils/DateHandler.java) | Da qualificare | Helper puro candidato solo con consumatore condiviso |
| [utils/ExperienceSystem.java](../../../src/main/java/com/safjnest/utils/ExperienceSystem.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/HttpUtils.java](../../../src/main/java/com/safjnest/utils/HttpUtils.java) | Riuso tecnico candidato | Base JDK da valutare per futuro client; non HttpBuilder completo |
| [utils/JsonCodec.java](../../../src/main/java/com/safjnest/utils/JsonCodec.java) | Da separare | JSON tecnico candidato; BSON resta Spring |
| [utils/PermissionHandler.java](../../../src/main/java/com/safjnest/utils/PermissionHandler.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/SafJNest.java](../../../src/main/java/com/safjnest/utils/SafJNest.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/SettingsLoader.java](../../../src/main/java/com/safjnest/utils/SettingsLoader.java) | Locale per servizio | Config/logging; nessuna dipendenza bot nel comune |
| [utils/TableHandler.java](../../../src/main/java/com/safjnest/utils/TableHandler.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/TimeConstant.java](../../../src/main/java/com/safjnest/utils/TimeConstant.java) | Da qualificare | Helper puro candidato solo con consumatore condiviso |
| [utils/ValidationUtils.java](../../../src/main/java/com/safjnest/utils/ValidationUtils.java) | Da qualificare | Helper puro candidato solo con consumatore condiviso |
| [utils/log/BotLogger.java](../../../src/main/java/com/safjnest/utils/log/BotLogger.java) | Locale per servizio | Config/logging; nessuna dipendenza bot nel comune |
| [utils/log/BotLoggerColorConverter.java](../../../src/main/java/com/safjnest/utils/log/BotLoggerColorConverter.java) | Locale per servizio | Config/logging; nessuna dipendenza bot nel comune |
| [utils/log/LoggerIDpair.java](../../../src/main/java/com/safjnest/utils/log/LoggerIDpair.java) | Locale per servizio | Config/logging; nessuna dipendenza bot nel comune |
| [utils/spotify/SpotifyException.java](../../../src/main/java/com/safjnest/utils/spotify/SpotifyException.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/spotify/SpotifyHandler.java](../../../src/main/java/com/safjnest/utils/spotify/SpotifyHandler.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/spotify/SpotifyMessage.java](../../../src/main/java/com/safjnest/utils/spotify/SpotifyMessage.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/spotify/SpotifyTokenManager.java](../../../src/main/java/com/safjnest/utils/spotify/SpotifyTokenManager.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/spotify/type/SpotifyMessageType.java](../../../src/main/java/com/safjnest/utils/spotify/type/SpotifyMessageType.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/spotify/type/SpotifyTimeRange.java](../../../src/main/java/com/safjnest/utils/spotify/type/SpotifyTimeRange.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/twitch/TwitchClient.java](../../../src/main/java/com/safjnest/utils/twitch/TwitchClient.java) | Bbot | Utility bot/integrazione; non trasferimento globale |
| [utils/twitch/TwitchEventsHandler.java](../../../src/main/java/com/safjnest/utils/twitch/TwitchEventsHandler.java) | Bbot | Utility bot/integrazione; non trasferimento globale |

## Dipendenze testuali baseline dei contratti da isolare

Gli import di snapshot job qui sotto sono storici: il port ADR-0017 li ha
trasferiti a `com.hive.status` con dipendenze solo Hive/JDK.

Per ciascun file LoL model, import diretti esterni al JDK/Jackson e agli
altri model LoL. `—` significa soltanto assenza di quegli import, non prova
di indipendenza operativa o chiusura transitive del contratto.

| File in `lol/model` | Import esterni al perimetro dati |
|---|---|
| `ActivityFilter.java` | — |
| `ApiResult.java` | — |
| `Augment.java` | `com.safjnest.lol.utils.AugmentUtils` |
| `Build.java` | `com.safjnest.lol.champion.RuneSignature`; `com.safjnest.lol.utils.BuildUtils`; `com.safjnest.utils.JsonCodec` |
| `ChampionIndexable.java` | `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `ChampionStatistics.java` | `com.safjnest.utils.JsonCodec`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `ChampionTierList.java` | `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `ChampionTierSource.java` | — |
| `ChampionView.java` | — |
| `Filter.java` | `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType`; `no.stelar7.api.r4j.basic.constants.types.lol.TierType`; `com.safjnest.lol.utils.FilterCodec`; `com.safjnest.lol.utils.NumberUtils`; `com.safjnest.lol.utils.PatchUtils`; `com.safjnest.lol.utils.SeasonUtils` |
| `PlayerChampionStats.java` | — |
| `ProfileIndexable.java` | — |
| `ResponseMetadata.java` | — |
| `competitive/CompetitiveEntry.java` | `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `leaderboard/LeaderboardDistribution.java` | — |
| `leaderboard/LeaderboardPage.java` | — |
| `match/LiveGame.java` | `com.safjnest.lol.champion.RuneSignature`; `com.safjnest.lol.utils.ChampionUtils`; `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameModeType`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.GameType`; `no.stelar7.api.r4j.basic.constants.types.lol.MapType`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType`; `no.stelar7.api.r4j.pojo.lol.shared.BannedChampion`; `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo`; `no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant` |
| `match/Match.java` | `org.json.JSONObject`; `com.safjnest.nosql.AbstractEntity`; `com.safjnest.lol.tracker.Tracker`; `com.safjnest.lol.utils.NumberUtils`; `com.safjnest.lol.utils.PatchUtils`; `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType`; `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType`; `no.stelar7.api.r4j.basic.constants.types.lol.TierType`; `no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch` |
| `match/MatchOrder.java` | — |
| `match/MatchPage.java` | — |
| `match/MatchResult.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `match/Participant.java` | `no.stelar7.api.r4j.basic.constants.types.lol.LaneType`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` |
| `match/RankHistory.java` | — |
| `match/RankHistoryMatch.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType`; `com.safjnest.lol.utils.LaneTypeUtils`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` |
| `match/RankHistoryMetadata.java` | — |
| `match/RankHistoryQuery.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` |
| `match/RankHistoryView.java` | — |
| `match/RankProgress.java` | `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType` |
| `record/ProfileRecord.java` | `com.safjnest.lol.utils.TierDivisionUtils`; `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` |
| `record/ProfileRecordPage.java` | — |
| `record/RecordMetric.java` | — |
| `record/RecordOrder.java` | — |
| `record/RecordOwner.java` | — |
| `record/RecordPage.java` | — |
| `record/RecordsOverview.java` | — |
| `rune/PageRunes.java` | — |
| `rune/Rune.java` | — |
| `statistics/CanonicalQueue.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` |
| `statistics/ChampionStatsDocument.java` | — |
| `statistics/ProfileActivity.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` |
| `statistics/ProfileMatchupLeaf.java` | — |
| `statistics/ProfileMatchups.java` | `com.safjnest.lol.utils.MatchupTimelineUtils`; `com.safjnest.lol.utils.MatchupTimelineUtils.Data`; `com.safjnest.lol.utils.MatchupTimelineUtils.Snapshot`; `com.safjnest.lol.utils.GameQueueTypeUtils`; `com.safjnest.lol.utils.KdaUtils`; `com.safjnest.lol.utils.LaneTypeUtils`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType` |
| `statistics/ProfileStatistics.java` | `com.safjnest.lol.utils.GameQueueTypeUtils`; `com.safjnest.lol.utils.KdaUtils`; `com.safjnest.lol.utils.OPTUtils`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.LaneType`; `no.stelar7.api.r4j.basic.constants.types.lol.TierType` |
| `statistics/ProfileStatisticsRow.java` | — |
| `statistics/shared/ChampionLeafStats.java` | — |
| `statistics/shared/ChampionNode.java` | — |
| `statistics/shared/ChampionStatsScope.java` | `com.safjnest.lol.utils.FilterCodec`; `com.safjnest.lol.utils.PatchUtils`; `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType`; `no.stelar7.api.r4j.basic.constants.types.lol.TierType` |
| `statistics/shared/LeafStats.java` | — |
| `statistics/shared/MatchupStats.java` | — |
| `statistics/shared/ProfileLeafStats.java` | `com.safjnest.lol.utils.KdaUtils`; `no.stelar7.api.r4j.basic.constants.types.lol.TeamType` |
| `statistics/shared/TrendStats.java` | — |
| `statistics/shared/WinLossStats.java` | — |
| `status/BotStatus.java` | — |
| `status/JobProgress.java` | — |
| `status/JobStatus.java` | `com.safjnest.lol.queue.job.JobPriority`; `com.safjnest.lol.queue.job.JobState` |
| `status/JvmMetrics.java` | — |
| `status/LeagueMetrics.java` | — |
| `status/MongoCollectionPerformance.java` | — |
| `status/MongoHottestCollection.java` | — |
| `status/MongoMemoryMetrics.java` | — |
| `status/MongoMetrics.java` | — |
| `status/MongoOperationRates.java` | — |
| `status/MongoOperationSample.java` | — |
| `status/MongoOperationsMetrics.java` | — |
| `status/MongoPerformanceMetrics.java` | — |
| `status/MongoSlowOperation.java` | — |
| `status/QueueStatus.java` | — |
| `status/RedisMetrics.java` | — |
| `status/RunStatus.java` | `com.safjnest.lol.queue.job.JobState` |
| `status/SchedulerStatus.java` | — |
| `status/SystemMetrics.java` | — |
| `status/WorkerStatus.java` | `com.safjnest.lol.queue.worker.WorkerState` |
| `summoner/Mastery.java` | — |
| `summoner/Rank.java` | `no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType` |
| `summoner/Summoner.java` | `com.safjnest.lol.utils.GameQueueTypeUtils`; `com.safjnest.nosql.AbstractEntity`; `no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard`; `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` |
| `summoner/SummonerLeaderboard.java` | — |
| `summoner/SummonerOverview.java` | `com.safjnest.lol.utils.ChampionUtils` |
| `summoner/SummonerView.java` | `no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType` |

## Aggiunte Hive rispetto al census baseline

- `com/hive/Hive.java`: runtime e admission per istanza.
- `com/hive/HiveConfig.java`: clock, identity, limiti, shutdown e osservazione.
- `com/hive/JobFuture.java`: caller view distinta dalla terminazione lifecycle.
- `com/hive/job/ResultType.java`: contratti di risultato e token per liste.
- `hive/pom.xml`: artifact core autonomo dai sorgenti canonici, senza dipendenze runtime.
- La classificazione dei run è nel hook locale SyncScheduler; StatusService compone il payload.
- Nessun alias o seconda copia del core in `lol.queue`; queue audio esclusa.

La mappa funzionale dei chiamanti e test è in [job-queue.md](job-queue.md).
