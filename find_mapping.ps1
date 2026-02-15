Add-Type -AssemblyName System.Drawing
$base = $PSScriptRoot
$sheet = New-Object System.Drawing.Bitmap("$base\sprites\UI\Keyboard and controller keys\Controller icons\spritesheet\controller sheet.png")
$cellW=96; $cellH=48; $cols=15
$totalCells = [int]($sheet.Width/$cellW) * [int]($sheet.Height/$cellH)

# Build a simple hash for each sheet cell using sampled pixels
$sheetH = @{}
for($i=0; $i -lt $totalCells; $i++) {
    $c = $i % $cols
    $r = [math]::Floor($i / $cols)
    $cx = $c * $cellW
    $cy = $r * $cellH
    $hash = ""
    # Sample 12 pixels spread across the cell
    foreach($sy in @(8,16,24,32,40)) {
        foreach($sx in @(16,32,48,64,80)) {
            $px = $sheet.GetPixel($cx+$sx, $cy+$sy)
            $hash += "$($px.R),$($px.G),$($px.B),"
        }
    }
    $sheetH[$i] = $hash
}

# Compare each relevant individual file against all sheet cells
foreach($n in @(28,29,30,31,34,35,39,40,41,42,49,50)) {
    $path = "$base\sprites\UI\Keyboard and controller keys\Controller icons\sprites\controller button $n.png"
    $img = New-Object System.Drawing.Bitmap($path)
    $hash = ""
    foreach($sy in @(8,16,24,32,40)) {
        foreach($sx in @(16,32,48,64,80)) {
            $px = $img.GetPixel($sx, $sy)
            $hash += "$($px.R),$($px.G),$($px.B),"
        }
    }
    $img.Dispose()
    
    $found = "NO MATCH"
    for($i=0; $i -lt $totalCells; $i++) {
        if($sheetH[$i] -eq $hash) {
            $c = $i % $cols
            $r = [math]::Floor($i / $cols)
            $found = "grid $i (row $r col $c)"
            break
        }
    }
    Write-Host "File $n -> $found"
}
$sheet.Dispose()
