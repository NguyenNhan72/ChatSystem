"""A second chat user on the PC, speaking the same WebSocket protocol as the Android app.

For the demo backup plan (only one emulator) and for poking the server by hand. Python standard library only.

  python scripts/demo_client.py bob listen           # stay online like a device: heartbeat every 5 s,
                                                      # acknowledge incoming messages as 'delivered'
  python scripts/demo_client.py bob listen --read    # ... as 'read' instead (the sender sees "Đã xem")
  python scripts/demo_client.py bob send Alice "Chào Alice"   # send into the chat whose title contains "Alice"

Ctrl+C in listen mode closes the socket with reason "logout", so the server marks the user offline at once.
The password defaults to the demo password set by scripts/demo-reset.ps1 (override with --password).
"""
import argparse
import base64
import json
import os
import socket
import struct
import sys
import time
import urllib.request
import uuid
from urllib.parse import urlparse

DEMO_PASSWORD = "demopass1"  # gia tri test demo, giong trong scripts/demo-reset.ps1


def http(base, method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, method=method, data=data)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read())


class WebSocket:
    """Minimal RFC 6455 client: text frames, masked as clients must, answers pings."""

    def __init__(self, url, token):
        u = urlparse(url)
        self.s = socket.create_connection((u.hostname, u.port or 80), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall((
            f"GET {u.path or '/'} HTTP/1.1\r\nHost: {u.hostname}:{u.port or 80}\r\n"
            "Upgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
            f"Authorization: Bearer {token}\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            chunk = self.s.recv(1)
            if not chunk:
                raise ConnectionError("connection closed during the handshake")
            head += chunk
        status = head.split(b"\r\n")[0].decode()
        if " 101 " not in status:
            raise ConnectionError("handshake refused: " + status)

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("socket closed")
            buf += chunk
        return buf

    def recv(self):
        """Next JSON event from the server."""
        while True:
            b1, b2 = self._read(2)
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            data, op = self._read(n), b1 & 0x0F
            if op == 1:
                return json.loads(data)
            if op == 9:
                self._frame(data, 0xA)  # gui frame pong phan hoi
            if op == 8:
                raise ConnectionError("server closed the connection")

    def _frame(self, data, op):
        mask, n = os.urandom(4), len(data)
        if n < 126:
            head = bytes([0x80 | op, 0x80 | n])
        elif n < 65536:
            head = bytes([0x80 | op, 0x80 | 126]) + struct.pack(">H", n)
        else:
            head = bytes([0x80 | op, 0x80 | 127]) + struct.pack(">Q", n)
        self.s.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def send(self, event):
        self._frame(json.dumps(event).encode(), 1)

    def close(self, reason):
        self._frame(struct.pack(">H", 1000) + reason.encode(), 8)


def show(ev, names):
    t = ev["type"]
    if t == "message":
        m = ev["message"]
        print(f"  ← tin mới  [{names.get(m['conversationId'], m['conversationId'])}] seq {m['seq']}: {m['content']}")
    elif t == "presence":
        print(f"  ← presence user {ev['userId']}: {'online' if ev['online'] else 'offline'}")
    elif t == "receipt":
        print(f"  ← receipt  user {ev['userId']}: delivered ≤ {ev['deliveredSeq']}, read ≤ {ev['readSeq']}")
    else:
        print("  ←", ev)


def listen(base, token, me, read):
    names = {c["id"]: c["title"] for c in http(base, "GET", "/api/conversations", token=token)}
    ws = WebSocket(base.replace("http", "ws", 1) + "/ws", token)
    print("connected:", ws.recv())
    ack = "read" if read else "delivered"
    # Giong nhu mot thiet bi online: gui xac nhan cho nhung tin nhan dang cho tren server.
    for c in http(base, "GET", "/api/conversations", token=token):
        if c["lastSeq"] and c["lastMessage"]["senderId"] != me:
            ws.send({"type": ack, "conversationId": c["id"], "seq": c["lastSeq"]})
            print(f"  → {ack} [{c['title']}] ≤ seq {c['lastSeq']}")
    # Don gian hoa: timeout doc 1s giua cac nhip heartbeat; phu hop tren localhost.
    ws.s.settimeout(1)
    next_beat = 0.0
    print("online — Ctrl+C để đăng xuất")
    try:
        while True:
            if time.time() >= next_beat:
                ws.send({"type": "heartbeat"})
                next_beat = time.time() + 5
            try:
                ev = ws.recv()
            except TimeoutError:
                continue
            show(ev, names)
            if ev["type"] == "message" and ev["message"]["senderId"] != me:
                m = ev["message"]
                names.setdefault(m["conversationId"], m["conversationId"])
                ws.send({"type": ack, "conversationId": m["conversationId"], "seq": m["seq"]})
                print(f"  → {ack} seq {m['seq']}")
    except KeyboardInterrupt:
        ws.close("logout")
        print("đã đăng xuất (server đánh dấu offline ngay)")


def send(base, token, title, text):
    conv = next((c for c in http(base, "GET", "/api/conversations", token=token) if title.lower() in c["title"].lower()), None)
    if conv is None:
        sys.exit(f"Không có cuộc trò chuyện nào có tên chứa '{title}'.")
    ws = WebSocket(base.replace("http", "ws", 1) + "/ws", token)
    ws.recv()  # nhan event connected
    client_msg_id = str(uuid.uuid4())
    ws.send({"type": "send", "clientMsgId": client_msg_id, "conversationId": conv["id"], "content": text})
    while True:
        ev = ws.recv()
        if ev.get("clientMsgId") == client_msg_id:
            print(f"[{conv['title']}]", ev)
            break
    ws.close("done")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("user")
    p.add_argument("command", choices=["listen", "send"])
    p.add_argument("args", nargs="*", help="send: <conversation title> <text>")
    p.add_argument("--password", default=DEMO_PASSWORD)
    p.add_argument("--server", default=os.environ.get("CHAT_SERVER", "http://localhost:8080"))
    p.add_argument("--read", action="store_true", help="listen: acknowledge as read instead of delivered")
    a = p.parse_args()
    # chatServerUrl khi login tra ve 10.0.2.2 cho emulator; khi chay tu PC ta dung --server http://localhost:8080.
    auth = http(a.server, "POST", "/api/auth/login", {"username": a.user, "password": a.password})
    if a.command == "listen":
        listen(a.server, auth["token"], auth["user"]["id"], a.read)
    else:
        if len(a.args) != 2:
            p.error('send needs: <conversation title> "<text>"')
        send(a.server, auth["token"], a.args[0], a.args[1])


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
