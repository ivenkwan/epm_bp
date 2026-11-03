import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("finance platform foundation", () => {
  it("exposes an accessible foundation without financial claims", () => {
    render(<App />);

    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Finance Platform" })).toBeInTheDocument();
    expect(
      screen.getByText("Toolchain foundation only. No financial data or approvals are available."),
    ).toBeInTheDocument();
  });
});
