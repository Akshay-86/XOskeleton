import socket
import random
import time
import subprocess
import re

# --- CONFIGURATION ---
ANDROID_MAC = "70:28:04:db:55:bc"
TARGET_SERVICE_NAME = "RFCOMM_Server"  # The name defined in your Android App

LIMITS = [
    (10, 50), (100, 200), (0, 1), (5, 15), (1000, 5000), (-50, 50)
]

def get_channel_auto(mac, service_name):
    """
    Runs 'sdptool browse' and parses the output to find the channel 
    for the specific Service Name.
    """
    print(f"🕵️  Scanning services on {mac}...")
    
    try:
        # Run the Linux sdptool command
        output = subprocess.check_output(["sdptool", "browse", mac], stderr=subprocess.STDOUT).decode()
    except Exception as e:
        print(f"❌ Error running sdptool: {e}")
        return None

    # We need to find the specific block for "RFCOMM_Server"
    # The output is separated into blocks. We split by "Service Name:"
    services = output.split("Service Name:")
    
    for service in services:
        # Check if this block is the one we want
        if service_name in service:
            # Look for "Channel: X" inside this specific block
            match = re.search(r"Channel:\s*(\d+)", service)
            if match:
                return int(match.group(1))
    
    return None

# --- MAIN PROGRAM ---

print("--- AUTOMATIC BLUETOOTH SENDER ---")

# 1. Find the channel automatically
channel = get_channel_auto(ANDROID_MAC, TARGET_SERVICE_NAME)

if channel is None:
    print(f"❌ Could not find service '{TARGET_SERVICE_NAME}' running on phone.")
    print("   Check: Is the App open? Is Bluetooth ON?")
    exit()

print(f"✅ Found '{TARGET_SERVICE_NAME}' on Channel {channel}")

# 2. Connect using the discovered channel
sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)

try:
    print("Connecting...")
    sock.connect((ANDROID_MAC, channel))
    print("🚀 Connected! Sending data...")

    while True:
        values = [random.randint(lo, hi) for lo, hi in LIMITS]
        msg = ",".join(map(str, values)) + "\n"
        sock.send(msg.encode())
        print(f"Sent to Ch{channel}: {msg.strip()}")
        time.sleep(1)

except ConnectionRefusedError:
    print("❌ Connection Refused. The app might have closed the socket.")
except KeyboardInterrupt:
    print("\nStopping...")
finally:
    sock.close()