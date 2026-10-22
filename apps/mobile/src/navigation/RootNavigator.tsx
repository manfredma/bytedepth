import React from "react";
import { NavigationContainer, DefaultTheme, DarkTheme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { useColorScheme } from "react-native";
import { DiscoveryScreen } from "../screens/DiscoveryScreen";
import { ColumnsScreen, ColumnScreen } from "../screens/ColumnsScreen";
import { RecentScreen } from "../screens/RecentScreen";
import { AccountScreen } from "../screens/AccountScreen";
import { ReaderScreen } from "../screens/ReaderScreen";
import { SearchScreen } from "../screens/SearchScreen";
import { usePreferences } from "../runtime/Runtime";
import type { RootStackParams } from "./types";
const Stack = createNativeStackNavigator<RootStackParams>();
const Tab = createBottomTabNavigator();
function Home() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: { minHeight: 62 },
        tabBarLabelStyle: { fontSize: 13 },
        tabBarIcon: () => null,
      }}
    >
      <Tab.Screen name="发现" component={DiscoveryScreen as never} />
      <Tab.Screen name="专栏" component={ColumnsScreen as never} />
      <Tab.Screen name="最近" component={RecentScreen as never} />
      <Tab.Screen name="我" component={AccountScreen as never} />
    </Tab.Navigator>
  );
}
export function RootNavigator() {
  const prefs = usePreferences();
  const system = useColorScheme();
  const dark = prefs.theme === "dark" || (prefs.theme === "system" && system === "dark");
  return (
    <SafeAreaProvider>
      <NavigationContainer theme={dark ? DarkTheme : DefaultTheme}>
        <Stack.Navigator
          screenOptions={{
            headerTitleStyle: { fontWeight: "400", fontSize: 17 },
            headerShadowVisible: false,
            contentStyle: { backgroundColor: dark ? "#15151d" : "#ffffff" },
          }}
        >
          <Stack.Screen name="Home" component={Home} options={{ title: "ByteDepth" }} />
          <Stack.Screen name="Reader" component={ReaderScreen} options={{ title: "阅读" }} />
          <Stack.Screen name="Search" component={SearchScreen} options={{ title: "搜索" }} />
          <Stack.Screen name="Column" component={ColumnScreen} options={{ title: "专栏" }} />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
