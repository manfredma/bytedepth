import React from "react";
import { Search } from "lucide-react-native";
import { Platform, Text, View } from "react-native";
import { ArticleRow, Button, Label, Loading, Notice, Page, Row, usePalette } from "../components/ui";
import type { Discovery } from "../contracts/mobile";
import type { Navigation } from "../navigation/types";
import { useQuery } from "../runtime/useQuery";
import { useOnline } from "../runtime/Runtime";
export function DiscoveryScreen({ navigation }: { navigation: Navigation }) {
  const { data, loading, error, reload } = useQuery<Discovery>("/discovery");
  const online = useOnline();
  const p = usePalette();
  return (
    <Page>
      <View style={{ paddingTop: 16, paddingBottom: 24, borderBottomWidth: 1, borderBottomColor: p.border, gap: 8 }}>
        <Text style={{ color: p.accent, fontSize: 10, letterSpacing: 1.8, fontWeight: "600" }}>
          A LIVING ENGINEERING NOTEBOOK
        </Text>
        <View
          accessible
          accessibilityRole="header"
          accessibilityLabel="记录系统，理解复杂。"
          style={{ marginVertical: 8 }}
        >
          <Text
            style={{
              color: p.text,
              fontFamily: Platform.OS === "ios" ? "Georgia" : "serif",
              fontSize: 32,
              lineHeight: 42,
              fontWeight: "600",
            }}
          >
            记录系统，
          </Text>
          <Text
            style={{
              color: p.accent,
              fontFamily: Platform.OS === "ios" ? "Georgia" : "serif",
              fontSize: 32,
              lineHeight: 42,
              fontWeight: "600",
            }}
          >
            理解复杂。
          </Text>
        </View>
        <Label muted>随时继续读，慢慢理解。</Label>
        <Button icon={Search} title="搜索文章" onPress={() => navigation.navigate("Search")} />
      </View>
      {!online && <Label muted>已离线，可在「最近」继续阅读已保存文章。</Label>}
      {loading && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={reload} />}
      {data && (
        <>
          {data.featured.length > 0 && <Label heading>精选</Label>}
          {data.featured.map((post) => (
            <ArticleRow
              key={post.id}
              title={post.title}
              detail={post.summary}
              onPress={() => navigation.navigate("Reader", { postId: post.id })}
            />
          ))}
          <Label heading>最近更新</Label>
          {data.latest.length === 0 && <Label muted>暂时没有文章</Label>}
          {data.latest.map((post, index) => (
            <ArticleRow
              key={post.id}
              title={post.title}
              detail={post.summary}
              ordinal={index + 1}
              category={post.category?.name}
              meta={`${post.estimatedReadingMinutes} 分钟阅读`}
              onPress={() => navigation.navigate("Reader", { postId: post.id })}
            />
          ))}
          {data.categories.length > 0 && <Label heading>分类</Label>}
          <Row>
            {data.categories.map((category) => (
              <Button
                key={category.id}
                title={category.name}
                onPress={() => navigation.navigate("Search", { category: category.slug })}
              />
            ))}
          </Row>
          {data.tags.length > 0 && <Label heading>标签</Label>}
          <Row>
            {data.tags.map((tag) => (
              <Button key={tag.id} title={tag.name} onPress={() => navigation.navigate("Search", { tag: tag.slug })} />
            ))}
          </Row>
        </>
      )}
    </Page>
  );
}
