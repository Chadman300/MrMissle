# PowerShell script to convert MP3 files to WAV using ffmpeg
# Make sure ffmpeg is installed: https://ffmpeg.org/download.html

$musicFolder = "SFX\Music Tracks"

# Check if ffmpeg is available
try {
    $null = & ffmpeg -version 2>&1
} catch {
    Write-Host "ERROR: ffmpeg is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install ffmpeg from https://ffmpeg.org/download.html" -ForegroundColor Yellow
    exit 1
}

# Find all MP3 files in the music folder
$mp3Files = Get-ChildItem -Path $musicFolder -Filter "*.mp3" -File

if ($mp3Files.Count -eq 0) {
    Write-Host "No MP3 files found in $musicFolder" -ForegroundColor Yellow
    exit 0
}

Write-Host "Found $($mp3Files.Count) MP3 file(s) to convert" -ForegroundColor Green

foreach ($mp3File in $mp3Files) {
    $wavFile = $mp3File.FullName -replace '\.mp3$', '.wav'
    
    # Skip if WAV already exists
    if (Test-Path $wavFile) {
        Write-Host "Skipping $($mp3File.Name) - WAV already exists" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "Converting $($mp3File.Name)..." -ForegroundColor Cyan
    
    # Convert MP3 to WAV using ffmpeg
    & ffmpeg -i $mp3File.FullName -acodec pcm_s16le -ar 44100 -ac 2 $wavFile -y 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Created $([System.IO.Path]::GetFileName($wavFile))" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Failed to convert $($mp3File.Name)" -ForegroundColor Red
    }
}

Write-Host "`nConversion complete!" -ForegroundColor Green
Write-Host "You can now run the game and the music will play." -ForegroundColor Cyan
