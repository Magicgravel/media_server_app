#!/usr/bin/env python3

# requires: websocket-client, opencv-python (optional for display)

import argparse
import os
import time

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


def main() -> None:
    parser = argparse.ArgumentParser(description="Receive JPEG frames from MediaServerApp WebSocket.")
    parser.add_argument("--url", required=True, help="WebSocket URL, e.g. ws://192.168.123.180:8080")
    parser.add_argument("--out", default="frames", help="Output directory for saved JPEGs")
    parser.add_argument("--latest", action="store_true", help="Only keep latest.jpg (overwrite)")
    parser.add_argument("--display", action="store_true", help="Show frames in a window")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)

    def on_open(ws):
        print("Connected.")
        # If your app expects a command, you can send it here:
        # ws.send("start_stream")

    def on_message(ws, message):
        if isinstance(message, str):
            # Text message from server
            print(f"Text: {message}")
            return

        if not isinstance(message, (bytes, bytearray)):
            return

        if is_jpeg(message):
            # if args.latest:
            #     filename = os.path.join(args.out, "latest.jpg")
            # else:
            #     ts = time.strftime("%Y%m%d_%H%M%S")
            #     filename = os.path.join(args.out, f"frame_{ts}_{int(time.time() * 1000) % 1000:03d}.jpg")
            # with open(filename, "wb") as f:
            #     f.write(message)
            # print(f"Saved: {filename} ({len(message)} bytes)")

            if args.display:
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

    def on_error(ws, error):
        print(f"Error: {error}")

    def on_close(ws, close_status_code, close_msg):
        print("Disconnected.")
        if args.display and cv2 is not None:
            cv2.destroyAllWindows()

    ws = websocket.WebSocketApp(
        args.url,
        on_open=on_open,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
    )

    ws.run_forever()


if __name__ == "__main__":
    main()
