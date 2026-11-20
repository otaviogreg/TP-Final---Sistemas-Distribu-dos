import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { login, register } from "../services/authApi";
import { LoginPage } from "../pages/LoginPage";

vi.mock("../services/authApi", () => ({
  login: vi.fn(),
  register: vi.fn(),
}));

const mockedLogin = vi.mocked(login);
const mockedRegister = vi.mocked(register);

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("cria a conta e inicia uma sessão", async () => {
    const user = userEvent.setup();
    const onAuthenticated = vi.fn();
    mockedRegister.mockResolvedValue({
      id: "user-1",
      displayName: "Ana",
      email: "ana@example.com",
      createdAt: "2025-01-01T10:00:00Z",
    });
    mockedLogin.mockResolvedValue({
      accessToken: "token-123",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: {
        id: "user-1",
        displayName: "Ana",
        email: "ana@example.com",
        createdAt: "2025-01-01T10:00:00Z",
      },
    });

    render(<LoginPage onAuthenticated={onAuthenticated} />);
    await user.click(screen.getByRole("button", { name: "Criar conta" }));
    await user.type(screen.getByLabelText("Nome"), "Ana");
    await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
    await user.type(screen.getByLabelText("Senha"), "senha-segura");
    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    await waitFor(() => expect(onAuthenticated).toHaveBeenCalledWith(
      "token-123",
      expect.objectContaining({ id: "user-1", displayName: "Ana" }),
    ));
    expect(mockedRegister).toHaveBeenCalledWith("Ana", "ana@example.com", "senha-segura");
    expect(mockedLogin).toHaveBeenCalledWith("ana@example.com", "senha-segura");
  });

  it("mostra o erro retornado pela API sem autenticar", async () => {
    const onAuthenticated = vi.fn();
    mockedLogin.mockRejectedValue(new Error("Credenciais inválidas."));
    render(<LoginPage onAuthenticated={onAuthenticated} />);

    fireEvent.change(screen.getByLabelText("E-mail"), {
      target: { value: "ana@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Senha"), {
      target: { value: "senha-segura" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Credenciais inválidas.");
    expect(onAuthenticated).not.toHaveBeenCalled();
  });
});