import React, { useState } from "react";
import { ArticleRow, Button, Label, Loading, Notice, Page, Row } from "../components/ui";
import type { MobilePage, ColumnSummary, ColumnDetail } from "../contracts/mobile";
import type { Navigation } from "../navigation/types";
import { useQuery } from "../runtime/useQuery";
import { useOnline } from "../runtime/Runtime";
export function ColumnsScreen({ navigation }: { navigation: Navigation }) {
  const [page, setPage] = useState(1);
  const { data, loading, error, reload } = useQuery<MobilePage<ColumnSummary>>(`/columns?page=${page}`);
  const online = useOnline();
  return (
    <Page>
      <Label heading>专栏</Label>
      {!online && <Label muted>专栏目录需要联网，已保存文章可从「最近」打开。</Label>}
      {loading && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={reload} />}
      {data && (
        <>
          {data.items.length === 0 && <Label muted>暂时没有专栏</Label>}
          {data.items.map((column) => (
            <ArticleRow
              key={column.id}
              title={column.name}
              detail={`${column.postCount} 篇 · ${column.description ?? ""}`}
              onPress={() => navigation.navigate("Column", { columnId: column.id })}
            />
          ))}
          <Row>
            {page > 1 && <Button title="上一页" onPress={() => setPage((p) => p - 1)} />}
            {data.hasNext && <Button title="下一页" onPress={() => setPage((p) => p + 1)} />}
          </Row>
        </>
      )}
    </Page>
  );
}
export function ColumnScreen({
  navigation,
  route,
}: {
  navigation: Navigation;
  route: { params: { columnId: string } };
}) {
  const [page, setPage] = useState(1);
  const { data, loading, error, reload } = useQuery<ColumnDetail>(`/columns/${route.params.columnId}?page=${page}`);
  const online = useOnline();
  return (
    <Page>
      {!online && <Label muted>专栏目录需要联网</Label>}
      {loading && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={reload} />}
      {data && (
        <>
          <Label heading>{data.name}</Label>
          <Label muted>{data.description}</Label>
          {data.posts.items.length === 0 && <Label muted>暂时没有文章</Label>}
          {data.posts.items.map((post) => (
            <ArticleRow
              key={post.id}
              title={post.title}
              detail={post.summary}
              onPress={() => navigation.navigate("Reader", { postId: post.id })}
            />
          ))}
          <Row>
            {page > 1 && <Button title="上一页" onPress={() => setPage((p) => p - 1)} />}
            {data.posts.hasNext && <Button title="下一页" onPress={() => setPage((p) => p + 1)} />}
          </Row>
        </>
      )}
    </Page>
  );
}
