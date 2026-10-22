import type { Directory, File } from "expo-file-system";
export interface FileStore {
  list(): Promise<{ name: string; size: number }[]>;
  read(name: string): Promise<Uint8Array>;
  write(name: string, bytes: Uint8Array): Promise<void>;
  move(from: string, to: string): Promise<void>;
  remove(name: string): Promise<void>;
  uri(name: string): string;
}

/** Only opaque index-controlled names may reach the native filesystem. */
export class NativeFileStore implements FileStore {
  constructor(
    private readonly directory: Directory,
    private readonly FileClass: typeof File,
  ) {}
  private file(name: string): File {
    if (!/^(?:[ab]-[a-f0-9]{64}|stage-[0-9]+)$/.test(name)) throw new Error("Invalid cache path");
    return new this.FileClass(this.directory, name);
  }
  async list(): Promise<{ name: string; size: number }[]> {
    return this.directory.list().map((item) => {
      if (!(item instanceof this.FileClass)) throw new Error("Unexpected cache directory");
      return { name: item.name, size: item.size };
    });
  }
  async read(name: string): Promise<Uint8Array> {
    return this.file(name).bytes();
  }
  async write(name: string, bytes: Uint8Array): Promise<void> {
    const file = this.file(name);
    file.create({ overwrite: false });
    await file.write(bytes);
  }
  async move(from: string, to: string): Promise<void> {
    this.file(from).move(this.file(to));
  }
  async remove(name: string): Promise<void> {
    const file = this.file(name);
    if (file.exists) file.delete();
  }
  uri(name: string): string {
    return this.file(name).uri;
  }
}
export async function createNativeFileStore(): Promise<FileStore> {
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- Metro lazy native boundary; injected stores require no native runtime.
  const { Directory, File, Paths }: typeof import("expo-file-system") = require("expo-file-system");
  const directory = new Directory(Paths.cache, "mobile-reader");
  directory.create({ idempotent: true, intermediates: true });
  return new NativeFileStore(directory, File);
}
