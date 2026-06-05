#!/usr/bin/env python3

# requires: websocket-client, opencv-python (optional for display)

import argparse
import os

try:
    import websocket
except ImportError as exc:
    raise SystemExit(
        "Missing dependency: websocket-client. Install with: pip install websocket-client"
    ) from exc

try:
    import cv2
    import numpy as np
except ImportError:
    cv2 = None
    np = None

JPEG_MAGIC = b"\xFF\xD8"


def is_jpeg(data: bytes) -> bool:
    return len(data) >= 2 and data[:2] == JPEG_MAGIC


class MediaStreamClient:
    def __init__(self, url: str, out_dir: str = "frames", latest_only: bool = False, display: bool = False):
        self.url = url
        self.out_dir = out_dir
        self.latest_only = latest_only
        self.display = display

        if self.out_dir:
            os.makedirs(self.out_dir, exist_ok=True)
            
        self.ws = None

    def on_open(self, ws):
        print("Connected.")
        # If your app expects a command, you can send it here:
        # ws.send("start_stream")

    def on_message(self, ws, message):
        if isinstance(message, str):
            # Text message from server
            print(f"Text: {message}")
            return

        if not isinstance(message, (bytes, bytearray)):
            return

        if is_jpeg(message):
            # if self.latest_only:
            #     filename = os.path.join(self.out_dir, "latest.jpg")
            # else:
            #     ts = time.strftime("%Y%m%d_%H%M%S")
            #     filename = os.path.join(self.out_dir, f"frame_{ts}_{int(time.time() * 1000) % 1000:03d}.jpg")
            # with open(filename, "wb") as f:
            #     f.write(message)
            # print(f"Saved: {filename} ({len(message)} bytes)")

            if self.display:
                if cv2 is None or np is None:
                    print("OpenCV is required for display. Install with: pip install opencv-python")
                    ws.close()
                    return
                frame = cv2.imdecode(np.frombuffer(message, dtype=np.uint8), cv2.IMREAD_COLOR)
                if frame is not None:
                    cv2.imshow("MediaServerApp", frame)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        ws.close()
        else:
            # Audio PCM or other binary data; ignore by default
            pass

    def on_error(self, ws, error):
        print(f"Error: {error}")

    def on_close(self, ws, close_status_code, close_msg):
        print("Disconnected.")
        if self.display and cv2 is not None:
            cv2.destroyAllWindows()

    def run(self):
        self.ws = websocket.WebSocketApp(
            self.url,
            on_open=self.on_open,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close,
        )
        self.ws.run_forever()


def main() -> None:
    parser = argparse.ArgumentParser(description="Receive JPEG frames from MediaServerApp WebSocket.")
    parser.add_argument("--url", required=True, help="WebSocket URL, e.g. ws://192.168.123.180:8080")
    parser.add_argument("--out", default="frames", help="Output directory for saved JPEGs")
    parser.add_argument("--latest", action="store_true", help="Only keep latest.jpg (overwrite)")
    parser.add_argument("--display", action="store_true", help="Show frames in a window")
    args = parser.parse_args()

    client = MediaStreamClient(
        url=args.url,
        out_dir=args.out,
        latest_only=args.latest,
        display=args.display
    )
    client.run()


if __name__ == "__main__":
    main()
