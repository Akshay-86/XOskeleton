import socket
import json
import time
import random
import subprocess
import re
import framing  # Make sure framing.py is in the same folder

def get_local_bluetooth_mac():
    try:
        result = subprocess.check_output(["hcitool", "dev"], text=True)
        macs = re.findall(r"([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2})", result, re.IGNORECASE)
        if macs: return macs[0]
    except: pass
    return "00:00:00:00:00:00"

def main():
    print("=== PC SERVER (Framed Data) ===")
    local_mac = get_local_bluetooth_mac()
    print(f"🔹 Local MAC: {local_mac}")

    server_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    try:
        server_sock.bind((local_mac, 1)) 
        print(f"✅ Bound to Port 1")
    except Exception as e:
        print(f"❌ Bind failed: {e}")
        return

    server_sock.listen(1)
    print("⏳ Waiting for Android...")

    client_sock, address = server_sock.accept()
    print(f"🚀 Connected: {address}")

    try:
        while True:
            # 1. Prepare JSON Data (Double quotes required for JSON)
            data = '{ "id": "sdgh", "val": ' + str(random.randint(0, 100)) + ', "motor_temp": ' + str(round(random.uniform(30.0, 45.0), 2)) + ' }'
            
            # 2. Convert string to bytes
            payload_bytes = data.encode('utf-8')

            # 3. ENCODE WITH FRAMING (Adds the 2-byte length header)
            # This uses the function from framing.py
            framed_message = framing.encode(payload_bytes)

            # 4. Send the framed message
            client_sock.send(framed_message)
            
            print(f"Sent: {data}")
            time.sleep(1)

    except Exception as e:
        print(f"❌ Error: {e}")
        client_sock.close()
        server_sock.close()

if __name__ == "__main__":
    main()