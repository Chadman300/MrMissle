Add-Type -AssemblyName System.Drawing
$base = $PSScriptRoot
$sheet = New-Object System.Drawing.Bitmap("$base\sprites\UI\Keyboard and controller keys\Controller icons\spritesheet\controller sheet.png")
$cellW=96; $cellH=48; $cols=15
$totalCells = [int]($sheet.Width/$cellW) * [int]($sheet.Height/$cellH)

# For each sheet cell, compute average R,G,B of non-background pixels
function Get-Signature($bmp, $cx, $cy, $w, $h) {
    $rSum=0; $gSum=0; $bSum=0; $count=0
    for($y=4; $y -lt ($h-4); $y+=2) {
        for($x=4; $x -lt ($w-4); $x+=2) {
            $px = $bmp.GetPixel($cx+$x, $cy+$y)
            if($px.A -gt 50) {
                $rSum += $px.R; $gSum += $px.G; $bSum += $px.B; $count++
            }
        }
    }
    if($count -eq 0) { return "0,0,0,$count" }
    return "$([math]::Round($rSum/$count)),$([math]::Round($gSum/$count)),$([math]::Round($bSum/$count)),$count"
}

# Build sheet signatures
$sheetSigs = @{}
for($i=0; $i -lt $totalCells; $i++) {
    $c = $i % $cols; $r = [math]::Floor($i / $cols)
    $sheetSigs[$i] = Get-Signature $sheet ($c*$cellW) ($r*$cellH) $cellW $cellH
}

# For each individual file, find best matching sheet cell
foreach($n in 1..96) {
    $path = "$base\sprites\UI\Keyboard and controller keys\Controller icons\sprites\controller button $n.png"
    if(-not (Test-Path $path)) { continue }
    $img = New-Object System.Drawing.Bitmap($path)
    $fSig = Get-Signature $img 0 0 $cellW $cellH
    $img.Dispose()
    
    $fParts = $fSig -split ','
    $fR=[int]$fParts[0]; $fG=[int]$fParts[1]; $fB=[int]$fParts[2]; $fC=[int]$fParts[3]
    
    $bestDist = 999999; $bestIdx = -1
    for($i=0; $i -lt $totalCells; $i++) {
        $sParts = $sheetSigs[$i] -split ','
        $sR=[int]$sParts[0]; $sG=[int]$sParts[1]; $sB=[int]$sParts[2]; $sC=[int]$sParts[3]
        $dist = [math]::Abs($fR-$sR) + [math]::Abs($fG-$sG) + [math]::Abs($fB-$sB) + [math]::Abs($fC-$sC)/10
        if($dist -lt $bestDist) { $bestDist = $dist; $bestIdx = $i }
    }
    $c = $bestIdx % $cols; $r = [math]::Floor($bestIdx / $cols)
    if($bestDist -lt 10) {
        Write-Host "File $n -> grid $bestIdx (row $r col $c) dist=$([math]::Round($bestDist,1))"
    }
}
$sheet.Dispose()
