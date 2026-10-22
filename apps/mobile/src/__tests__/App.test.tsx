import { render, screen } from "@testing-library/react-native";
import App from "../../App";
it("rendersGuestRoot", async () => {
  await render(<App />);
  expect(screen.getByText("ByteDepth")).toBeOnTheScreen();
  expect(screen.getByText("游客阅读")).toBeOnTheScreen();
});
