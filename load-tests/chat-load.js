import http from "k6/http";
import ws from "k6/ws";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const WS_URL = __ENV.WS_URL || "ws://localhost:8080/ws";
const USER_COUNT = Number(__ENV.USER_COUNT || 100);
const GROUP_COUNT = Number(__ENV.GROUP_COUNT || 20);
const MESSAGE_COUNT = Number(__ENV.MESSAGE_COUNT || 5);
const PASSWORD = "Carga-Segura-123";

const messageLatency = new Trend("chat_message_latency_ms");
const websocketErrors = new Counter("chat_websocket_errors");

export const options = {
  scenarios: {
    chat: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: USER_COUNT },
        { duration: "1m", target: USER_COUNT },
        { duration: "30s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    chat_websocket_errors: ["count==0"],
    http_req_failed: ["rate<0.05"],
  },
};

function registerOrLogin(index) {
  const email = `load-${index}@example.com`;
  const displayName = `Carga ${index}`;
  const registerResponse = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ displayName, email, password: PASSWORD }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "register" },
      responseCallback: http.expectedStatuses(200, 201, 409),
    },
  );

  if (![200, 201].includes(registerResponse.status)) {
    check(registerResponse, {
      "register returns success or the load user already exists": (response) =>
        response.status === 409,
    });
  }

  const loginResponse = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } },
  );

  check(loginResponse, {
    "load user logged in": (response) => response.status === 200,
  });

  if (loginResponse.status !== 200) return null;
  const session = loginResponse.json();
  return { email, password: PASSWORD, token: session.accessToken, userId: session.user.id };
}

export function setup() {
  const users = [];
  for (let index = 0; index < USER_COUNT; index += 1) {
    const user = registerOrLogin(index);
    if (user) users.push(user);
  }

  check(users, {
    "at least 100 load users were authenticated": (created) => created.length >= USER_COUNT,
  });

  const groups = [];
  for (let index = 0; index < GROUP_COUNT; index += 1) {
    const start = Math.floor((index * users.length) / GROUP_COUNT);
    const end = Math.floor(((index + 1) * users.length) / GROUP_COUNT);
    const members = users.slice(start, end);
    const [owner, ...participants] = members;
    if (!owner) continue;

    const response = http.post(
      `${BASE_URL}/api/v1/conversations`,
      JSON.stringify({
        type: "GROUP",
        name: `Carga grupo ${index + 1}`,
        participantIds: participants.map((participant) => participant.userId),
      }),
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${owner.token}`,
        },
        tags: { name: "create_group" },
      },
    );

    check(response, {
      "load group was created": (result) => result.status === 201,
    });

    if (response.status === 201) {
      groups.push({
        id: response.json().id,
        memberIds: members.map((member) => member.userId),
      });
    }
  }

  return { users, groups };
}

function stompFrame(command, headers = {}, body = "") {
  const headerText = Object.entries(headers)
    .map(([name, value]) => `${name}:${value}`)
    .join("\n");
  return `${command}\n${headerText}\n\n${body}\0`;
}

export default function (data) {
  const userIndex = (__VU - 1) % data.users.length;
  const user = data.users[userIndex];
  if (!user) {
    websocketErrors.add(1);
    return;
  }

  const group = data.groups.find((candidate) =>
    candidate.memberIds.includes(user.userId),
  );
  if (!group) {
    websocketErrors.add(1);
    return;
  }

  const startedAt = Date.now();
  const response = ws.connect(
    WS_URL,
    {
      tags: { scenario: "chat_websocket" },
    },
    (socket) => {
      socket.on("open", () => {
        socket.send(stompFrame("CONNECT", {
          "accept-version": "1.2",
          "heart-beating": "0,0",
          Authorization: `Bearer ${user.token}`,
        }));
      });

      socket.on("message", (frame) => {
        if (frame.startsWith("CONNECTED")) {
          socket.send(stompFrame("SUBSCRIBE", {
            id: "subscription-1",
            destination: `/topic/conversations/${group.id}`,
            ack: "auto",
          }));

          for (let index = 0; index < MESSAGE_COUNT; index += 1) {
            socket.send(stompFrame("SEND", {
              destination: `/app/conversations/${group.id}/messages`,
              "content-type": "application/json",
            }, JSON.stringify({ content: `Carga ${__VU}-${index}-${Date.now()}` })));
          }
        } else if (frame.startsWith("MESSAGE")) {
          messageLatency.add(Date.now() - startedAt);
        } else if (frame.startsWith("ERROR")) {
          websocketErrors.add(1);
        }
      });

      socket.on("error", () => websocketErrors.add(1));
      socket.setTimeout(() => socket.close(), 8000);
    },
  );

  check(response, {
    "websocket response accepted": (result) => result && result.status === 101,
  });

  const historyResponse = http.get(
    `${BASE_URL}/api/v1/conversations/${group.id}/messages?page=0&size=50`,
    {
      headers: { Authorization: `Bearer ${user.token}` },
      tags: { name: "history" },
    },
  );

  check(historyResponse, {
    "message history is accessible": (result) => result.status === 200,
  });

  sleep(1);
}