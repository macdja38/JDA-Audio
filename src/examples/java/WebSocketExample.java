/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.neovisionaries.ws.client.*;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.CoreClient;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketExample extends WebSocketAdapter
{
    public static void main(String[] args) throws IOException, WebSocketException
    {
        //Provide user id of the logged in account.
        String userId = "157914012892397568";

        new WebSocketExample(userId);
    }

    // =============================================

    private final HashMap<String, Core> cores = new HashMap<>();
    private final String userId;
    private WebSocket socket;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager playerManager;

    public WebSocketExample(String userId) throws IOException, WebSocketException
    {
        this.userId = userId;
        socket = new WebSocketFactory()
                .createSocket("ws://localhost/")
                .addListener(this);
        socket.connect();
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception
    {
        try {
            System.out.println("Received Text Message");
            System.out.println(text);
            JSONObject obj = new JSONObject(text);
            System.out.println("Parsed JSON");

            String guild_id = obj.getString("guild_id");
            String nonce = obj.getString("nonce");
            String action = obj.getString("action");

            System.out.println("Before Get Core");

            Core core = getCore(guild_id);

            System.out.println("After Get Core");

            System.out.println(action);

            switch (action) {
                case "OPEN_CONNECTION": {
                    System.out.println("Trying to open connection");
                    String guildId = obj.getString("guild_id");
                    String channelId = obj.getString("channel_id");

                    core.getAudioManager(guildId).openAudioConnection(channelId);
                    break;
                }
                case "VOICE_SERVER_UPDATE": {
                    String sessionId = obj.getString("session_id");
                    JSONObject vsu = obj.getJSONObject("vsu");
                    System.out.println("VOICE_SERVER_UPDATE");
                    System.out.println("sessionId");
                    System.out.println(vsu);

                    core.provideVoiceServerUpdate(sessionId, vsu);
                    break;
                }
                case "CLOSE_CONNECTION": {
                    String guildId = obj.getString("guild_id");

                    core.getAudioManager(guildId).closeAudioConnection();
                    break;
                }
                case "PLAY_SONG": {
                    String guildId = obj.getString("guild_id");
                    loadAndPlay(core, guildId, "https://r17---sn-p5qlsn6e.googlevideo.com/videoplayback?source=youtube&mm=31&mn=sn-p5qlsn6e&pl=24&requiressl=yes&ip=192.99.34.113&mime=video%2Fmp4&expire=1495323970&id=o-AJXwixb79wuXwfWOXCv5GX9thLLEEj15BnOHTodW0bSN&ms=au&mt=1495302261&mv=m&ipbits=0&ratebypass=yes&upn=gOz8UmaVCtU&key=yt6&itag=22&dur=267.749&initcwndbps=767500&beids=%5B9466593%5D&sparams=dur%2Cei%2Cid%2Cinitcwndbps%2Cip%2Cipbits%2Citag%2Clmt%2Cmime%2Cmm%2Cmn%2Cms%2Cmv%2Cpl%2Cratebypass%2Crequiressl%2Csource%2Cupn%2Cexpire&lmt=1471599886719114&ei=4oAgWcjQI8XI8wSH66jAAw&signature=02BCA2B07E0D068BF205F5BF0751B029B64C5160.8A8801FE9A1C4C4D866F29ED476BB300B5356953" /*"riVdyBDw8SE"*/);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Core core, String guildString) {
        long guildId = Long.parseLong(guildString);
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        core.getAudioManager(guildString).setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(Core core, String guild_id, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(core, guild_id);

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("Adding to queue " + track.getInfo().title);

                play(musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                System.out.println("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")");

                play(musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                System.out.println("Nothing found by " + trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                System.out.println("Could not play: " + exception.getMessage());
            }
        });
    }

    private void play(GuildMusicManager musicManager, AudioTrack track) {
        musicManager.scheduler.queue(track);
    }


    void playSong(String song, Core core, String guildId) {
        AudioManager manager = core.getAudioManager(guildId);

        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioPlayer player = playerManager.createPlayer();

        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);

        System.out.println("Attempting to play video");
        playerManager.loadItem(song, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("Calling PlayTrack");
                player.playTrack(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                for (AudioTrack track : playlist.getTracks()) {
                }
            }

            @Override
            public void noMatches() {
                System.out.println("No Matches");
                // Notify the user that we've got nothing
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                System.out.println("Load Failed");
                throwable.printStackTrace();
                // Notify the user that everything exploded
            }
        });

        manager.setSendingHandler(new AudioPlayerSendHandler(player));
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception
    {
        System.out.println("Successfully connected to localhost WS!");
    }


    @Override
    public void onDisconnected(WebSocket websocket,
                               WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame,
                               boolean closedByServer) throws Exception
    {
        System.out.println("Disconnected from localhost WS. Might wanna build a reconnect system!");
    }

    private Core getCore(String guild_id)
    {
        Core core = cores.get(guild_id);
        if (core == null)
        {
            synchronized (cores)
            {
                core = cores.get(guild_id);
                if (core == null)
                {
                    core = new Core(userId, new MyCoreClient(guild_id));
                    cores.put(guild_id, core);
                }
            }
        }

        return core;
    }

    private class MyCoreClient implements CoreClient
    {
        private final String guild_id;

        public MyCoreClient(String guild_id)
        {
            this.guild_id = guild_id;
        }

        @Override
        public void sendWS(String message)
        {
            JSONObject obj = new JSONObject();
            obj.put("action", "SEND_WS");
            obj.put("guild_id", guild_id);
            obj.put("message", message);

            socket.sendText(obj.toString());
        }

        @Override
        public boolean isConnected()
        {
            return true;
        }

        @Override
        public boolean inGuild(String guildId)
        {
            return true;
        }

        @Override
        public boolean voiceChannelExists(String channelId)
        {
            return true;
        }

        @Override
        public boolean hasPermissionInChannel(String channelId, long permission)
        {
            return true;
        }
    }
}