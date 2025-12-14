# How to Convert MP3 Music Files to WAV

Java's built-in audio system (`javax.sound.sampled`) only supports WAV, AIFF, and AU formats.
Your MP3 music files need to be converted to WAV format.

## Option 1: Using Online Converter (Easiest)
1. Go to https://cloudconvert.com/mp3-to-wav or https://online-audio-converter.com/
2. Upload your MP3 files from `SFX/Music Tracks/`
3. Convert to WAV format (PCM 16-bit, 44100 Hz recommended)
4. Download and save the WAV files in the same folder as the MP3s

## Option 2: Using FFmpeg (Best Quality)
1. Download FFmpeg from https://ffmpeg.org/download.html
2. Install and add to system PATH
3. Run the provided PowerShell script:
   ```powershell
   .\convert_mp3_to_wav.ps1
   ```

## Option 3: Using VLC Media Player (Free Desktop Tool)
1. Download VLC if you don't have it: https://www.videolan.org/
2. Open VLC → Media → Convert/Save
3. Add your MP3 files
4. Click "Convert/Save"
5. Profile: Select "Audio - CD"
6. Choose destination folder and filename
7. Click "Start"

## Option 4: Using Audacity (Free, More Control)
1. Download Audacity: https://www.audacityteam.org/
2. File → Open → Select your MP3 file
3. File → Export → Export as WAV
4. Choose "Signed 16-bit PCM" format
5. Save in the same folder as the MP3

## Files to Convert
- Main menu theme.mp3 → Main menu theme.wav
- Boss Fight Theme (1).mp3 → Boss Fight Theme (1).wav
- Boss Fight Theme (5).mp3 → Boss Fight Theme (5).wav
- Boss Fight Theme (6).mp3 → Boss Fight Theme (6).wav
- Boss Fight Theme (7).mp3 → Boss Fight Theme (7).wav
- Boss Fight Theme (8).mp3 → Boss Fight Theme (8).wav

## After Conversion
The game will automatically look for `.wav` versions of the music files.
You can keep both MP3 and WAV files in the folder - the game will use WAV.
