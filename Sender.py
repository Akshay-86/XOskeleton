import socket
import json
import time
import random
import subprocess
import re

def get_local_bluetooth_mac():
    """
    Dynamically fetches the MAC address of the first available 
    Bluetooth controller (hci0) using the 'hcitool' command.
    """
    try:
        # Run 'hcitool dev' to list devices
        result = subprocess.check_output(["hcitool", "dev"], text=True)
        
        # Look for a MAC address pattern (XX:XX:XX:XX:XX:XX)
        # The output usually looks like: "Devices:\n    hci0    00:1A:7D:DA:71:13"
        macs = re.findall(r"([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2})", result, re.IGNORECASE)
        
        if macs:
            return macs[0] # Return the first one found
    except Exception as e:
        print(f"⚠️ Could not auto-detect MAC: {e}")
    
    # Fallback to "Any" address if detection fails
    return "00:00:00:00:00:00"

def main():
    print("=== PC SERVER (Port 1) ===")
    
    # 1. Dynamically get the Local MAC
    local_mac = get_local_bluetooth_mac()
    print(f"🔹 Local Bluetooth MAC: {local_mac}")

    # 2. Create RFCOMM Socket
    server_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    
    # 3. Bind to the dynamic MAC on Port 1
    try:
        server_sock.bind((local_mac, 1)) 
        print(f"✅ Bound to Port 1")
    except Exception as e:
        print(f"❌ Could not bind: {e}")
        print("   Try running: sudo sdptool add SP")
        return

    server_sock.listen(1)
    print("⏳ Waiting for Android to connect...")

    client_sock, address = server_sock.accept()
    print(f"🚀 Android Connected: {address}")

    try:
        while True:
            data = {
                "status": "connected",
                "val": random.randint(0, 100),
                "motor_temp": random.uniform(30.0, 45.0)
            }
            # Send JSON + Newline
            msg = json.dumps(data) + "\n"
            client_sock.send(msg.encode())
            print(f"Sent: {msg.strip()}")
            time.sleep(1)
            
    except Exception as e:
        print(f"Error: {e}")
        client_sock.close()
        server_sock.close()

if __name__ == "__main__":
    main()