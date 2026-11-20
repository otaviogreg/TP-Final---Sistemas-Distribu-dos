export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function requestJson<T>(
  path: string,
  token: string | null,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, { ...init, headers });
  const responseText = await response.text();
  let payload: unknown;

  if (responseText) {
    try {
      payload = JSON.parse(responseText) as unknown;
    } catch {
      throw new ApiError("O servidor retornou uma resposta inválida.", response.status);
    }
  }

  if (!response.ok) {
    const problem = payload as { detail?: string; message?: string } | undefined;
    throw new ApiError(
      problem?.detail ?? problem?.message ?? "Não foi possível concluir a solicitação.",
      response.status,
    );
  }

  return payload as T;
}
