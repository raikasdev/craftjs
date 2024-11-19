import { test } from '@craftjs-typings/core';
import { WebSocket } from '@craftjs-typings/core/websocket';

test('websocket echo', async (t) => {
  const ws = await WebSocket.open('wss://echo.websocket.org');
  ws.send('websocket test');
  t.eq(await ws.receive(), 'websocket test', 'websocket echo works');
  await ws.close('finished');
});
