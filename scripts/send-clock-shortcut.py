"""Inject only the clock's chord; no screen capture or unrelated key logging."""
import argparse
import ctypes
import json
import time

parser = argparse.ArgumentParser()
parser.add_argument('--alternate', action='store_true')
parser.add_argument('--count', type=int, default=1)
parser.add_argument('--hold-ms', type=int, default=40)
parser.add_argument('--gap-ms', type=int, default=60)
parser.add_argument('--hold-modifiers', action='store_true')
args = parser.parse_args()
assert 1 <= args.count <= 100 and 10 <= args.hold_ms <= 2000 and 10 <= args.gap_ms <= 2000
user32 = ctypes.windll.user32
modifiers = [0x11, 0x12, 0x10] if args.alternate else [0x5B, 0x12]
letter = 0x5A if args.alternate else 0x58


def send(key, up=False):
    user32.keybd_event(key, 0, 2 if up else 0, 0)


sent = []
try:
    if args.hold_modifiers:
        for key in modifiers:
            send(key)
    for index in range(args.count):
        if not args.hold_modifiers:
            for key in modifiers:
                send(key)
        sent.append(time.time_ns() // 1_000_000)
        send(letter)
        time.sleep(args.hold_ms / 1000)
        send(letter, True)
        if not args.hold_modifiers:
            for key in reversed(modifiers):
                send(key, True)
        time.sleep(args.gap_ms / 1000)
finally:
    send(letter, True)
    for key in reversed(modifiers):
        send(key, True)
print(json.dumps({'sent': len(sent), 'timesMs': sent}))
