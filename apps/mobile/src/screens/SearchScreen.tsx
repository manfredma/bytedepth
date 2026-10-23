import React, { useState } from "react";
import { ArticleRow, Button, Field, Label, Loading, Notice, Page, Row, PageHeading } from "../components/ui";
import type { MobilePage, PostSummary, SearchResult } from "../contracts/mobile";
import type { Navigation, RootStackParams } from "../navigation/types";
import { useQuery } from "../runtime/useQuery";
import { useOnline } from "../runtime/Runtime";
export function SearchScreen({
  navigation,
  route,
}: {
  navigation: Navigation;
  route?: { params?: RootStackParams["Search"] };
}) {
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [page, setPage] = useState(1);
  const filter = route?.params;
  const browsing = !!(filter?.category || filter?.tag);
  const path = browsing
    ? `/posts?page=${page}&${filter?.category ? "category=" + encodeURIComponent(filter.category) : "tag=" + encodeURIComponent(filter!.tag!)}`
    : submitted
      ? `/search?query=${encodeURIComponent(submitted)}&page=${page}`
      : null;
  const { data, loading, error, reload } = useQuery<MobilePage<SearchResult | PostSummary>>(path);
  const online = useOnline();
  return (
    <Page>
      <PageHeading title={browsing ? "文章" : "搜索"} eyebrow="FIND SOMETHING TO READ" />
      {!browsing && (
        <>
          <Field
            accessibilityLabel="搜索关键词"
            placeholder="输入关键词"
            value={query}
            onChangeText={setQuery}
            onSubmitEditing={() => {
              setPage(1);
              setSubmitted(query.trim());
            }}
            returnKeyType="search"
          />
          <Button
            title="搜索"
            disabled={!online || !query.trim()}
            onPress={() => {
              setPage(1);
              setSubmitted(query.trim());
            }}
          />
        </>
      )}
      {!online && <Label muted>搜索需要联网</Label>}
      {loading && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={reload} />}
      {data && (
        <>
          {data.items.length === 0 && <Label muted>没有找到文章，试试其他关键词。</Label>}
          {data.items.map((post) => (
            <ArticleRow
              key={post.id}
              title={post.title}
              detail={"snippet" in post ? post.snippet : post.summary}
              onPress={() => navigation.navigate("Reader", { postId: post.id })}
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
