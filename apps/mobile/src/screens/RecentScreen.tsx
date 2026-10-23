import React, { useEffect, useState } from "react";
import { useIsFocused } from "@react-navigation/native";
import { ArticleRow, Button, Label, Loading, Notice, Page, PageHeading } from "../components/ui";
import type { RecentEntry } from "../contracts/mobile";
import type { Navigation } from "../navigation/types";
import { useRuntime } from "../runtime/Runtime";
import { errorMessage } from "../runtime/useQuery";
export function RecentScreen({ navigation }: { navigation: Navigation }) {
  const { reader } = useRuntime();
  const [entries, setEntries] = useState<RecentEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const focused = useIsFocused();
  useEffect(() => {
    if (!focused) return;
    let active = true;
    reader.listRecent().then(
      (value) => {
        if (active) {
          setEntries(value);
          setError(null);
        }
      },
      (failure) => {
        if (active) setError(errorMessage(failure));
      },
    );
    return () => {
      active = false;
    };
  }, [reader, focused, revision]);
  return (
    <Page>
      <PageHeading title="最近阅读" eyebrow="PICK UP WHERE YOU LEFT OFF" subtitle="回到上次停下的地方。" />
      <Label muted>只保存在这台设备，登录后也不会上传。</Label>
      {!entries && !error && <Loading />}
      <Notice message={error} />
      {error && <Button title="重试" onPress={() => setRevision((n) => n + 1)} />}
      {entries?.length === 0 && <Label muted>读过的文章会自动出现在这里。</Label>}
      {entries?.map((entry) => (
        <ArticleRow
          key={entry.postId}
          title={entry.title}
          detail={
            entry.copyState === "UNAVAILABLE"
              ? "文章已不可访问"
              : `${entry.position ? Math.round(entry.position.progress * 100) : 0}% · ${entry.copyState === "OFFLINE_READY" ? "离线可读" : "需要联网打开"}`
          }
          progress={entry.position?.progress}
          disabled={entry.copyState === "UNAVAILABLE"}
          onPress={() => navigation.navigate("Reader", { postId: entry.postId })}
        />
      ))}
    </Page>
  );
}
