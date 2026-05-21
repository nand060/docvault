import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL } from '../api/client';

export function createStompClient(token, onConnect) {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
    connectHeaders: {
      Authorization: `Bearer ${token}`
    },
    reconnectDelay: 3000,
    onConnect
  });

  client.activate();
  return client;
}
