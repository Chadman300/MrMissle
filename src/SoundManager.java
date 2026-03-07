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
    private float masterVolume = 0.5f;
    private float sfxVolume = 0.6f;
    private float uiVolume = 0.45f;
    private float musicVolume = 0.5f;
    private boolean soundEnabled = true;
    private boolean spatialAudioEnabled = true; // Spatial/surround audio panning
    private static final float MAX_PAN = 0.85f; // Max stereo pan (never fully silent in one ear)
    private boolean soundsReady = false; // Track if sounds are preloaded
    
    // Proximity warning hum
    private Clip proximityHumClip; // Looping hum clip for bullet proximity
    private boolean proximityHumPlaying = false;
    private Clip ambientClip; // For looping ambient sound
    private Clip musicClip; // For looping background music (WAV only - convert MP3 to WAV)
    private Clip fadingOutClip; // Second clip for crossfade transitions
    private String currentMusic = null; // Track which music is playing
    private String lastPlayedSound = ""; // Track last played SFX for debug
    private long lastPlayedTime = 0; // When it was played
    private volatile boolean isCrossfading = false; // Track if crossfade is in progress
    private static final int CROSSFADE_DURATION_MS = 2000; // 2 second crossfade
    private static final int CROSSFADE_STEPS = 40; // Number of volume steps during crossfade
    
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
        BOSS_SHOOT_FAST(GAME_PATH + "Weapon/laser/Retro Gun Laser SingleShot 01.wav"),
        BOSS_SHOOT_LARGE(GAME_PATH + "Weapon/various/Retro Missile Launcher 01.wav"),
        BOSS_SHOOT_HOMING(GAME_PATH + "Charge/Retro Charge Electric Off StereoUP 03.wav"),
        BOSS_SHOOT_BOUNCING(GAME_PATH + "Bounce Jump/Retro Jump Classic 08.wav"),
        BOSS_SHOOT_SPIRAL(GAME_PATH + "Electric/Retro Electric Short 17.wav"),
        BOSS_SHOOT_WAVE(GAME_PATH + "Swoosh/Retro Swooosh 16.wav"),
        BOSS_SHOOT_BOMB(GAME_PATH + "Weapon/various/Retro Weapon Bomb 06.wav"),
        BOSS_SHOOT_GRENADE(GAME_PATH + "Weapon/various/Retro Granade Launcher 03.wav"),
        BOSS_SHOOT_NUKE(GAME_PATH + "Weapon/various/Retro Weapon Plasma Type B 03.wav"),
        BOSS_SHOOT_ACCELERATING(GAME_PATH + "Charge/Retro Charge StereoUP 01.wav"),
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
        MENU_OPEN(GAME_PATH + "Events/Retro Event UI 13.wav"),
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
        BOSS_HIT_CONFIRMED(GAME_PATH + "Impact/Retro Impact Metal 36.wav"),
        BOSS_FINAL_HIT(GAME_PATH + "Explosion/Retro Explosion Short 15.wav"),
        BOSS_DEATH(GAME_PATH + "Explosion/Retro Explosion Swoshes 04.wav"),
        BOSS_ROAR(GAME_PATH + "Roar/Retro Roar 02.wav"),
        
        // Item effects
        ITEM_END(GAME_PATH + "Descending/Retro Descending Short 14.wav"),
        
        // Achievements and Milestones
        ACHIEVEMENT_UNLOCKED(GAME_PATH + "Events/Retro Event Acute 11.wav"),
        BOSS_INTRO(GAME_PATH + "HiTech/Retro HiTech 16.wav"),
        COUNTDOWN_TICK(UI_PATH + "JDSherbert - Ultimate UI SFX Pack - Cursor - 1.wav"),
        COUNTDOWN_GO(GAME_PATH + "Ascending/Retro Ascending Short 20.wav"),
        
        // Game Over
        GAME_OVER(GAME_PATH + "Music/Negative/Retro Negative Melody 02 - space voice pad.wav"),
        
        // Ambient/Background
        AMBIENT_BACKGROUND(GAME_PATH + "Ambience/Retro Ambience Stretch Large 01.wav"),
        
        // Proximity warning hum (low-frequency pulse for nearby bullets)
        PROXIMITY_HUM(GAME_PATH + "Ambience/Retro Ambience Stretch Large 01.wav"),
        
        // Flare sounds
        FLARE_DEPLOY(GAME_PATH + "Swoosh/Retro Swooosh 07.wav"),
        FLARE_EXPLODE(GAME_PATH + "Explosion/Retro Explosion Short 01.wav");
        
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
        preloadSounds(null);
    }
    
    public void preloadSounds(java.util.function.IntConsumer progressCallback) {
        // Preload common sounds
        Sound[] allSounds = Sound.values();
        int total = allSounds.length;
        for (int i = 0; i < total; i++) {
            try {
                loadSound(allSounds[i]);
            } catch (Exception e) {
                System.err.println("Failed to preload sound: " + allSounds[i].name() + " - " + e.getMessage());
            }
            if (progressCallback != null && (i % 5 == 0 || i == total - 1)) {
                progressCallback.accept((int)((i + 1) * 100.0 / total));
            }
        }
        soundsReady = true; // Mark sounds as ready
    }
    
    private Clip loadSound(Sound sound) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (soundCache.containsKey(sound.name())) {
            return soundCache.get(sound.name());
        }
        
        try {
            Clip clip = AssetLoader.loadAudioClip(sound.getPath());
            if (clip != null) {
                soundCache.put(sound.name(), clip);
            }
            return clip;
        } catch (Exception e) {
            System.err.println("Sound file not found: " + sound.getPath());
            return null;
        }
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
                                sound.name().startsWith("BOSS_SHOOT") ||
                                sound.name().equals("BOSS_HIT") ||
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
                // Always stop and flush for a clean restart
                // Pooled clips still overlap (up to SOUND_POOL_SIZE-1 simultaneous)
                // since only the reused slot gets stopped on wrap-around
                if (clip.isRunning()) {
                    clip.stop();
                }
                clip.flush();
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
                
                // Skip playing if volume is effectively zero
                if (volume < 0.001f) {
                    return;
                }
                
                setVolume(clip, volume);
                clip.start();
                lastPlayedSound = sound.name();
                lastPlayedTime = System.currentTimeMillis();
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
        try {
            return AssetLoader.loadAudioClip(sound.path);
        } catch (Exception e) {
            System.err.println("Sound file not found: " + sound.path);
            return null;
        }
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
    
    /**
     * Set stereo pan on a clip. Tries PAN (mono) first, then BALANCE (stereo).
     * @param clip The audio clip
     * @param pan Value from -1.0 (full left) to 1.0 (full right)
     */
    private void setPan(Clip clip, float pan) {
        if (clip == null || !spatialAudioEnabled) return;
        pan = Math.max(-1.0f, Math.min(1.0f, pan));
        try {
            if (clip.isControlSupported(FloatControl.Type.PAN)) {
                FloatControl panControl = (FloatControl) clip.getControl(FloatControl.Type.PAN);
                panControl.setValue(pan);
            } else if (clip.isControlSupported(FloatControl.Type.BALANCE)) {
                FloatControl balanceControl = (FloatControl) clip.getControl(FloatControl.Type.BALANCE);
                balanceControl.setValue(pan);
            }
        } catch (Exception e) {
            // Silently ignore - some audio systems don't support panning
        }
    }
    
    /**
     * Calculate pan value from a sound source's X position relative to the world width.
     * @param sourceX The X position of the sound source in world coordinates
     * @param worldWidth The total world width
     * @return Pan value from -MAX_PAN to +MAX_PAN
     */
    private float calculatePan(double sourceX, double worldWidth) {
        if (worldWidth <= 0) return 0.0f;
        // Map sourceX from [0, worldWidth] to [-1, 1], then scale by MAX_PAN
        float normalized = (float)((sourceX / worldWidth) * 2.0 - 1.0);
        return Math.max(-MAX_PAN, Math.min(MAX_PAN, normalized));
    }
    
    /**
     * Play a sound with spatial panning based on the source's screen position.
     * Falls back to normal playSound if spatial audio is disabled.
     * @param sound The sound to play
     * @param volumeMultiplier Volume scaling factor
     * @param sourceX The X position of the sound source in world coordinates
     * @param worldWidth The total world width for pan calculation
     */
    public void playSoundSpatial(Sound sound, float volumeMultiplier, double sourceX, double worldWidth) {
        if (!spatialAudioEnabled) {
            playSound(sound, volumeMultiplier);
            return;
        }
        if (!soundEnabled || !soundsReady) return;
        
        // Apply cooldown for high-frequency sounds
        boolean needsCooldown = sound.name().startsWith("EXPL_") || 
                                sound.name().startsWith("BLOP_") || 
                                sound.name().equals("GRAZE");
        if (needsCooldown) {
            long currentTime = System.currentTimeMillis();
            Long lastPlayTime = soundCooldowns.get(sound.name());
            if (lastPlayTime != null && (currentTime - lastPlayTime) < SOUND_COOLDOWN_MS) {
                return;
            }
            soundCooldowns.put(sound.name(), currentTime);
        }
        
        try {
            Clip clip;
            boolean shouldPool = sound.name().startsWith("UI_") ||
                                sound.name().startsWith("EXPL_") ||
                                sound.name().startsWith("BOSS_SHOOT") ||
                                sound.name().equals("BOSS_HIT") ||
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
                if (clip.isRunning()) {
                    clip.stop();
                }
                clip.flush();
                clip.setFramePosition(0);
                
                // Set volume
                float volume = masterVolume * volumeMultiplier;
                if (sound.name().startsWith("UI_")) {
                    volume *= uiVolume;
                } else {
                    volume *= sfxVolume;
                }
                if (sound == Sound.PAUSE || sound == Sound.UNPAUSE) {
                    volume *= 0.4f;
                }
                if (volume < 0.001f) return;
                
                setVolume(clip, volume);
                
                // Apply spatial panning
                float pan = calculatePan(sourceX, worldWidth);
                setPan(clip, pan);
                
                clip.start();
                lastPlayedSound = sound.name();
                lastPlayedTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            System.err.println("Error playing spatial sound " + sound.name() + ": " + e.getMessage());
        }
    }
    
    /**
     * Start or update the proximity warning hum.
     * Call each frame with the closest bullet distance and its X position.
     * @param closestDistance Distance to nearest bullet (0 = on top of player)
     * @param warningRadius Maximum distance at which hum is audible
     * @param sourceX X position of the closest bullet for panning
     * @param worldWidth World width for pan calculation
     */
    public void updateProximityHum(double closestDistance, double warningRadius, double sourceX, double worldWidth) {
        if (!soundEnabled || !soundsReady || !spatialAudioEnabled) {
            stopProximityHum();
            return;
        }
        
        if (closestDistance > warningRadius || closestDistance <= 0) {
            stopProximityHum();
            return;
        }
        
        // Calculate intensity: 0 at warningRadius, 1 at distance=0
        float intensity = (float)(1.0 - (closestDistance / warningRadius));
        intensity = Math.max(0, Math.min(1, intensity));
        
        // Very subtle volume: max 0.08 * sfx * master
        float volume = intensity * 0.08f * sfxVolume * masterVolume;
        if (volume < 0.001f) {
            stopProximityHum();
            return;
        }
        
        try {
            // Start the hum clip if not already playing
            if (!proximityHumPlaying || proximityHumClip == null || !proximityHumClip.isRunning()) {
                if (proximityHumClip == null) {
                    proximityHumClip = loadSoundClip(Sound.PROXIMITY_HUM);
                }
                if (proximityHumClip != null) {
                    proximityHumClip.setFramePosition(0);
                    proximityHumClip.loop(Clip.LOOP_CONTINUOUSLY);
                    proximityHumPlaying = true;
                }
            }
            
            // Update volume and pan each frame
            if (proximityHumClip != null && proximityHumPlaying) {
                setVolume(proximityHumClip, volume);
                float pan = calculatePan(sourceX, worldWidth);
                setPan(proximityHumClip, pan);
            }
        } catch (Exception e) {
            // Silently ignore proximity hum errors
        }
    }
    
    /**
     * Stop the proximity warning hum.
     */
    public void stopProximityHum() {
        if (proximityHumPlaying && proximityHumClip != null) {
            try {
                proximityHumClip.stop();
            } catch (Exception e) {
                // Ignore
            }
            proximityHumPlaying = false;
        }
    }
    
    public void stopAllSounds() {
        for (Clip clip : soundCache.values()) {
            if (clip != null && clip.isRunning()) {
                clip.stop();
            }
        }
    }
    
    /**
     * Stop a specific sound if it's currently playing.
     * Checks both the main cache and pooled clips.
     */
    public void stopSound(Sound sound) {
        Clip clip = soundCache.get(sound.name());
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
        // Also stop any pooled clips for this sound
        List<Clip> pool = soundPoolCache.get(sound.name());
        if (pool != null) {
            for (Clip pooledClip : pool) {
                if (pooledClip != null && pooledClip.isRunning()) {
                    pooledClip.stop();
                }
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
    public void setMasterVolume(float volume) { 
        this.masterVolume = Math.max(0, Math.min(1, volume)); 
        // Update currently playing music volume
        if (musicClip != null && musicClip.isRunning()) {
            setVolume(musicClip, masterVolume * musicVolume * 1.5f);
        }
    }
    
    public float getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(float volume) { this.sfxVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getUiVolume() { return uiVolume; }
    public void setUiVolume(float volume) { this.uiVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getMusicVolume() { return musicVolume; }
    public void setMusicVolume(float volume) { 
        this.musicVolume = Math.max(0, Math.min(1, volume)); 
        // Update currently playing music volume
        if (musicClip != null && musicClip.isRunning()) {
            setVolume(musicClip, masterVolume * musicVolume * 1.5f);
        }
    }
    
    public boolean isSoundEnabled() { return soundEnabled; }
    public String getLastPlayedSound() { return lastPlayedSound; }
    public long getLastPlayedTime() { return lastPlayedTime; }
    
    public void startAmbientSound() {
        if (!soundEnabled) return;
        
        try {
            if (ambientClip != null && ambientClip.isRunning()) {
                return; // Already playing
            }
            
            ambientClip = loadSound(Sound.AMBIENT_BACKGROUND);
            if (ambientClip != null) {
                ambientClip.loop(Clip.LOOP_CONTINUOUSLY);
                setVolume(ambientClip, masterVolume * sfxVolume * 0.01f); // Barely audible ambient
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
        playMusic(musicPath, CROSSFADE_DURATION_MS);
    }
    
    /**
     * Play music with a fast crossfade (~1 second) - used when entering a level.
     */
    public void playMusicFast(String musicPath) {
        playMusic(musicPath, 1000);
    }
    
    private void playMusic(String musicPath, int crossfadeDurationMs) {
        if (!soundEnabled || !soundsReady) return;
        
        // Convert MP3 path to WAV path automatically
        String wavPath = musicPath.replace(".mp3", ".wav");
        
        // Don't restart if same music is already playing
        if (wavPath.equals(currentMusic) && musicClip != null && musicClip.isRunning()) {
            return;
        }
        
        // If music is currently playing, crossfade to new track
        if (musicClip != null && musicClip.isRunning()) {
            crossfadeToNewTrack(wavPath, crossfadeDurationMs);
        } else {
            // No music playing, just start fresh
            stopMusic();
            startMusicClip(wavPath);
        }
    }
    
    /**
     * Play music immediately, bypassing the soundsReady check.
     * Used for loading screen music before sounds are fully preloaded.
     */
    public void playMusicEarly(String musicPath) {
        if (!soundEnabled) return;
        
        String wavPath = musicPath.replace(".mp3", ".wav");
        
        // Don't restart if same music is already playing
        if (wavPath.equals(currentMusic) && musicClip != null && musicClip.isRunning()) {
            return;
        }
        
        stopMusic();
        startMusicClip(wavPath);
    }
    
    private void startMusicClip(String wavPath) {
        try {
            AudioInputStream audioStream = AssetLoader.getAudioInputStream(wavPath);
            musicClip = AudioSystem.getClip();
            musicClip.open(audioStream);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            setVolume(musicClip, masterVolume * musicVolume * 1.5f);
            currentMusic = wavPath;
        } catch (Exception e) {
            System.err.println("Error playing music: " + e.getMessage());
            System.err.println("Note: Java's built-in audio only supports WAV, AIFF, and AU formats.");
        }
    }
    
    private void crossfadeToNewTrack(String newWavPath, int durationMs) {
        // Move current clip to fading out
        if (fadingOutClip != null) {
            fadingOutClip.stop();
            fadingOutClip.close();
        }
        fadingOutClip = musicClip;
        musicClip = null;
        
        // Start new track at zero volume
        try {
            AudioInputStream audioStream = AssetLoader.getAudioInputStream(newWavPath);
            musicClip = AudioSystem.getClip();
            musicClip.open(audioStream);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            setVolume(musicClip, 0.0001f); // Start nearly silent
            currentMusic = newWavPath;
        } catch (Exception e) {
            System.err.println("Error starting crossfade music: " + e.getMessage());
            // If new track fails, keep the old one playing
            musicClip = fadingOutClip;
            fadingOutClip = null;
            return;
        }
        
        // Perform crossfade in a background thread
        final Clip fadeOut = fadingOutClip;
        final Clip fadeIn = musicClip;
        final float targetVolume = masterVolume * musicVolume * 1.5f;
        
        isCrossfading = true;
        Thread crossfadeThread = new Thread(() -> {
            try {
                int stepDelay = durationMs / CROSSFADE_STEPS;
                for (int i = 1; i <= CROSSFADE_STEPS; i++) {
                    float progress = (float) i / CROSSFADE_STEPS;
                    
                    // Fade out old track
                    if (fadeOut != null && fadeOut.isOpen()) {
                        float fadeOutVol = targetVolume * (1.0f - progress);
                        setVolume(fadeOut, Math.max(0.0001f, fadeOutVol));
                    }
                    
                    // Fade in new track
                    if (fadeIn != null && fadeIn.isOpen()) {
                        float fadeInVol = targetVolume * progress;
                        setVolume(fadeIn, Math.max(0.0001f, fadeInVol));
                    }
                    
                    Thread.sleep(stepDelay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // Clean up the faded-out clip
                if (fadeOut != null) {
                    try {
                        fadeOut.stop();
                        fadeOut.close();
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
                if (fadingOutClip == fadeOut) {
                    fadingOutClip = null;
                }
                isCrossfading = false;
            }
        }, "Music-Crossfade");
        crossfadeThread.setDaemon(true);
        crossfadeThread.start();
    }
    
    public void stopMusic() {
        if (fadingOutClip != null) {
            fadingOutClip.stop();
            fadingOutClip.close();
            fadingOutClip = null;
        }
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
            currentMusic = null;
        }
    }
    
    public String getCurrentMusicName() {
        if (currentMusic == null) return null;
        // Extract just the track name from the full path (e.g. "SFX/Music Tracks/Main/Rock Battle Menu.wav" -> "Rock Battle Menu")
        String name = currentMusic;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) name = name.substring(0, lastDot);
        return name;
    }
    
    public String getCurrentMusic() {
        return currentMusic;
    }
    
    public void setSoundEnabled(boolean enabled) { 
        this.soundEnabled = enabled;
        if (!enabled) {
            stopAmbientSound();
            stopMusic();
            stopAllSounds();
            stopProximityHum();
        }
    }
    
    public boolean isSpatialAudioEnabled() { return spatialAudioEnabled; }
    public void setSpatialAudioEnabled(boolean enabled) { 
        this.spatialAudioEnabled = enabled; 
        if (!enabled) {
            stopProximityHum();
        }
    }
}
