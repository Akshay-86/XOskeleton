import socket

MAX_PAYLOAD_SIZE = 65535

# Exceptions:
class FramingError(Exception):
    """Base exception for framing errors."""
    pass


class FrameTooLargeError(FramingError):
    pass


class FrameReceiveError(FramingError):
    pass


def encode(payload: bytes) -> bytes:
    """
    Encodes payload bytes and adds length header to it

    Frame format:
        [2 bytes length][payload]
    """

    #check weather the payload is of bytes type or not
    if not isinstance(payload, (bytes, bytearray)):
        raise TypeError("Payload must be bytes")

    length = len(payload)

    if length > MAX_PAYLOAD_SIZE:
        raise FrameTooLargeError(
            f"Payload size {length} exceeds max {MAX_PAYLOAD_SIZE}"
        )
    
    length_bytes = length.to_bytes(2, byteorder="big")
    return length_bytes + payload


def recv_exact(sock: socket.socket, size: int) -> bytes:
    """
    Recieves exactly "size" bytes from the socket
    """
    data = b""

    while len(data) < size:
        try:
            chunk = sock.recv(size - len(data))
        except OSError as e:
            raise FrameReceiveError(
                f"Socket recv failed: {e}"
            ) from e

        if not chunk:
            raise FrameReceiveError(
                "Connection closed while receiving data"
            )

        data += chunk

    return data


def decode(sock: socket.socket) -> bytes:
    """
    Recieve complete payload from chunks of byte data from the socket
    """

    # read first two bytes for length of the data packet
    length_bytes = recv_exact(sock, 2)
    length = int.from_bytes(length_bytes, byteorder="big")

    if length > MAX_PAYLOAD_SIZE:
        raise FrameTooLargeError(
            f"Incoming frame size {length} exceeds max {MAX_PAYLOAD_SIZE}"
        )

    # recv_exact payload of size
    payload = recv_exact(sock, length)

    return payload