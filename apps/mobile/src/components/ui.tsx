import React from "react";
import type { LucideIcon } from "lucide-react-native";
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
// 对应网站 static/css/theme.css 中 default/midnight 的语义变量。
const light = {
  background: "#f0f2f5",
  surface: "#ffffff",
  surfaceMuted: "#f4f6f8",
  text: "#1a1a2e",
  muted: "#555555",
  border: "#dddddd",
  danger: "#cc0000",
  accent: "#d93652",
  accentSoft: "#fff5f7",
  nav: "#1a1a2e",
  navText: "#eeeeee",
  onAccent: "#ffffff",
};
const dark = {
  background: "#111827",
  surface: "#172033",
  surfaceMuted: "#202b3f",
  text: "#e5e7eb",
  muted: "#cbd5e1",
  border: "#334155",
  danger: "#ff8b8b",
  accent: "#38bdf8",
  accentSoft: "#202b3f",
  nav: "#0b1120",
  navText: "#e5e7eb",
  onAccent: "#0b1120",
};
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
        fontSize: heading ? 21 : 14,
        fontWeight: heading ? "600" : "400",
        lineHeight: heading ? 30 : 22,
        marginTop: heading ? 20 : 4,
        marginBottom: heading ? 12 : 4,
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
  primary = false,
  icon: Icon,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  selected?: boolean;
  color?: string;
  primary?: boolean;
  icon?: LucideIcon;
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
        borderRadius: 6,
        backgroundColor: primary ? p.accent : selected ? p.accentSoft : p.surface,
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        borderWidth: StyleSheet.hairlineWidth,
        borderColor: primary || selected ? p.accent : p.border,
        opacity: disabled ? 0.45 : pressed ? 0.65 : 1,
      })}
    >
      {Icon && <Icon size={18} color={primary ? p.onAccent : p.muted} accessible={false} />}
      <Text
        style={{
          color: color ?? (primary ? p.onAccent : selected ? p.accent : p.text),
          fontSize: 14,
          fontWeight: selected ? "600" : "400",
        }}
      >
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
          backgroundColor: p.surface,
          borderColor: p.border,
          borderWidth: 1,
          borderRadius: 6,
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
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
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
  return (
    <View style={[{ flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: 8 }, style]}>{children}</View>
  );
}
export function Loading() {
  const p = usePalette();
  return (
    <View accessibilityLabel="正在加载" style={{ padding: 24 }}>
      <ActivityIndicator color={p.accent} />
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
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        style={{ flex: 1, backgroundColor: "#00000055", justifyContent: "flex-end" }}
      >
        <SafeAreaView
          style={{
            maxHeight: "88%",
            backgroundColor: p.surface,
            borderTopLeftRadius: 16,
            borderTopRightRadius: 16,
            overflow: "hidden",
          }}
        >
          <Row
            style={{
              justifyContent: "space-between",
              paddingHorizontal: 20,
              paddingVertical: 8,
              borderBottomWidth: StyleSheet.hairlineWidth,
              borderBottomColor: p.border,
            }}
          >
            <Label heading>{title}</Label>
            <Button title="关闭" onPress={onClose} />
          </Row>
          <ScrollView
            style={{ flexShrink: 1 }}
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ padding: 20, paddingBottom: 36 }}
          >
            {children}
          </ScrollView>
        </SafeAreaView>
      </KeyboardAvoidingView>
    </Modal>
  );
}
export function ArticleRow({
  title,
  detail,
  onPress,
  disabled = false,
  ordinal,
  category,
  meta,
  progress,
}: {
  title: string;
  detail?: string;
  onPress: () => void;
  disabled?: boolean;
  ordinal?: number;
  category?: string;
  meta?: string;
  progress?: number;
}) {
  const p = usePalette();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled }}
      accessibilityValue={progress === undefined ? undefined : { min: 0, max: 100, now: Math.round(progress * 100) }}
      onPress={onPress}
      disabled={disabled}
      style={{
        minHeight: 60,
        paddingVertical: 20,
        borderBottomColor: p.border,
        borderBottomWidth: StyleSheet.hairlineWidth,
        opacity: disabled ? 0.5 : 1,
        flexDirection: "row",
        gap: 12,
      }}
    >
      {ordinal !== undefined && (
        <Text style={{ color: p.accent, fontSize: 12, lineHeight: 21, width: 20 }}>
          {String(ordinal).padStart(2, "0")}
        </Text>
      )}
      <View style={{ flex: 1, minWidth: 0 }}>
        <Row style={{ marginBottom: 6 }}>
          {category && <Text style={{ color: p.accent, fontSize: 12 }}>{category}</Text>}
          {meta && <Text style={{ color: p.muted, fontSize: 12 }}>{meta}</Text>}
        </Row>
        <Text
          numberOfLines={2}
          style={{ color: p.text, fontSize: 17, lineHeight: 26, fontWeight: "500", marginBottom: 6 }}
        >
          {title}
        </Text>
        {detail && (
          <Label muted numberOfLines={3}>
            {detail}
          </Label>
        )}
        {progress !== undefined && (
          <View
            accessibilityRole="progressbar"
            accessibilityLabel="阅读进度"
            accessibilityValue={{ min: 0, max: 100, now: Math.round(progress * 100) }}
            style={{ height: 3, backgroundColor: p.border, marginTop: 12 }}
          >
            <View style={{ width: `${Math.round(progress * 100)}%`, height: 3, backgroundColor: p.accent }} />
          </View>
        )}
      </View>
    </Pressable>
  );
}
export function PageHeading({ title, eyebrow, subtitle }: { title: string; eyebrow: string; subtitle?: string }) {
  const p = usePalette();
  return (
    <View style={{ paddingTop: 12, marginBottom: 12 }}>
      <Text style={{ color: p.accent, fontSize: 10, letterSpacing: 1.5, marginBottom: 16 }}>{eyebrow}</Text>
      <Text
        accessibilityRole="header"
        style={{
          color: p.text,
          fontFamily: Platform.OS === "ios" ? "Georgia" : "serif",
          fontSize: 30,
          lineHeight: 40,
          marginBottom: 12,
        }}
      >
        {title}
      </Text>
      {subtitle && <Label muted>{subtitle}</Label>}
    </View>
  );
}
export function Group({ children }: { children: React.ReactNode }) {
  const p = usePalette();
  return (
    <View
      style={{
        padding: 18,
        marginVertical: 12,
        borderColor: p.border,
        borderWidth: StyleSheet.hairlineWidth,
        borderRadius: 7,
        backgroundColor: p.surface,
      }}
    >
      {children}
    </View>
  );
}
export function ColumnCard({
  title,
  description,
  count,
  onPress,
}: {
  title: string;
  description: string | null;
  count: number;
  onPress: () => void;
}) {
  const p = usePalette();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={{
        backgroundColor: p.surface,
        borderWidth: StyleSheet.hairlineWidth,
        borderColor: p.border,
        borderRadius: 7,
        padding: 20,
        marginVertical: 8,
      }}
    >
      <Text
        accessibilityRole="header"
        style={{ color: p.text, fontSize: 18, lineHeight: 27, marginBottom: 10, fontWeight: "500" }}
      >
        {title}
      </Text>
      {description && <Label muted>{description}</Label>}
      <Row
        style={{
          justifyContent: "space-between",
          borderTopWidth: StyleSheet.hairlineWidth,
          borderTopColor: p.border,
          marginTop: 16,
          paddingTop: 12,
        }}
      >
        <Text style={{ color: p.muted, fontSize: 12 }}>{count} 篇 · 按章节顺序阅读</Text>
        <Text style={{ color: p.accent, fontSize: 12 }}>进入专栏 ↗</Text>
      </Row>
    </Pressable>
  );
}
export function Tool({ title, icon: Icon, onPress }: { title: string; icon: LucideIcon; onPress: () => void }) {
  const p = usePalette();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: 5, minWidth: 44, minHeight: 48 }}
    >
      <Icon size={18} color={p.muted} accessible={false} />
      <Text style={{ color: p.muted, fontSize: 11 }}>{title}</Text>
    </Pressable>
  );
}
