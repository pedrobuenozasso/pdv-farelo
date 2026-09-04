// Thin client for /api/v1/users (see docs/api.md, Epic 9).
// Client-only — called from app/admin/users/page.tsx.

import { authHeaders, parseResponse } from "./client";

export type UserRole =
  "ADMIN" | "MANAGER" | "CASHIER" | "KITCHEN" | "ATTENDANT";

export type User = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateUserInput = {
  name: string;
  email: string;
  password: string;
  role: UserRole;
};

export type UpdateUserInput = {
  name: string;
  email: string;
  role: UserRole;
  active: boolean;
};

const USERS_URL = "/api/v1/users";

export async function listUsers(): Promise<User[]> {
  const response = await fetch(USERS_URL, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<User[]>(response);
}

export async function createUser(input: CreateUserInput): Promise<User> {
  const response = await fetch(USERS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<User>(response);
}

export async function updateUser(
  id: string,
  input: UpdateUserInput,
): Promise<User> {
  const response = await fetch(`${USERS_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<User>(response);
}

export async function updateUserPassword(
  id: string,
  newPassword: string,
): Promise<User> {
  const response = await fetch(`${USERS_URL}/${id}/password`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ newPassword }),
  });
  return parseResponse<User>(response);
}
