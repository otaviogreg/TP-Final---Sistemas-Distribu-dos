import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NewConversationDialog } from "../components/NewConversationDialog";
import { searchUsers } from "../services/authApi";

vi.mock("../services/authApi", () => ({
  searchUsers: vi.fn(),
}));

const mockedSearchUsers = vi.mocked(searchUsers);

describe("NewConversationDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("cria um grupo com os participantes escolhidos", async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockResolvedValue(undefined);
    mockedSearchUsers.mockResolvedValue([
      { id: "user-2", displayName: "Ana" },
      { id: "user-3", displayName: "Bruno" },
    ]);

    render(
      <NewConversationDialog
        token="token"
        currentUserId="user-1"
        onClose={vi.fn()}
        onCreate={onCreate}
        onUsersFound={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: /Grupo/ }));
    await user.type(screen.getByLabelText("Nome do grupo"), "Equipe do projeto");
    await user.type(screen.getByRole("textbox", { name: "Adicionar participantes" }), "ana");

    await screen.findByRole("button", { name: /Ana/ });
    await user.click(screen.getByRole("button", { name: /Ana/ }));
    await user.click(screen.getByRole("button", { name: /Bruno/ }));
    fireEvent.click(screen.getByRole("button", { name: /Criar conversa/ }));

    await waitFor(() => expect(onCreate).toHaveBeenCalledWith({
      type: "GROUP",
      name: "Equipe do projeto",
      participantIds: ["user-2", "user-3"],
    }));
  });

  it("adiciona participantes a um grupo sem oferecer membros que já estão nele", async () => {
    const user = userEvent.setup();
    const onAddParticipants = vi.fn().mockResolvedValue(undefined);
    mockedSearchUsers.mockResolvedValue([
      { id: "user-2", displayName: "Ana" },
      { id: "user-3", displayName: "Bruno" },
    ]);

    render(
      <NewConversationDialog
        mode="addParticipants"
        token="token"
        currentUserId="user-1"
        existingParticipantIds={["user-1", "user-2"]}
        onClose={vi.fn()}
        onAddParticipants={onAddParticipants}
        onUsersFound={vi.fn()}
      />,
    );

    await user.type(screen.getByRole("textbox", { name: "Adicionar participantes" }), "ana");

    expect(await screen.findByRole("button", { name: /Bruno/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ana/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Bruno/ }));
    await user.click(screen.getByRole("button", { name: /^Adicionar/ }));

    await waitFor(() => expect(onAddParticipants).toHaveBeenCalledWith(["user-3"]));
  });
});
