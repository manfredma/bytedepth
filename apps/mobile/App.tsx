import React, { useEffect, useState } from "react";
import { ActivityIndicator, Pressable, Text, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { RuntimeContext, type Runtime } from "./src/runtime/Runtime";
import { createNativeRuntime } from "./src/runtime/nativeRuntime";
import { RootNavigator } from "./src/navigation/RootNavigator";
export default function App({ createRuntime = createNativeRuntime }: { createRuntime?: () => Promise<Runtime> }) {
  const [runtime, setRuntime] = useState<Runtime | null>(null);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    let service: Runtime | null = null;
    void createRuntime().then(
      (value) => {
        service = value;
        if (active) setRuntime(value);
        else value.dispose();
      },
      () => {
        if (active) setError(true);
      },
    );
    return () => {
      active = false;
      service?.dispose();
    };
  }, [createRuntime, retry]);
  if (!runtime)
    return (
      <View style={{ flex: 1, justifyContent: "center", padding: 24 }}>
        <Text style={{ fontSize: 22, marginBottom: 16 }}>ByteDepth</Text>
        {error ? (
          <>
            <Text>本机阅读数据暂时无法打开，请重试。</Text>
            <Pressable
              accessibilityRole="button"
              onPress={() => {
                setError(false);
                setRetry((n) => n + 1);
              }}
              style={{ padding: 16, minHeight: 44 }}
            >
              <Text>重试</Text>
            </Pressable>
          </>
        ) : (
          <ActivityIndicator accessibilityLabel="正在打开阅读应用" />
        )}
      </View>
    );
  return (
    <RuntimeContext.Provider value={runtime}>
      <StatusBar style="auto" />
      <RootNavigator />
    </RuntimeContext.Provider>
  );
}
