import React from "react";
import { Compass, Library, History, UserRound } from "lucide-react-native";
import { NavigationContainer, DefaultTheme, DarkTheme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { SafeAreaProvider, useSafeAreaInsets } from "react-native-safe-area-context";
import { Image, Text, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { usePalette } from "../components/ui";
import { DiscoveryScreen } from "../screens/DiscoveryScreen";
import { ColumnsScreen, ColumnScreen } from "../screens/ColumnsScreen";
import { RecentScreen } from "../screens/RecentScreen";
import { AccountScreen } from "../screens/AccountScreen";
import { ReaderScreen } from "../screens/ReaderScreen";
import { SearchScreen } from "../screens/SearchScreen";

import type { RootStackParams } from "./types";
const Stack = createNativeStackNavigator<RootStackParams>();
const Tab = createBottomTabNavigator();
function Home() {
  const insets = useSafeAreaInsets();
  const p = usePalette();
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          height: 68 + insets.bottom,
          paddingTop: 6,
          paddingBottom: insets.bottom + 6,
          backgroundColor: p.surface,
          borderTopColor: p.border,
        },
        tabBarActiveTintColor: p.accent,
        tabBarInactiveTintColor: p.muted,
        tabBarActiveBackgroundColor: p.accentSoft,
        tabBarItemStyle: { marginHorizontal: 4, borderRadius: 6 },
        tabBarLabelStyle: { fontSize: 13, fontWeight: "600" },
      }}
    >
      <Tab.Screen
        options={{ tabBarIcon: ({ color }) => <Compass color={color} size={18} accessible={false} /> }}
        name="发现"
        component={DiscoveryScreen as never}
      />
      <Tab.Screen
        options={{ tabBarIcon: ({ color }) => <Library color={color} size={18} accessible={false} /> }}
        name="专栏"
        component={ColumnsScreen as never}
      />
      <Tab.Screen
        options={{ tabBarIcon: ({ color }) => <History color={color} size={18} accessible={false} /> }}
        name="最近"
        component={RecentScreen as never}
      />
      <Tab.Screen
        options={{ tabBarIcon: ({ color }) => <UserRound color={color} size={18} accessible={false} /> }}
        name="我"
        component={AccountScreen as never}
      />
    </Tab.Navigator>
  );
}
export function RootNavigator() {
  const p = usePalette();
  const base = p.background === "#111827" ? DarkTheme : DefaultTheme;
  const theme = {
    ...base,
    colors: {
      ...base.colors,
      primary: p.accent,
      background: p.background,
      card: p.surface,
      text: p.text,
      border: p.border,
    },
  };
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <NavigationContainer theme={theme}>
        <Stack.Navigator
          screenOptions={{
            headerTitleStyle: { fontWeight: "500", fontSize: 17 },
            headerStyle: { backgroundColor: p.nav },
            headerTintColor: p.navText,
            headerShadowVisible: false,
            contentStyle: { backgroundColor: p.background },
          }}
        >
          <Stack.Screen name="Home" component={Home} options={{ title: "ByteDepth", headerTitle: BrandTitle }} />
          <Stack.Screen name="Reader" component={ReaderScreen} options={{ title: "阅读" }} />
          <Stack.Screen name="Search" component={SearchScreen} options={{ title: "搜索" }} />
          <Stack.Screen name="Column" component={ColumnScreen} options={{ title: "专栏" }} />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}

function BrandTitle() {
  const p = usePalette();
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
      <Image
        source={require("../../assets/icon.png")}
        style={{ width: 30, height: 30 }}
        accessibilityIgnoresInvertColors
      />
      <View>
        <Text style={{ color: p.accent, fontSize: 18, fontWeight: "700" }}>bytedepth</Text>
        <Text style={{ color: p.accent, fontSize: 10, letterSpacing: 1 }}>复杂之下，皆有脉路</Text>
      </View>
    </View>
  );
}
