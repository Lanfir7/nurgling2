param(
    [string]$Source = (Join-Path $PSScriptRoot "..\build\news-icon-comfy.png"),
    [string]$TemplateRoot = (Join-Path $PSScriptRoot "..\resources\src\nurgling\hud\buttons\rbtn\bud"),
    [string]$DestinationRoot = (Join-Path $PSScriptRoot "..\resources\src\nurgling\hud\buttons\rbtn\news")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path $Source).Path
$templatePath = (Resolve-Path $TemplateRoot).Path
$destinationPath = [System.IO.Path]::GetFullPath($DestinationRoot)

# The ComfyUI artwork is cut out from its generated dark background and placed
# inside the untouched native rbtn frame.
$sourceCrop = [System.Drawing.Rectangle]::new(140, 90, 744, 844)
$stateSettings = @{
    u  = @{ Brightness = 1.00; OffsetY = 0 }
    h  = @{ Brightness = 1.16; OffsetY = 0 }
    d  = @{ Brightness = 0.76; OffsetY = 2 }
    dh = @{ Brightness = 0.96; OffsetY = 2 }
}

function New-RoundedPath([System.Drawing.RectangleF]$rectangle, [single]$radius) {
    $diameter = $radius * 2
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc($rectangle.X, $rectangle.Y, $diameter, $diameter, 180, 90)
    $path.AddArc($rectangle.Right - $diameter, $rectangle.Y, $diameter, $diameter, 270, 90)
    $path.AddArc($rectangle.Right - $diameter, $rectangle.Bottom - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($rectangle.X, $rectangle.Bottom - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-ColorMatrix([double]$brightness) {
    return [System.Drawing.Imaging.ColorMatrix]::new(@(
        [single[]]@( $brightness, 0, 0, 0, 0 ),
        [single[]]@( 0, $brightness, 0, 0, 0 ),
        [single[]]@( 0, 0, $brightness, 0, 0 ),
        [single[]]@( 0, 0, 0, 1, 0 ),
        [single[]]@( 0, 0, 0, 0, 1 )
    ))
}

$sourceImage = [System.Drawing.Bitmap]::FromFile($sourcePath)
try {
    $cutout = [System.Drawing.Bitmap]::new(70, 70, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $cutoutGraphics = [System.Drawing.Graphics]::FromImage($cutout)
    try {
        $cutoutGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $cutoutGraphics.DrawImage($sourceImage, [System.Drawing.Rectangle]::new(0, 0, 70, 70),
            $sourceCrop.X, $sourceCrop.Y, $sourceCrop.Width, $sourceCrop.Height,
            [System.Drawing.GraphicsUnit]::Pixel)
    } finally {
        $cutoutGraphics.Dispose()
    }
    # Build a soft alpha matte from brightness and warm-brown color contrast.
    # This removes the neutral ComfyUI backdrop while retaining the dark ink outline.
    for($y = 0; $y -lt $cutout.Height; $y++) {
        for($x = 0; $x -lt $cutout.Width; $x++) {
            $pixel = $cutout.GetPixel($x, $y)
            $luma = (0.299 * $pixel.R) + (0.587 * $pixel.G) + (0.114 * $pixel.B)
            $warm = (($pixel.R - $pixel.B) * 2) - 20
            $alpha = [Math]::Max(0, [Math]::Min(255, [Math]::Round(([Math]::Max($luma - 60, $warm)) * 5)))
            $cutout.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, $pixel.R, $pixel.G, $pixel.B))
        }
    }

    foreach($state in @("u", "h", "d", "dh")) {
        $templateFile = Join-Path $templatePath "$state.res\image\image_0.png"
        $destinationDir = Join-Path $destinationPath "$state.res\image"
        [System.IO.Directory]::CreateDirectory($destinationDir) | Out-Null

        $template = [System.Drawing.Bitmap]::FromFile($templateFile)
        $output = [System.Drawing.Bitmap]$template.Clone()
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($output)
            try {
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
                $insidePath = New-RoundedPath ([System.Drawing.RectangleF]::new(7, 7, 76, 76)) 2
                try {
                    # Keep the original outer orange rim and brown/grey state palette.
                    # This clean sample is outside the heart artwork in the template.
                    $insideBrush = [System.Drawing.SolidBrush]::new($template.GetPixel(10, 10))
                    try {
                        $graphics.FillPath($insideBrush, $insidePath)
                    } finally {
                        $insideBrush.Dispose()
                    }
                } finally {
                    $insidePath.Dispose()
                }
                $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
                try {
                    $attributes.SetColorMatrix((New-ColorMatrix $stateSettings[$state].Brightness))
                    $destination = [System.Drawing.Rectangle]::new(
                        7, 7 + $stateSettings[$state].OffsetY,
                        76, 76 - $stateSettings[$state].OffsetY)
                    $graphics.DrawImage($cutout, $destination, 0, 0, 70, 70, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
                } finally {
                    $attributes.Dispose()
                }
            } finally {
                $graphics.Dispose()
            }
            # Match the exact 90x90 silhouette of every neighboring rbtn icon.
            for($y = 0; $y -lt 90; $y++) {
                for($x = 0; $x -lt 90; $x++) {
                    $pixel = $output.GetPixel($x, $y)
                    $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
                        $template.GetPixel($x, $y).A, $pixel.R, $pixel.G, $pixel.B))
                }
            }
            $output.Save((Join-Path $destinationDir "image_0.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $output.Dispose()
            $template.Dispose()
        }
    }
} finally {
    if($null -ne $cutout) { $cutout.Dispose() }
    $sourceImage.Dispose()
}
