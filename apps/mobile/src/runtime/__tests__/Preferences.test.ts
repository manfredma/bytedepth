import { Preferences } from "../Preferences";
it("keeps local reading preferences across starts without a service request", async () => {
  let value: string | null = null;
  const port = {
    read: async () => value,
    write: async (text: string) => {
      value = text;
    },
  };
  const prefs = await Preferences.open(port);
  await prefs.set({ theme: "dark", fontScale: 1.25 });
  expect((await Preferences.open(port)).snapshot()).toEqual({ theme: "dark", fontScale: 1.25 });
});
it("falls back from damaged local data and validates font bounds", async () => {
  const prefs = await Preferences.open({ read: async () => "{bad", write: async () => {} });
  expect(prefs.snapshot()).toEqual({ theme: "system", fontScale: 1 });
  await expect(prefs.set({ theme: "dark", fontScale: 5 })).rejects.toThrow();
});
it("notifies local observers only after durable preference writes and recovers from a failed write", async () => {
  const write = jest.fn().mockRejectedValueOnce(new Error("full")).mockResolvedValue(undefined);
  const prefs = await Preferences.open({ read: async () => null, write });
  const listener = jest.fn();
  const unsubscribe = prefs.subscribe(listener);
  await expect(prefs.set({ theme: "light", fontScale: 1.2 })).rejects.toThrow("full");
  expect(prefs.snapshot().theme).toBe("system");
  await prefs.set({ theme: "dark", fontScale: 1.3 });
  expect(listener).toHaveBeenCalledTimes(1);
  unsubscribe();
  await prefs.set({ theme: "system", fontScale: 1 });
  expect(listener).toHaveBeenCalledTimes(1);
});
