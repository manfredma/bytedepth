import { StatusBar } from "expo-status-bar";
import { StyleSheet, Text, View } from "react-native";

export default function App() {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        ByteDepth
      </Text>
      <Text style={styles.subtitle}>游客阅读</Text>
      <StatusBar style="auto" />
    </View>
  );
}
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafaf9", alignItems: "center", justifyContent: "center", padding: 24 },
  title: { color: "#292524", fontSize: 24, fontWeight: "500" },
  subtitle: { color: "#78716c", fontSize: 16, marginTop: 12 },
});
