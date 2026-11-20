import type { AuthResponse, UserProfile, UserSummary } from "../types/auth";
import { requestJson } from "./http";

export function register(
  displayName: string,
  email: string,
  password: string,
): Promise<UserProfile> {
  return requestJson("/api/v1/auth/register", null, {
    method: "POST",
    body: JSON.stringify({ displayName, email, password }),
  });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return requestJson("/api/v1/auth/login", null, {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function getCurrentUser(token: string): Promise<UserProfile> {
  return requestJson("/api/v1/users/me", token);
}

export function searchUsers(token: string, query: string): Promise<UserSummary[]> {
  const params = new URLSearchParams({ query });
  return requestJson(`/api/v1/users?${params.toString()}`, token);
}