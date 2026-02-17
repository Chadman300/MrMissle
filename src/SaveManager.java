import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SaveManager handles saving and loading game data to/from disk.
 * Supports unlimited save slots with save, load, delete, and exists operations.
 */
public class SaveManager {
    private static final String SAVE_DIRECTORY = "saves";
    private static final String SAVE_FILE_PREFIX = "save_slot_";
    private static final String SAVE_FILE_EXTENSION = ".dat";
    
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
        } catch (java.io.InvalidClassException e) {
            System.err.println("Save file in slot " + slot + " is incompatible (outdated format). Deleting it.");
            delete(slot);
            return null;
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
                slot,
                data.saveName,
                data.saveTimestamp,
                data.creationTimestamp,
                data.maxUnlockedLevel,
                data.totalMoney,
                data.totalRunsCompleted,
                data.bestRunLevel,
                data.totalBossesDefeated,
                data.gameMode
            );
        } catch (java.io.InvalidClassException e) {
            System.err.println("Save in slot " + slot + " is incompatible (outdated format). Deleting it.");
            delete(slot);
            return null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to read save metadata: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get all existing save slots by scanning the saves directory.
     * Returns sorted list of slot numbers.
     */
    public List<Integer> getAllSaveSlots() {
        List<Integer> slots = new ArrayList<>();
        File dir = new File(SAVE_DIRECTORY);
        if (!dir.exists() || !dir.isDirectory()) return slots;
        
        File[] files = dir.listFiles((d, name) -> 
            name.startsWith(SAVE_FILE_PREFIX) && name.endsWith(SAVE_FILE_EXTENSION));
        
        if (files != null) {
            for (File f : files) {
                try {
                    String numPart = f.getName()
                        .replace(SAVE_FILE_PREFIX, "")
                        .replace(SAVE_FILE_EXTENSION, "");
                    int slot = Integer.parseInt(numPart);
                    if (slot >= 1) {
                        slots.add(slot);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        Collections.sort(slots);
        return slots;
    }
    
    /**
     * Get metadata for all existing saves, sorted by slot number.
     */
    public List<SaveMetadata> getAllSaveMetadata() {
        List<SaveMetadata> result = new ArrayList<>();
        for (int slot : getAllSaveSlots()) {
            SaveMetadata meta = getSaveMetadata(slot);
            if (meta != null) {
                result.add(meta);
            }
        }
        return result;
    }
    
    /**
     * Find the next available (unused) slot number.
     */
    public int getNextAvailableSlot() {
        List<Integer> existing = getAllSaveSlots();
        int next = 1;
        for (int slot : existing) {
            if (slot == next) {
                next++;
            } else {
                break; // Found a gap
            }
        }
        return next;
    }
    
    /**
     * Get the number of existing saves.
     */
    public int getSaveCount() {
        return getAllSaveSlots().size();
    }
    
    /**
     * Get the path to a save file
     */
    private String getSaveFilePath(int slot) {
        return SAVE_DIRECTORY + File.separator + SAVE_FILE_PREFIX + slot + SAVE_FILE_EXTENSION;
    }
    
    /**
     * Check if a slot number is valid (any positive integer)
     */
    private boolean isValidSlot(int slot) {
        return slot >= 1;
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
        return !getAllSaveSlots().isEmpty();
    }
    
    /**
     * Metadata class for save file information
     */
    public static class SaveMetadata {
        public final int slotNumber;
        public final String saveName;
        public final long timestamp;
        public final long creationTimestamp;
        public final int maxLevel;
        public final int totalMoney;
        public final int totalRuns;
        public final int bestRunLevel;
        public final int totalBosses;
        public final GameMode gameMode;
        
        public SaveMetadata(int slotNumber, String saveName, long timestamp, long creationTimestamp,
                          int maxLevel, int totalMoney, int totalRuns, int bestRunLevel, 
                          int totalBosses, GameMode gameMode) {
            this.slotNumber = slotNumber;
            this.saveName = saveName;
            this.timestamp = timestamp;
            this.creationTimestamp = creationTimestamp > 0 ? creationTimestamp : timestamp;
            this.maxLevel = maxLevel;
            this.totalMoney = totalMoney;
            this.totalRuns = totalRuns;
            this.bestRunLevel = bestRunLevel;
            this.totalBosses = totalBosses;
            this.gameMode = gameMode != null ? gameMode : GameMode.MASTER;
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
            return sdf.format(new Date(timestamp));
        }
        
        public String getFormattedCreationDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            return sdf.format(new Date(creationTimestamp));
        }
        
        public String getSummary() {
            return String.format("Level %d | $%d | %d Runs", 
                maxLevel, totalMoney, totalRuns);
        }
    }
}
