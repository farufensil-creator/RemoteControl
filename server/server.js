// Relay server: the phone and the viewer both make OUTBOUND connections
// to this server, so neither one needs a public IP or open port.
//
// Pairing works with a simple 6-digit "room code":
//   phone  -> { type: "register", role: "phone",  code: "123456" }
//   viewer -> { type: "join",     role: "viewer",  code: "123456" }
//
// After that:
//   phone  -> binary JPEG frames  -> forwarded to all viewers in that room
//   viewer -> JSON command messages -> forwarded to the phone in that room

const http = require('http');
const WebSocket = require('ws');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('RemoteControl relay is running.\n');
});

const wss = new WebSocket.Server({ server });

// code -> { phone: WebSocket|null, viewers: Set<WebSocket> }
const rooms = new Map();

function getRoom(code) {
  if (!rooms.has(code)) {
    rooms.set(code, { phone: null, viewers: new Set() });
  }
  return rooms.get(code);
}

function cleanupRoomIfEmpty(code) {
  const room = rooms.get(code);
  if (room && !room.phone && room.viewers.size === 0) {
    rooms.delete(code);
  }
}

wss.on('connection', (ws) => {
  ws.role = null;
  ws.roomCode = null;

  ws.on('message', (data, isBinary) => {
    // First message from any client must be the handshake (register/join).
    if (!ws.role) {
      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch (e) {
        ws.close(1002, 'Expected JSON handshake as first message');
        return;
      }

      if (msg.type === 'register' && msg.role === 'phone' && msg.code) {
        ws.role = 'phone';
        ws.roomCode = msg.code;
        const room = getRoom(msg.code);
        room.phone = ws;
        ws.send(JSON.stringify({ type: 'registered' }));
        console.log(`[room ${msg.code}] phone registered`);
      } else if (msg.type === 'join' && msg.role === 'viewer' && msg.code) {
        ws.role = 'viewer';
        ws.roomCode = msg.code;
        const room = getRoom(msg.code);
        room.viewers.add(ws);
        ws.send(JSON.stringify({ type: 'joined' }));
        console.log(`[room ${msg.code}] viewer joined`);
      } else {
        ws.close(1002, 'Invalid handshake');
      }
      return;
    }

    const room = rooms.get(ws.roomCode);
    if (!room) return;

    if (ws.role === 'phone') {
      // Binary screen frame -> fan out to every connected viewer.
      for (const viewer of room.viewers) {
        if (viewer.readyState === WebSocket.OPEN) {
          viewer.send(data, { binary: isBinary });
        }
      }
    } else if (ws.role === 'viewer') {
      // JSON tap/swipe command -> forward to the phone in this room.
      if (room.phone && room.phone.readyState === WebSocket.OPEN) {
        room.phone.send(data.toString());
      }
    }
  });

  ws.on('close', () => {
    if (!ws.roomCode) return;
    const room = rooms.get(ws.roomCode);
    if (!room) return;
    if (ws.role === 'phone' && room.phone === ws) room.phone = null;
    if (ws.role === 'viewer') room.viewers.delete(ws);
    cleanupRoomIfEmpty(ws.roomCode);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Relay server listening on port ${PORT}`);
});
