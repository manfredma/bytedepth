import React from "react";
import { ArticleRow, Button, Label, Loading, Notice, Page, Row } from "../components/ui";
import type { Discovery } from "../contracts/mobile";
import type { Navigation } from "../navigation/types";
import { useQuery } from "../runtime/useQuery";
import { useOnline } from "../runtime/Runtime";
export function DiscoveryScreen({ navigation }: { navigation: Navigation }) {
  const { data, loading, error, reload } = useQuery<Discovery>("/discovery");
  const online = useOnline();
  return (
    <Page>
      <Label heading>ByteDepth</Label>
      <Label muted>随时继续读，慢慢理解。</Label>
      <Button title="搜索文章" onPress={() => navigation.navigate("Search")} />
      {!online && <Label muted>已离线，可在「最近」继续阅读已保存文章。</Label>}
      {loading && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={reload} />}
      {data && (
        <>
          <Label heading>精选</Label>
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
          {data.latest.map((post) => (
            <ArticleRow
              key={post.id}
              title={post.title}
              detail={`${post.estimatedReadingMinutes} 分钟 · ${post.summary}`}
              onPress={() => navigation.navigate("Reader", { postId: post.id })}
            />
          ))}
          <Label heading>分类</Label>
          <Row>
            {data.categories.map((category) => (
              <Button
                key={category.id}
                title={category.name}
                onPress={() => navigation.navigate("Search", { category: category.slug })}
              />
            ))}
          </Row>
          <Label heading>标签</Label>
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
