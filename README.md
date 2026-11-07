# SkyLyrics (Fabric, Minecraft 1.21.11)

Spotify synced lyrics floating in the sky. Client-side mod. Needs Fabric Loader + Fabric API.
In game: type `/lyrics` to open the settings screen (Look tab + Spotify tab).

## Make the jar - easiest way (GitHub, nothing to install)
1. Make a free account at github.com, click New repository, name it SkyLyrics.
2. Upload ALL files from this folder (drag them in; make sure the `.github` folder goes too).
3. Open the Actions tab -> click the latest "build" run -> wait for the green check (~3 min).
4. Scroll to Artifacts, download SkyLyrics-jar, unzip it.
5. Use skylyrics-1.0.0.jar (NOT the -sources one) in .minecraft/mods/ with Fabric API.

## Make the jar - on your own PC
1. Install JDK 21 (Temurin) and Gradle 9.2.x.
2. Open a terminal in this folder and run: gradle build
3. The jar appears in build/libs/skylyrics-1.0.0.jar

## Connect Spotify
Make an app at developer.spotify.com/dashboard (redirect URI http://127.0.0.1:8888/callback),
run `node get_token.js <CLIENT_ID> <CLIENT_SECRET>`, then paste the three values
into the Spotify tab of the settings screen and press Save & reconnect.
