import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * SaveManager handles saving and loading game data to/from disk.
 * Supports 3 save slots with save, load, delete, and exists operations.
 */
public class SaveManager {
    private static final String SAVE_DIRECTORY = "saves";
    private static final String SAVE_FILE_PREFIX = "save_slot_";
    private static final String SAVE_FILE_EXTENSION = ".dat";
    private static final int MAX_SAVE_SLOTS = 3;
    
    private int currentSaveSlot = -1; // Currently active save slot
    
    public SaveManager() {
        // Create saves directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(SAVE_DIRECTORY));
        } catch (IOException e) {
            System.err.println("Failed to create save directory: " + e.getMessage());
        }
    }
    
    /**
     * Save game data to the specified slot
     */
    public boolean save(int slot, SaveData data) {
        if (!isValidSlot(slot)) {
            System.err.println("Invalid save slot: " + slot);
            return false;
        }
        
        String filePath = getSaveFilePath(slot);
        
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            // Update timestamp
            data.saveTimestamp = System.currentTimeMillis();
            oos.writeObject(data);
            currentSaveSlot = slot;
            System.out.println("Game saved to slot " + slot);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to save game: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Load game data from the specified slot
     */
    public SaveData load(int slot) {
        if (!isValidSlot(slot)) {
            System.err.println("Invalid save slot: " + slot);
            return null;
        }
        
        String filePath = getSaveFilePath(slot);
        
        if (!saveExists(slot)) {
            System.out.println("No save file found in slot " + slot);
            return null;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            SaveData data = (SaveData) ois.readObject();
            currentSaveSlot = slot;
            System.out.println("Game loaded from slot " + slot);
            return data;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load game: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Delete the save file in the specified slot
     */
    public boolean delete(int slot) {
        if (!isValidSlot(slot)) {
            System.err.println("Invalid save slot: " + slot);
            return false;
        }
        
        String filePath = getSaveFilePath(slot);
        
        try {
            Files.deleteIfExists(Paths.get(filePath));
            if (currentSaveSlot == slot) {
                currentSaveSlot = -1;
            }
            System.out.println("Save file deleted from slot " + slot);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete save file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a save file exists in the specified slot
     */
    public boolean saveExists(int slot) {
        if (!isValidSlot(slot)) {
            return false;
        }
        return Files.exists(Paths.get(getSaveFilePath(slot)));
    }
    
    /**
     * Get save metadata without fully loading the save
     */
    public SaveMetadata getSaveMetadata(int slot) {
        if (!saveExists(slot)) {
            return null;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(getSaveFilePath(slot)))) {
            SaveData data = (SaveData) ois.readObject();
            return new SaveMetadata(
                data.saveName,
                data.saveTimestamp,
                data.maxUnlockedLevel,
                data.totalMoney,
                data.totalRunsCompleted,
                data.bestRunLevel,
                data.totalBossesDefeated
            );
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to read save metadata: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the path to a save file
     */
    private String getSaveFilePath(int slot) {
        return SAVE_DIRECTORY + File.separator + SAVE_FILE_PREFIX + slot + SAVE_FILE_EXTENSION;
    }
    
    /**
     * Check if a slot number is valid
     */
    private boolean isValidSlot(int slot) {
        return slot >= 1 && slot <= MAX_SAVE_SLOTS;
    }
    
    /**
     * Get the currently active save slot
     */
    public int getCurrentSaveSlot() {
        return currentSaveSlot;
    }
    
    /**
     * Set the current save slot (for when loading from a specific slot)
     */
    public void setCurrentSaveSlot(int slot) {
        if (isValidSlot(slot)) {
            this.currentSaveSlot = slot;
        }
    }
    
    /**
     * Get the maximum number of save slots
     */
    public int getMaxSaveSlots() {
        return MAX_SAVE_SLOTS;
    }
    
    /**
     * Auto-save to the current slot
     */
    public boolean autoSave(SaveData data) {
        if (currentSaveSlot == -1) {
            System.err.println("No save slot selected for auto-save");
            return false;
        }
        return save(currentSaveSlot, data);
    }
    
    /**
     * Check if any save files exist
     */
    public boolean hasSaveFiles() {
        for (int i = 1; i <= MAX_SAVE_SLOTS; i++) {
            if (saveExists(i)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Metadata class for save file information
     */
    public static class SaveMetadata {
        public final String saveName;
        public final long timestamp;
        public final int maxLevel;
        public final int totalMoney;
        public final int totalRuns;
        public final int bestRunLevel;
        public final int totalBosses;
        
        public SaveMetadata(String saveName, long timestamp, int maxLevel, 
                          int totalMoney, int totalRuns, int bestRunLevel, int totalBosses) {
            this.saveName = saveName;
            this.timestamp = timestamp;
            this.maxLevel = maxLevel;
            this.totalMoney = totalMoney;
            this.totalRuns = totalRuns;
            this.bestRunLevel = bestRunLevel;
            this.totalBosses = totalBosses;
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
            return sdf.format(new Date(timestamp));
        }
        
        public String getSummary() {
            return String.format("Level %d | $%d | %d Runs", 
                maxLevel, totalMoney, totalRuns);
        }
    }
}
