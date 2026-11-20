import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MessageComposer } from "../components/MessageComposer";

describe("MessageComposer", () => {
  it("limpa a mensagem somente depois de um envio aceito", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockReturnValueOnce(false).mockReturnValue(true);
    render(<MessageComposer disabled={false} onSend={onSend} />);
    const input = screen.getByRole("textbox", { name: "Escreva uma mensagem" });

    await user.type(input, "  Olá, grupo!  ");
    fireEvent.submit(input.closest("form")!);
    expect(onSend).toHaveBeenLastCalledWith("Olá, grupo!");
    expect(input).toHaveValue("  Olá, grupo!  ");

    fireEvent.submit(input.closest("form")!);
    expect(input).toHaveValue("");
  });
});
