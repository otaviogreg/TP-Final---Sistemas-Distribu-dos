export interface UserProfile {
  id: string;
  displayName: string;
  email: string;
  createdAt: string;
}

export interface UserSummary {
  id: string;
  displayName: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: UserProfile;
}