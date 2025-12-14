import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.*;

public class SoundManager {
    private static SoundManager instance;
    private Map<String, Clip> soundCache;
    private Map<String, List<Clip>> soundPoolCache; // Pool of clips for UI sounds
    private Map<String, Integer> soundPoolIndex; // Current pool index
    private Map<String, Long> soundCooldowns; // Track last play time for throttling
    private static final long SOUND_COOLDOWN_MS = 50; // Minimum time between same sounds
    private static final int SOUND_POOL_SIZE = 5; // Clips per pooled sound
    private float masterVolume = 0.7f;
    private float sfxVolume = 0.8f;
    private float uiVolume = 0.8f;
    private float musicVolume = 0.5f;
    private boolean soundEnabled = true;
    private boolean soundsReady = false; // Track if sounds are preloaded
    private Clip ambientClip; // For looping ambient sound
    private Clip musicClip; // For looping background music (WAV only - convert MP3 to WAV)
    private String currentMusic = null; // Track which music is playing
    
    // Sound paths
    private static final String UI_PATH = "SFX/UI SFX/Mono/wav (SD)/";
    private static final String GAME_PATH = "SFX/Retro Game SFX/GameSFX/";
    private static final String EXPLOSION_PATH = "SFX/Explosions SFX/";
    private static final String MUSIC_PATH = "SFX/Music Tracks/";
    
