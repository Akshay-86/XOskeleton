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
    print("=== PC SERVER (Large Data Test) ===")
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
            # 1. Create a large dictionary with random values
            large_data_packet = {
                "system": {
                    "id": "EXO_PRO_V2",
                    "uptime_sec": int(time.time()), 
                    "mode": random.choice(["ASSIST", "RESIST", "WALK", "RUN", "IDLE"]),
                    "battery": {
                        "level": random.randint(10, 100),
                        "voltage": round(random.uniform(44.0, 52.0), 2),
                        "current": round(random.uniform(0.5, 12.0), 2),
                        "is_charging": random.choice([True, False])
                    }
                },
                "sensors": {
                    "imu": {
                        "pitch": round(random.uniform(-10.0, 10.0), 2),
                        "roll": round(random.uniform(-5.0, 5.0), 2),
                        "yaw": round(random.uniform(0.0, 360.0), 1)
                    },
                    "environment": {
                        "temp_ambient": round(random.uniform(20.0, 35.0), 1),
                        "humidity": random.randint(40, 80)
                    }
                },
                "motors": [
                    {
                        "name": "Left_Knee",
                        "rpm": random.randint(0, 3000),
                        "torque": round(random.uniform(0.0, 50.0), 2),
                        "temp": round(random.uniform(30.0, 65.0), 1),
                        "status": "OK"
                    },
                    {
                        "name": "Right_Knee",
                        "rpm": random.randint(0, 3000),
                        "torque": round(random.uniform(0.0, 50.0), 2),
                        "temp": round(random.uniform(30.0, 65.0), 1),
                        "status": "OK" if random.random() > 0.1 else "WARNING"
                    }
                ],
                "safety_checks": {
                    "emergency_stop": False,
                    "vibration_level": "LOW",
                    "comm_status": "STABLE"
                }
            }

            # 2. Convert Dictionary to JSON String (serialization)
            # json.dumps() handles all the quotes, commas, and formatting for you.
            data = json.dumps(large_data_packet)
            
            # 3. Convert string to bytes
            payload_bytes = data.encode('utf-8')

            # 4. ENCODE WITH FRAMING
            framed_message = framing.encode(payload_bytes)

            # 5. Send
            client_sock.send(framed_message)
            
            # Print length just to see how big it is
            print(f"Sent Packet Size: {len(data)} chars")
            
            # Sleep a bit so we don't spam too hard (0.2s = 5 times a second)

    except Exception as e:
        print(f"❌ Error: {e}")
        client_sock.close()
        server_sock.close()

if __name__ == "__main__":
    main()