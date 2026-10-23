# ByteDepth Android preview

Independent Expo 58 / React Native package. The API origin in `src/config.ts` is the staging HTTPS origin. This scaffold does not prove staging API integration or device acceptance.

Use Node **22.22.1** (`.nvmrc` and engine-strict), JDK **25** for Android and backend, and the source-controlled `android-toolchain.json` inputs. SDK 58 currently generates Gradle 9.4.1, AGP 9.2.1, compile SDK 37, target SDK 36, Build Tools 37.0.0 and NDK 27.1.12297006. The release and instrumentation APKs have been built locally with JDK 25; Linux Host verification remains a separate gate. Install package dependencies independently:

```sh
bash ../../scripts/with-mobile-toolchain-lock.sh npm ci --ignore-scripts --no-audit --no-fund
npm run typecheck
npm test -- --runInBand
npm run lint
npm run prebuild:android
npm run android:build
```

Run dependency installation and native builds serially. The mobile quality runner and npm native scripts share an exclusive worktree lock; a live owner must finish before retrying. A stale lock requires checking the interrupted process first. Run long native builds/downloads in the coordinating process because worker lifetimes may terminate their child processes.

Keep Gradle wrapper preflight and builds serial: the first wrapper invocation downloads its pinned distribution under an exclusive lock; a second invocation can time out while that download is healthy. Wait for the first terminal result before retrying.

`android/` and `ios/` are generated and ignored. Configure native behavior through `app.config.ts`; regenerate after config or native dependency changes. The pinned Android plugin verifies the generated wrapper URL/checksum and disables the template release debug-key signing. The Host builds unsigned release bytes, then signs with the managed preview key after the source build exits. Version code 2 is reserved in source for the first trial; upgrade verification requires a separately defined version-1 fixture with the same application ID/key. This declaration alone does not prove upgrade preservation.

Local developer build output is not a signed distribution or a release-platform receipt. Do not use EAS or upload builds from this workspace.

A development build needs Metro: run `npm start` with the same fixed Node environment. Use the existing JDK 25 environment for Android tooling; a second JDK installation is not required.

The template and SDK-compatible dependencies are locked. React DOM 19.3.0 is an explicit test peer so npm cannot resolve React DOM 19.3.1 against React 19.3.0. RN's test preset and Expo's jsdom environment currently pull deprecated `inflight`, `glob@7`, and `whatwg-encoding`; these warnings are upstream test tooling debt and are reported rather than hidden with forced peer resolution or blanket overrides.

Jest disables Watchman for deterministic one-shot tests. Metro can continue using its own watcher.
