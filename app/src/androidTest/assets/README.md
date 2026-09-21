# Playback fixture

`playback-test.mp4` is a synthetic 20-second, 160×90 H.264/AAC clip generated for
`PlaybackDeviceTest`. It contains test-pattern video and silent audio, with no
third-party content. It is packaged only in the instrumentation test APK.

Recreate with FFmpeg:

```powershell
ffmpeg -f lavfi -i 'testsrc2=size=160x90:rate=10' -f lavfi -i 'anullsrc=r=22050:cl=mono' -t 20 -c:v libx264 -preset fast -crf 35 -pix_fmt yuv420p -c:a aac -b:a 16k -movflags +faststart playback-test.mp4
```
