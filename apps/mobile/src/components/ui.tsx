import React from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useColorScheme,
  View,
  type TextInputProps,
  type ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { usePreferences } from "../runtime/Runtime";
// Native values mirror the existing light/dark --bd-* reading palette.
const light = { background: "#ffffff", text: "#1a1a2e", muted: "#555555", border: "#dddddd", danger: "#cc0000" };
const dark = { background: "#15151d", text: "#eeeeee", muted: "#b5b5be", border: "#454550", danger: "#ff8b8b" };
export function usePalette() {
  const preferences = usePreferences();
  const system = useColorScheme();
  return preferences.theme === "dark" || (preferences.theme === "system" && system === "dark") ? dark : light;
}
export function Label({
  children,
  muted = false,
  heading = false,
  numberOfLines,
}: {
  children: React.ReactNode;
  muted?: boolean;
  heading?: boolean;
  numberOfLines?: number;
}) {
  const p = usePalette();
  return (
    <Text
      numberOfLines={numberOfLines}
      accessibilityRole={heading ? "header" : undefined}
      style={{
        color: muted ? p.muted : p.text,
        fontSize: heading ? 22 : 16,
        lineHeight: heading ? 31 : 25,
        marginVertical: 4,
      }}
    >
      {children}
    </Text>
  );
}
export function Button({
  title,
  onPress,
  disabled = false,
  selected = false,
  color,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  selected?: boolean;
  color?: string;
}) {
  const p = usePalette();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled, selected }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => ({
        minHeight: 44,
        minWidth: 44,
        paddingHorizontal: 12,
        paddingVertical: 10,
        opacity: disabled ? 0.45 : pressed ? 0.65 : 1,
      })}
    >
      <Text style={{ color: color ?? p.text, fontSize: 16, textDecorationLine: selected ? "underline" : "none" }}>
        {title}
      </Text>
    </Pressable>
  );
}
export function Field(props: TextInputProps) {
  const p = usePalette();
  return (
    <TextInput
      {...props}
      placeholderTextColor={p.muted}
      style={[
        {
          color: p.text,
          borderBottomColor: p.border,
          borderBottomWidth: StyleSheet.hairlineWidth,
          minHeight: 48,
          fontSize: 16,
          padding: 10,
          marginVertical: 8,
        },
        props.style,
      ]}
    />
  );
}
export function Page({ children, scroll = true }: { children: React.ReactNode; scroll?: boolean }) {
  const p = usePalette();
  return (
    <SafeAreaView edges={["bottom", "left", "right"]} style={{ flex: 1, backgroundColor: p.background }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        {scroll ? (
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 20, paddingBottom: 32 }}>
            {children}
          </ScrollView>
        ) : (
          children
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
export function Row({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return <View style={[{ flexDirection: "row", alignItems: "center", flexWrap: "wrap" }, style]}>{children}</View>;
}
export function Loading() {
  return (
    <View accessibilityLabel="正在加载" style={{ padding: 24 }}>
      <ActivityIndicator />
    </View>
  );
}
export function Notice({ message }: { message: string | null }) {
  const p = usePalette();
  return message ? (
    <Text accessibilityRole="alert" style={{ color: p.muted, fontSize: 14, lineHeight: 22, marginVertical: 10 }}>
      {message}
    </Text>
  ) : null;
}
export function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const p = usePalette();
  return (
    <Modal testID="reader-sheet" transparent animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: "#00000055", justifyContent: "flex-end" }}>
        <SafeAreaView style={{ maxHeight: "88%", backgroundColor: p.background }}>
          <Row style={{ justifyContent: "space-between", padding: 12 }}>
            <Label heading>{title}</Label>
            <Button title="关闭" onPress={onClose} />
          </Row>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 20, paddingBottom: 36 }}>
              {children}
            </ScrollView>
          </KeyboardAvoidingView>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
export function ArticleRow({
  title,
  detail,
  onPress,
  disabled = false,
}: {
  title: string;
  detail?: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  const p = usePalette();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled }}
      onPress={onPress}
      disabled={disabled}
      style={{
        minHeight: 60,
        paddingVertical: 16,
        borderBottomColor: p.border,
        borderBottomWidth: StyleSheet.hairlineWidth,
        opacity: disabled ? 0.5 : 1,
      }}
    >
      <Label numberOfLines={2}>{title}</Label>
      {detail && (
        <Label muted numberOfLines={3}>
          {detail}
        </Label>
      )}
    </Pressable>
  );
}
