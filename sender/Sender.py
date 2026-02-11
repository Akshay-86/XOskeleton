import socket
import json
import time
import math
import random
import subprocess
import re
import select  # Required for non-blocking read
import framing # <--- IMPORT YOUR MODULE

# ==========================================
# CONFIGURATION
# ==========================================
PORT = 1

def get_local_bluetooth_mac():
    try:
        # Tries to find the local Bluetooth MAC address automatically
        result = subprocess.check_output(["hcitool", "dev"], text=True)
        macs = re.findall(r"([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2})", result, re.IGNORECASE)
        if macs: return macs[0]
    except: pass
    return "00:00:00:00:00:00"

def main():
    print("=== SENDER (Interactive with framing.py) ===")
    local_mac = get_local_bluetooth_mac()
    print(f"🔹 Local MAC: {local_mac}")

    server_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    try:
        server_sock.bind((local_mac, PORT))
        print(f"✅ Bound to Port {PORT}")
    except Exception as e:
        print(f"❌ Bind failed: {e}")
        return

    server_sock.listen(1)
    print("⏳ Waiting for connection...")

    client_sock, address = server_sock.accept()
    print(f"🚀 Connected to: {address}")

    packet_counter = 0
    start_time = time.time()
    
    # --- THIS IS THE VALUE YOU CONTROL FROM APP ---
    user_setpoint = 0.0 

    try:
        while True:
            # 1. READ COMMANDS (Non-Blocking)
            # Check if there is data to read without pausing the script
            ready_to_read, _, _ = select.select([client_sock], [], [], 0)
            
            if ready_to_read:
                try:
                    data = client_sock.recv(1024)
                    if not data: 
                        print("Client disconnected")
                        break
                        
                    cmd = data.decode('utf-8').strip()
                    print(f"📥 Command Received: {cmd}")
                    
                    # PARSE: "SET_VAL:50" -> 50.0
                    if cmd.startswith("SET_VAL:"):
                        try:
                            val_str = cmd.split(":")[1]
                            user_setpoint = float(val_str)
                            print(f"✅ Setpoint updated to: {user_setpoint}")
                        except:
                            print("❌ Invalid Number Format")
                except Exception as e:
                    print(f"Read Error: {e}")

            # 2. GENERATE DATA (Physics Simulation)
            packet_counter += 1
            t = time.time() - start_time
            freq = 0.5 # 0.5Hz wave
            
            # Simulated Motor Physics
            pos_val = 50 + (40 * math.sin(2 * math.pi * freq * t))
            vel_val = 40 * math.cos(2 * math.pi * freq * t)
            torque_val = (vel_val * 0.2) + random.uniform(-0.5, 0.5)
            volt_val = 48.0 - (abs(torque_val) * 0.05) + random.uniform(-0.1, 0.1)

            simple_packet = {
                "packet_id": packet_counter,
                "timestamp": time.time(),
                "right(1)": {
                    "fault": 0, 
                    "Position": round(pos_val, 2),
                    "velocity": round(vel_val, 2),
                    "torque": round(torque_val, 2),
                    "voltage": round(volt_val, 2),
                    "current": packet_counter % 100,
                    # Feedback the user setpoint to prove it updated
                    "user_setpoint": user_setpoint  
                },
                "left(2)": {
                    "fault": 0,
                    "Position": round(50 + (40 * math.sin(2 * math.pi * freq * t + math.pi)), 2), 
                    "velocity": round(40 * math.cos(2 * math.pi * freq * t + math.pi), 2),
                    "torque": round(torque_val * -1, 2),
                    "voltage": round(volt_val, 2),
                    "current": packet_counter % 100,
                    "user_setpoint": user_setpoint 
                }
            }

            # 3. SEND WITH FRAMING MODULE
            json_str = json.dumps(simple_packet)
            payload_bytes = json_str.encode('utf-8')
            
            try:
                # Use your imported framing logic here
                framed_message = framing.encode(payload_bytes)
                client_sock.send(framed_message)
            except framing.FramingError as e:
                print(f"❌ Framing Error: {e}")
            except Exception as e:
                print(f"❌ Send Error: {e}")
                break
            
            time.sleep(0.05) # 20Hz Update Rate

    except Exception as e:
        print(f"❌ Critical Error: {e}")
        try:
            client_sock.close()
            server_sock.close()
        except: pass

if __name__ == "__main__":
    main()