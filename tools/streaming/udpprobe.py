#!/usr/bin/env python3
"""Paced UDP stream between two of my own machines, to measure loss and delay spikes in one direction.

  server (accepts inbound):  udpprobe.py serve PORT
  client:                    udpprobe.py down HOST PORT MBIT SECS   server sends, client measures
                             udpprobe.py up   HOST PORT MBIT SECS   client sends, server measures
"""
import json
import socket
import struct
import sys
import time

SIZE = 1200
HDR = struct.Struct("!4sId")


def send_stream(sock, peer, mbit, secs):
    pps = mbit * 1e6 / 8 / SIZE
    pad = b"\0" * (SIZE - HDR.size)
    start = time.monotonic()
    seq = 0
    while True:
        now = time.monotonic()
        if now - start >= secs:
            break
        due = int((now - start) * pps)
        while seq < due:
            sock.sendto(HDR.pack(b"DATA", seq, time.monotonic()) + pad, peer)
            seq += 1
        time.sleep(0.0005)
    for _ in range(5):
        sock.sendto(HDR.pack(b"FIN!", seq, 0.0), peer)
        time.sleep(0.02)
    return seq


def recv_stream(sock, secs):
    got = []          # (seq, sent, arrived)
    total = None
    sock.settimeout(secs + 5)
    end = time.monotonic() + secs + 5
    try:
        while time.monotonic() < end:
            data, _ = sock.recvfrom(2048)
            if len(data) < HDR.size:
                continue
            kind, seq, sent = HDR.unpack_from(data)
            if kind == b"FIN!":
                total = seq
                break
            if kind == b"DATA":
                got.append((seq, sent, time.monotonic()))
    except socket.timeout:
        pass
    return stats(got, total)


def pct(xs, p):
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0.0


def stats(got, total):
    if not got:
        return {"received": 0}
    total = total or (max(g[0] for g in got) + 1)
    # One-way delay above the best packet: the clock offset between machines cancels out.
    d = [a - s for _, s, a in got]
    base = min(d)
    extra = sorted((x - base) * 1000 for x in d)
    gaps = sorted((got[i][2] - got[i - 1][2]) * 1000 for i in range(1, len(got)))
    reorder = sum(1 for i in range(1, len(got)) if got[i][0] < got[i - 1][0])
    # Stalls: runs of time where delay stays above 30 ms.
    stalls, in_stall = 0, False
    for x in ((a - s - base) * 1000 for _, s, a in got):
        if x > 30 and not in_stall:
            stalls += 1
        in_stall = x > 30
    secs = got[-1][2] - got[0][2]
    return {
        "sent": total, "received": len(got), "loss_pct": round(100 * (1 - len(got) / total), 3),
        "mbit": round(len(got) * SIZE * 8 / max(secs, 1e-3) / 1e6, 1),
        "delay_ms": {"p50": round(pct(extra, .5), 1), "p95": round(pct(extra, .95), 1), "p99": round(pct(extra, .99), 1), "max": round(extra[-1], 1)},
        "over_16ms_pct": round(100 * sum(1 for x in extra if x > 16) / len(extra), 2),
        "over_50ms_pct": round(100 * sum(1 for x in extra if x > 50) / len(extra), 2),
        "stalls_over_30ms": stalls, "max_gap_ms": round(gaps[-1], 1) if gaps else 0, "reordered": reorder,
    }


def serve(port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4 << 20)
    sock.bind(("0.0.0.0", port))
    while True:
        sock.settimeout(None)
        data, peer = sock.recvfrom(2048)
        if data.startswith(b"SEND "):
            _, mbit, secs = data.split()
            send_stream(sock, peer, float(mbit), float(secs))
        elif data.startswith(b"RECV "):
            secs = float(data.split()[1])
            sock.sendto(b"OK", peer)
            out = json.dumps(recv_stream(sock, secs)).encode()
            for _ in range(3):
                sock.sendto(b"STAT" + out, peer)
        elif data == b"QUIT":
            return


def client(mode, host, port, mbit, secs):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4 << 20)
    peer = (host, port)
    if mode == "down":
        sock.sendto(b"SEND %g %g" % (mbit, secs), peer)
        print(json.dumps(recv_stream(sock, secs)))
    else:
        sock.sendto(b"RECV %g" % secs, peer)
        sock.settimeout(3)
        sock.recvfrom(64)
        send_stream(sock, peer, mbit, secs)
        sock.settimeout(10)
        while True:
            data, _ = sock.recvfrom(4096)
            if data.startswith(b"STAT"):
                print(data[4:].decode())
                return


if __name__ == "__main__":
    if sys.argv[1] == "serve":
        serve(int(sys.argv[2]))
    elif sys.argv[1] == "quit":
        socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(b"QUIT", (sys.argv[2], int(sys.argv[3])))
    else:
        client(sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5]))
