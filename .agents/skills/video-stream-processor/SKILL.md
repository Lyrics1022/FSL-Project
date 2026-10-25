---
name: video-stream-processor
description: >-
  Use this skill when capturing real-time camera feeds, RTSP video streams, processing video files frame-by-frame, or overlaying visual HUD elements.
license: Apache-2.0
compatibility: requires-opencv-python
---

# Video Stream Processor

Thread-safe, low-latency video capture and processing runbook.

## Core Protocols

### 1. Threaded Video Capture (Eliminate OS Buffer Lag)
Standard `cv2.VideoCapture.read()` can lag behind when processing takes longer than frame interval. Always read in a background daemon thread:

```python
import cv2
import threading
import queue

class ThreadedCamera:
    def __init__(self, src=0):
        self.cap = cv2.VideoCapture(src)
        self.q = queue.Queue(maxsize=2)
        self.running = True
        self.thread = threading.Thread(target=self._reader, daemon=True)
        self.thread.start()

    def _reader(self):
        while self.running:
            ret, frame = self.cap.read()
            if not ret:
                break
            if not self.q.empty():
                try:
                    self.q.get_nowait()  # Drop stale frame
                except queue.Empty:
                    pass
            self.q.put(frame)

    def read(self):
        return self.q.get()

    def release(self):
        self.running = False
        self.thread.join(timeout=1.0)
        self.cap.release()
```

### 2. HUD Drawing & Real-Time Stats
- Always draw text with drop-shadow or bounding background for legibility against dynamic backgrounds.
- Monitor and display moving average FPS.
