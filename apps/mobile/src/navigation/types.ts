import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
export type RootStackParams = {
  Home: undefined;
  Reader: { postId: string };
  Search: { category?: string; tag?: string } | undefined;
  Column: { columnId: string };
};
export type Navigation = NativeStackNavigationProp<RootStackParams>;