    public enum Sound {
        // UI Sounds - Navigation
        UI_SELECT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Select - 1.wav"),
        UI_SELECT_ALT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Select - 2.wav"),
        UI_CURSOR(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cursor - 1.wav"),
        UI_CURSOR_ALT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cursor - 2.wav"),
        UI_CURSOR_SOFT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cursor - 3.wav"),
        UI_CANCEL(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cancel - 1.wav"),
        UI_CANCEL_ALT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cancel - 2.wav"),
        UI_ERROR(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Error - 1.wav"),
        UI_POPUP_OPEN(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Popup Open - 1.wav"),
        UI_POPUP_CLOSE(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Popup Close - 1.wav"),
        UI_SWIPE(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Swipe - 1.wav"),
        UI_SWIPE_ALT(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Swipe - 2.wav"),
        
        // 8-bit Explosions - Short (for bullet fire, small impacts)
        EXPL_SHORT_1(EXPLOSION_PATH + "Short/8bit_expl_short_00.wav"),
        EXPL_SHORT_2(EXPLOSION_PATH + "Short/8bit_expl_short_01.wav"),
        EXPL_SHORT_3(EXPLOSION_PATH + "Short/8bit_expl_short_02.wav"),
        EXPL_SHORT_4(EXPLOSION_PATH + "Short/8bit_expl_short_03.wav"),
        EXPL_SHORT_5(EXPLOSION_PATH + "Short/8bit_expl_short_04.wav"),
        
        // 8-bit Explosions - Medium (for active items, bullet explosions)
        EXPL_MEDIUM_1(EXPLOSION_PATH + "Medium/8bit_expl_medium_00.wav"),
        EXPL_MEDIUM_2(EXPLOSION_PATH + "Medium/8bit_expl_medium_01.wav"),
        EXPL_MEDIUM_3(EXPLOSION_PATH + "Medium/8bit_expl_medium_02.wav"),
        EXPL_MEDIUM_4(EXPLOSION_PATH + "Medium/8bit_expl_medium_03.wav"),
        EXPL_MEDIUM_5(EXPLOSION_PATH + "Medium/8bit_expl_medium_04.wav"),
        
        // 8-bit Explosions - Long (for boss death, major events)
        EXPL_LONG_1(EXPLOSION_PATH + "Long/8bit_expl_long_00.wav"),
        EXPL_LONG_2(EXPLOSION_PATH + "Long/8bit_expl_long_01.wav"),
        EXPL_LONG_3(EXPLOSION_PATH + "Long/8bit_expl_long_02.wav"),
        EXPL_LONG_4(EXPLOSION_PATH + "Long/8bit_expl_long_03.wav"),
        
        // Game Sounds - Retro Explosions
        EXPLOSION_SHORT(GAME_PATH + "Explosion/Retro Explosion Short 01.wav"),
        EXPLOSION_LONG(GAME_PATH + "Explosion/Retro Explosion Long 02.wav"),
        
        // Game Sounds - Impacts
        HIT_NORMAL(GAME_PATH + "Impact/Retro Impact Punch 07.wav"),
        HIT_STRONG(GAME_PATH + "Impact/Retro Impact Punch Hurt 01.wav"),
        HIT_METAL(GAME_PATH + "Impact/Retro Impact Metal 05.wav"),
        HIT_WATER(GAME_PATH + "Impact/Retro Impact Water 03.wav"),
        
        // Game Sounds - PowerUps and Pickups
        POWERUP_PICKUP(GAME_PATH + "PickUp/Retro PickUp Coin 04.wav"),
        POWERUP_ACTIVATE(GAME_PATH + "PowerUp/Retro PowerUP 09.wav"),
        POWERUP_ACTIVATE_ALT(GAME_PATH + "PowerUp/Retro PowerUP 23.wav"),
        ITEM_PICKUP(GAME_PATH + "PickUp/Retro PickUp Coin 07.wav"),
        ITEM_PICKUP_ALT(GAME_PATH + "PickUp/Retro PickUp 10.wav"),
        COIN_PICKUP(GAME_PATH + "PickUp/Retro PickUp Coin StereoUP 04.wav"),
        
        // Game Sounds - Events and Milestones
        COMBO_MILESTONE(GAME_PATH + "Events/Retro Event UI 15.wav"),
        PERFECT_DODGE(GAME_PATH + "Events/Retro Event StereoUP 02.wav"),
        CLOSE_CALL(GAME_PATH + "Events/Retro Event Acute 08.wav"),
        GRAZE(GAME_PATH + "Events/Retro Event UI 01.wav"),
        VULNERABILITY_WINDOW(GAME_PATH + "Charge/Retro Charge 07.wav"),
        LEVEL_START(GAME_PATH + "Events/Retro Event 19.wav"),
        LEVEL_COMPLETE(GAME_PATH + "Events/Retro Event 49.wav"),
        CONTRACT_UNLOCK(GAME_PATH + "Magic/Retro Magic 11.wav"),
        ACHIEVEMENT_UNLOCK(GAME_PATH + "Magic/Retro Magic 34.wav"),
        
        // Game Sounds - Magic and Special Effects
        MAGIC_CAST(GAME_PATH + "Magic/Retro Magic 06.wav"),
        MAGIC_CHARGE(GAME_PATH + "Charge/Retro Charge Magic 11.wav"),
        ELECTRIC_ZAP(GAME_PATH + "Electric/Retro Electric 02.wav"),
        
        // Game Sounds - Weapons and Combat
        SHOOT(GAME_PATH + "Weapon/Retro Gun SingleShot 04.wav"),
        SHOOT_MULTI(GAME_PATH + "Weapon/Retro Gun Multishots 6 Delay9 03.wav"),
        LASER_CHARGE(GAME_PATH + "Charge/Retro Charge Electric Off 07.wav"),
        BOSS_SHOOT(GAME_PATH + "Weapon/Retro Gun SingleShot 04.wav"),
        GRENADE_EXPLODE(GAME_PATH + "Explosion/Retro Explosion Long 02.wav"),
        BEAM_WARNING(GAME_PATH + "Alarms Blip Beeps/Retro Alarm 02.wav"),
        
        // Shop Sounds
        PURCHASE_SUCCESS(GAME_PATH + "PickUp/Retro PickUp Coin 07.wav"),
        PURCHASE_FAIL(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Error - 1.wav"),
        
        // Game Sounds - Active Items
        SHIELD_ACTIVATE(GAME_PATH + "HiTech/Retro HiTech 08.wav"),
        SHIELD_BREAK(GAME_PATH + "Impact/Retro Impact Metal 36.wav"),
        BOMB_ACTIVATE(GAME_PATH + "Electronic Burst/Retro Electronic Burst 05.wav"),
        SLOW_TIME_ACTIVATE(GAME_PATH + "Charge/Retro Charge 13.wav"),
        INVINCIBILITY_ACTIVATE(GAME_PATH + "Magic/Retro Magic Electric 03.wav"),
        
        // Game Sounds - Alarms and Warnings
        WARNING(GAME_PATH + "Alarms Blip Beeps/Retro Alarm 02.wav"),
        WARNING_LONG(GAME_PATH + "Alarms Blip Beeps/Retro Alarm Long 02.wav"),
        BEEP(GAME_PATH + "Alarms Blip Beeps/Retro Beeep 06.wav"),
        BLIP(GAME_PATH + "Alarms Blip Beeps/Retro Blip 07.wav"),
        
        // Game Sounds - Movement
        DASH(GAME_PATH + "Swoosh/Retro Swooosh 02.wav"),
        SWOOSH(GAME_PATH + "Swoosh/Retro Swooosh 16.wav"),
        DODGE(GAME_PATH + "Bounce Jump/Retro Jump Simple A 01.wav"),
        JUMP(GAME_PATH + "Bounce Jump/Retro Jump Classic 08.wav"),
        SCREEN_SHAKE(GAME_PATH + "Impact/Retro Impact Punch 07.wav"),
        
        // Game Sounds - UI Navigation and Transitions
        LEVEL_SWITCH(GAME_PATH + "Swoosh/Retro Swooosh 07.wav"),
        MENU_OPEN(GAME_PATH + "Events/Retro Event Complex 03.wav"),
        PAUSE(GAME_PATH + "Events/Retro Event Echo 12.wav"),
        UNPAUSE(GAME_PATH + "Bounce Jump/Retro Jump Simple B 05.wav"),
        
        // Game Sounds - Ascending/Leveling
        LEVEL_UP(GAME_PATH + "Ascending/Retro Ascending Short 20.wav"),
        RANK_UP(GAME_PATH + "Ascending/Retro Ascending Long 06.wav"),
        
        // Game Sounds - Blops and Soft Impacts
        BLOP_1(GAME_PATH + "Blops/Retro Blop 07.wav"),
        BLOP_2(GAME_PATH + "Blops/Retro Blop 18.wav"),
        BLOP_3(GAME_PATH + "Blops/Retro Blop StereoUP 04.wav"),
        
        // Death and Boss
        PLAYER_DEATH(GAME_PATH + "Explosion/Retro Explosion Short 15.wav"),
        PLAYER_RESPAWN(GAME_PATH + "PowerUp/Retro PowerUP StereoUP 05.wav"),
        BOSS_HIT(GAME_PATH + "Impact/Retro Impact Metal 05.wav"),
        BOSS_DEATH(GAME_PATH + "Explosion/Retro Explosion Swoshes 04.wav"),
        BOSS_ROAR(GAME_PATH + "Roar/Retro Roar 02.wav"),
        
        // Game Over
        GAME_OVER(GAME_PATH + "Music/Negative/Retro Negative Melody 02 - space voice pad.wav"),
        
        // Ambient/Background
        AMBIENT_BACKGROUND(GAME_PATH + "Ambience/Retro Ambience Stretch Large 01.wav");
        
        private final String path;
        
        Sound(String path) {
            this.path = path;
        }
        
        public String getPath() {
            return path;
        }
    }
    
    private SoundManager() {
        soundCache = new HashMap<>();
        soundPoolCache = new HashMap<>();
        soundPoolIndex = new HashMap<>();
        soundCooldowns = new HashMap<>();
    }
    
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    public void preloadSounds() {
        // Preload common sounds
        for (Sound sound : Sound.values()) {
            try {
                loadSound(sound);
            } catch (Exception e) {
                System.err.println("Failed to preload sound: " + sound.name() + " - " + e.getMessage());
            }
        }
        soundsReady = true; // Mark sounds as ready
    }
    
    private Clip loadSound(Sound sound) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (soundCache.containsKey(sound.name())) {
            return soundCache.get(sound.name());
        }
        
        File soundFile = new File(sound.getPath());
        if (!soundFile.exists()) {
            System.err.println("Sound file not found: " + sound.getPath());
            return null;
        }
        
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        soundCache.put(sound.name(), clip);
        return clip;
    }
    
    public void playSound(Sound sound) {
        playSound(sound, 1.0f);
    }
    
    public void playSound(Sound sound, float volumeMultiplier) {
        if (!soundEnabled || !soundsReady) return;
        
        // Only apply cooldown to high-frequency sounds to prevent spam
        boolean needsCooldown = sound.name().startsWith("EXPL_") || 
                                sound.name().startsWith("BLOP_") || 
                                sound.name().equals("GRAZE");
        
        if (needsCooldown) {
            long currentTime = System.currentTimeMillis();
            Long lastPlayTime = soundCooldowns.get(sound.name());
            if (lastPlayTime != null && (currentTime - lastPlayTime) < SOUND_COOLDOWN_MS) {
                return; // Skip if played too recently
            }
            soundCooldowns.put(sound.name(), currentTime);
        }
        
        try {
            Clip clip;
            
            // Use sound pool for frequently played sounds to allow simultaneous playback
            boolean shouldPool = sound.name().startsWith("UI_") ||
                                sound.name().startsWith("EXPL_") ||
                                sound.name().equals("BOSS_HIT") ||
                                sound.name().equals("BOSS_SHOOT") ||
                                sound.name().equals("GRENADE_EXPLODE") ||
                                sound.name().equals("BEAM_WARNING") ||
                                sound.name().equals("SCREEN_SHAKE") ||
                                sound.name().equals("DODGE") ||
                                sound.name().equals("PERFECT_DODGE") ||
                                sound.name().equals("CLOSE_CALL") ||
                                sound.name().equals("COIN_PICKUP") ||
                                sound.name().equals("ITEM_PICKUP");
            
            if (shouldPool) {
                clip = getPooledClip(sound);
            } else {
                clip = soundCache.get(sound.name());
                if (clip == null) {
                    clip = loadSound(sound);
                }
            }
            
            if (clip != null) {
                // For pooled clips, don't stop - just use next in pool
                // For non-pooled, stop and restart if already playing
                if (!shouldPool && clip.isRunning()) {
                    clip.stop();
                }
                clip.setFramePosition(0);
                
                // Set volume based on sound type
                float volume = masterVolume * volumeMultiplier;
                if (sound.name().startsWith("UI_")) {
                    volume *= uiVolume;
                } else {
                    volume *= sfxVolume;
                }
                
                // Reduce volume for specific loud sounds
                if (sound == Sound.PAUSE || sound == Sound.UNPAUSE) {
                    volume *= 0.4f; // Pause sounds are too loud, reduce to 40%
                }
                
                setVolume(clip, volume);
                clip.start();
            }
        } catch (Exception e) {
            System.err.println("Error playing sound " + sound.name() + ": " + e.getMessage());
        }
    }
    
    private Clip getPooledClip(Sound sound) throws LineUnavailableException, IOException, UnsupportedAudioFileException {
        List<Clip> pool = soundPoolCache.get(sound.name());
        
        // Initialize pool if it doesn't exist
        if (pool == null) {
            pool = new ArrayList<>();
            for (int i = 0; i < SOUND_POOL_SIZE; i++) {
                Clip clip = loadSoundClip(sound);
                if (clip != null) {
                    pool.add(clip);
                }
            }
            soundPoolCache.put(sound.name(), pool);
            soundPoolIndex.put(sound.name(), 0);
        }
        
        // Get next clip from pool in round-robin fashion
        int index = soundPoolIndex.get(sound.name());
        Clip clip = pool.get(index);
        soundPoolIndex.put(sound.name(), (index + 1) % pool.size());
        
        return clip;
    }
    
    private Clip loadSoundClip(Sound sound) throws LineUnavailableException, IOException, UnsupportedAudioFileException {
        File soundFile = new File(sound.path);
        if (!soundFile.exists()) {
            System.err.println("Sound file not found: " + sound.path);
            return null;
        }
        
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        return clip;
    }
    
    private void setVolume(Clip clip, float volume) {
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            // Convert linear volume (0.0-1.0) to decibels
            float dB = (float) (Math.log(Math.max(0.0001f, volume)) / Math.log(10.0) * 20.0);
            // Clamp to control's range
            dB = Math.max(volumeControl.getMinimum(), Math.min(dB, volumeControl.getMaximum()));
            volumeControl.setValue(dB);
        }
    }
    
    public void stopAllSounds() {
        for (Clip clip : soundCache.values()) {
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        }
    }
    
    public void cleanup() {
        for (Clip clip : soundCache.values()) {
            if (clip != null) {
                clip.close();
            }
        }
        soundCache.clear();
    }
    
    // Getters and setters for volume controls
    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) { this.masterVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(float volume) { this.sfxVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getUiVolume() { return uiVolume; }
    public void setUiVolume(float volume) { this.uiVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getMusicVolume() { return musicVolume; }
    public void setMusicVolume(float volume) { this.musicVolume = Math.max(0, Math.min(1, volume)); }
    
    public boolean isSoundEnabled() { return soundEnabled; }
    
    public void startAmbientSound() {
        if (!soundEnabled) return;
        
        try {
            if (ambientClip != null && ambientClip.isRunning()) {
                return; // Already playing
            }
            
            ambientClip = loadSound(Sound.AMBIENT_BACKGROUND);
            if (ambientClip != null) {
                ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
                setVolume(ambientClip, masterVolume * sfxVolume * 0.15f); // Very low volume for ambient
            }
        } catch (Exception e) {
            System.err.println("Error starting ambient sound: " + e.getMessage());
        }
    }
    
    public void stopAmbientSound() {
        if (ambientClip != null) {
            ambientClip.stop();
            ambientClip.close();
            ambientClip = null;
        }
    }
    
    public void playMusic(String musicPath) {
        if (!soundEnabled || !soundsReady) return;
        
        // Convert MP3 path to WAV path automatically
        String wavPath = musicPath.replace(".mp3", ".wav");
        
        // Don't restart if same music is already playing
        if (wavPath.equals(currentMusic) && musicClip != null && musicClip.isRunning()) {
            return;
        }
        
        stopMusic();
        
        try {
            File musicFile = new File(wavPath);
            if (!musicFile.exists()) {
                System.err.println("Music file not found: " + wavPath);
                System.err.println("Note: Java's built-in audio only supports WAV files.");
                System.err.println("Please convert " + musicPath + " to WAV format.");
                return;
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicFile);
            musicClip = AudioSystem.getClip();
            musicClip.open(audioStream);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            setVolume(musicClip, masterVolume * musicVolume * 0.6f);
            currentMusic = wavPath;
        } catch (Exception e) {
            System.err.println("Error playing music: " + e.getMessage());
            System.err.println("Note: Java's built-in audio only supports WAV, AIFF, and AU formats.");
        }
    }
    
    public void stopMusic() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
            currentMusic = null;
        }
    }
    
    public void setSoundEnabled(boolean enabled) { 
        this.soundEnabled = enabled;
        if (!enabled) {
            stopAmbientSound();
            stopMusic();
            stopAllSounds();
        }
    }
}
