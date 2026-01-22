import socket
import random
import time
import subprocess
import re
import sys

# ================= CONFIG =================
TARGET_SERVICE_NAME = "RFCOMM_Server"

LIMITS = [
    (10, 50),
    (100, 200),
    (0, 1),
    (5, 15),
    (1000, 5000),
    (-50, 50)
]

SEND_INTERVAL = 1  # seconds
# =========================================


def run(cmd):
    return subprocess.run(
        cmd,
        shell=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True
    ).stdout



# ---------- BLUETOOTH HELPERS ----------

def ensure_bluetooth_on():
    try:
        state = run("bluetoothctl show")
        if "Powered: yes" not in state:
            print("🔌 Turning Bluetooth ON...")
            run("bluetoothctl power on")
    except:
        print("❌ Bluetooth not available")
        sys.exit(1)


def get_connected_devices():
    output = run("bluetoothctl devices Connected")
    return [line.split()[1] for line in output.splitlines() if line]


def scan_devices():
    print("🔍 Scanning for devices (8s)...")
    subprocess.Popen("bluetoothctl scan on", shell=True)
    time.sleep(8)
    run("bluetoothctl scan off")
    output = run("bluetoothctl devices")
    return [line.split()[1] for line in output.splitlines() if line]


def is_likely_phone(mac):
    info = run(f"bluetoothctl info {mac}")
    keywords = ["Phone", "Smartphone", "Android", "iPhone"]
    return any(k in info for k in keywords)


def device_has_service(mac, service_name):
    try:
        output = subprocess.check_output(
            ["sdptool", "browse", mac],
            stderr=subprocess.DEVNULL
        ).decode()
        return service_name in output
    except:
        return False


def auto_find_android_mac(service_name):
    print("🧠 Searching for Android device...")

    devices = get_connected_devices()
    if not devices:
        devices = scan_devices()

    for mac in devices:
        if is_likely_phone(mac) and device_has_service(mac, service_name):
            print(f"📱 Found device: {mac}")
            return mac

    return None


def get_rfcomm_channel(mac, service_name):
    print("🕵️  Discovering RFCOMM channel...")
    output = subprocess.check_output(
        ["sdptool", "browse", mac],
        stderr=subprocess.DEVNULL
    ).decode()

    services = output.split("Service Name:")
    for service in services:
        if service_name in service:
            match = re.search(r"Channel:\s*(\d+)", service)
            if match:
                return int(match.group(1))
    return None


# ---------- MAIN ----------

def main():
    print("\n=== AUTOMATIC BLUETOOTH RFCOMM SENDER ===\n")

    ensure_bluetooth_on()

    mac = auto_find_android_mac(TARGET_SERVICE_NAME)
    if not mac:
        print("❌ No Android device running RFCOMM_Server found")
        sys.exit(1)

    channel = get_rfcomm_channel(mac, TARGET_SERVICE_NAME)
    if channel is None:
        print("❌ RFCOMM channel not found")
        sys.exit(1)

    print(f"✅ Using MAC {mac}, Channel {channel}")

    sock = socket.socket(
        socket.AF_BLUETOOTH,
        socket.SOCK_STREAM,
        socket.BTPROTO_RFCOMM
    )

    try:
        print("🔗 Connecting...")
        sock.connect((mac, channel))
        print("🚀 Connected! Sending data...\n")

        while True:
            values = [random.randint(lo, hi) for lo, hi in LIMITS]
            msg = ",".join(map(str, values)) + "\n"
            sock.send(msg.encode())
            print(f"➡️  {msg.strip()}")
            time.sleep(SEND_INTERVAL)

    except KeyboardInterrupt:
        print("\n🛑 Stopped by user")

    except Exception as e:
        print(f"❌ Error: {e}")

    finally:
        sock.close()
        print("🔒 Socket closed")


if __name__ == "__main__":
    main()
